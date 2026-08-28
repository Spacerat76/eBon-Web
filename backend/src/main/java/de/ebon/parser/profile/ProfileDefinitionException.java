package de.ebon.parser.profile;

import java.util.List;

/** Sanitized boundary exception: deliberately does not retain the parser/regex cause. */
public final class ProfileDefinitionException extends IllegalArgumentException {
    private final ProfileValidationResult result;

    ProfileDefinitionException(ProfileValidationError.Code code) {
        super("Formatprofil ist ungueltig: " + code.name());
        result = new ProfileValidationResult(List.of(new ProfileValidationError("$", code)));
    }

    public ProfileValidationResult result() {
        return result;
    }
}
