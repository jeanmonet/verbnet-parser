/*
 * Copyright 2019 James Gung
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

package io.github.semlink.parser;

import io.github.clearwsd.type.DepTree;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Aggregate of data output during semantic parsing.
 *
 * @author jgung
 */
@Getter
@Setter
@Accessors(fluent = true)
public class VerbNetSemanticParse {

    private DepTree tree;
    private List<String> tokens;
    private List<VerbNetProp> props = new ArrayList<>();

    @Override
    public String toString() {
        return props.stream()
            .map(Object::toString)
            .collect(Collectors.joining("\n\n"));
    }
}
