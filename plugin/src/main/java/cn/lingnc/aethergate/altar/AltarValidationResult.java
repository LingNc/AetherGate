package cn.lingnc.aethergate.altar;

import java.util.Collections;
import java.util.List;

public class AltarValidationResult {

    private final boolean valid;
    private final List<String> errors;

    private AltarValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors;
    }

    public static AltarValidationResult success() {
        return new AltarValidationResult(true, Collections.emptyList());
    }

    public static AltarValidationResult failure(List<String> errors) {
        return new AltarValidationResult(false, List.copyOf(errors));
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }
}
