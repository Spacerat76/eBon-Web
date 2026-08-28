package de.ebon.parser.profile;

import java.util.List;

public record ProfileValidationResult(List<ProfileValidationError> errors) {
    public ProfileValidationResult {
        errors = List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
