/*
 * Copyright 2020 James Gung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.semlink.dep;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.tensorflow.example.SequenceExample;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import io.github.semlink.app.WordPieceTokenizer;
import io.github.semlink.dep.feat.BertDepExampleExtractor;
import io.github.semlink.dep.util.ParsingUtils;
import io.github.semlink.extractor.SequenceExampleExtractor;
import io.github.semlink.extractor.Vocabulary;
import io.github.semlink.tensor.TensorList;
import io.github.semlink.type.Fields;
import io.github.semlink.type.HasFields;
import io.github.semlink.type.IToken;
import io.github.semlink.type.ITokenSequence;
import io.github.semlink.type.Token;
import io.github.semlink.type.TokenSequence;
import lombok.NonNull;
import lombok.Setter;

import static io.github.semlink.tensor.Tensors.batchExamples;
import static io.github.semlink.tensor.Tensors.toStringLists;


/**
 * Tensorflow-based dependency parser.
 *
 * @author jgung
 */
public class DependencyParser implements AutoCloseable {

    private static final String OP_NAME = "input_example_tensor";

    private final SequenceExampleExtractor featureExtractor;
    private final SavedModelBundle model;
    private final Vocabulary relVocabulary;

    @Setter
    private String posTensor = "pos/labels";
    @Setter
    private String arcTensor = "deprel/Softmax";
    @Setter
    private String relTensor = "deprel/transpose_1";

    public DependencyParser(@NonNull SequenceExampleExtractor featureExtractor,
                            @NonNull SavedModelBundle model,
                            @NonNull Vocabulary vocabulary) {
        this.featureExtractor = featureExtractor;
        this.model = model;
        this.relVocabulary = vocabulary;
    }

    public List<ITokenSequence> predictBatch(@NonNull List<HasFields> inputs) {

        List<SequenceExample> sequenceExamples = inputs.stream()
                .map(featureExtractor::extractSequence)
                .collect(Collectors.toList());

        try (Tensor<?> inputTensor = Tensor.create(batchExamples(sequenceExamples), String.class)) {
            Session.Runner runner = model.session().runner()
                    .feed(OP_NAME, inputTensor)
                    .fetch(posTensor)
                    .fetch(arcTensor)
                    .fetch(relTensor);

            TensorList results = TensorList.of(runner.run());

            List<List<String>> posResults = toStringLists(results.get(0));
            List<float[][]> arcProbs = ParsingUtils.toArcProbs(results.get(1));
            List<float[][][]> relProbs = ParsingUtils.toRelProbs(results.get(2));

            List<ITokenSequence> output = new ArrayList<>();

            for (int i = 0; i < inputs.size(); ++i) {
                List<String> words = inputs.get(i).field("word");

                List<IToken> tokens = new ArrayList<>();

                float[][] arcs = ParsingUtils.trimToLength(arcProbs.get(i), words.size() + 1);
                float[][][] rels = ParsingUtils.trimToLength(relProbs.get(i), words.size() + 1);
                List<String> tags = posResults.get(i);
                List<Integer> edges = ParsingUtils.fixCycles(arcs);
                List<String> labels = ParsingUtils.getLabels(rels, edges, relVocabulary::indexToFeat);

                for (int idx = 1, len = words.size() + 1; idx < len; ++idx) {
                    String tag = tags.get(idx - 1);
                    int head = edges.get(idx);
                    String label = labels.get(idx);
                    String word = words.get(idx - 1);
                    Token token = (Token) new Token(word, idx - 1)
                            .add("pos", tag)
                            .add("dep", label)
                            .add("head", head);
                    tokens.add(token);
                }

                output.add(new TokenSequence(tokens));
            }

            results.close();

            return output;
        }
    }

    @Override
    public void close() {
        model.close();
    }

    public static DependencyParser fromDirectory(@NonNull String modelDir) {
        SequenceExampleExtractor extractor = new BertDepExampleExtractor(
                new WordPieceTokenizer(Paths.get(modelDir, "model", "assets", "vocab.txt").toString()));
        SavedModelBundle model = SavedModelBundle.load(Paths.get(modelDir, "model").toString(), "serve");
        try (FileInputStream vocabStream = new FileInputStream(Paths.get(modelDir, "vocab", "deprel").toString())) {
            return new DependencyParser(extractor, model, Vocabulary.read(vocabStream, "dep"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Fields process(String input) {
        List<String> words = Arrays.asList(input.split("\\s+"));
        Fields seq = new Fields();
        seq.add("word", words);
        return seq;
    }

    public static void main(String[] args) {
        String path = args[0];

        try (DependencyParser model = fromDirectory(path)) {

            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print(">>> ");
                String line = scanner.nextLine();
                if (line.equals("QUIT")) {
                    break;
                }
                List<HasFields> entries = ImmutableList.of(line).stream()
                        .map(DependencyParser::process)
                        .collect(Collectors.toList());
                Stopwatch started = Stopwatch.createStarted();
                ITokenSequence result =
                        entries.parallelStream()
                                .map(e -> model.predictBatch(Collections.singletonList(e)).get(0))
                                .findFirst().orElseThrow(IllegalStateException::new);
                System.out.println("Elapsed time: " + started);
                List<String> lines = new ArrayList<>();
                for (IToken token : result) {
                    lines.add(String.format("%d\t%10s\t%10s\t%10s\t%10s",
                            token.index() + 1,
                            token.field(Fields.DefaultFields.TEXT), token.field("pos"),
                            token.field("dep"), token.field("head")));
                }
                System.out.println("\n" + String.join("\n", lines) + "\n");
            }
        }
    }

}