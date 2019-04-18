package io.github.semlink.verbnet.semantics;

import io.github.semlink.util.StringUtils;
import io.github.semlink.verbnet.type.SemanticArgumentType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * VerbNet semantic argument.
 *
 * @author jgung
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public abstract class SemanticArgument {

    protected final SemanticArgumentType type;
    protected final String value;

    public SemanticArgument(@NonNull SemanticArgumentType type, @NonNull String value) {
        this.type = type;
        this.value = value;
    }

    @Override
    public String toString() {
        return StringUtils.capitalized(type) + "(" + value + ")";
    }
}