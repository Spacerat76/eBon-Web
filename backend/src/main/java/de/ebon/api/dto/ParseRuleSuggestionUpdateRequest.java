package de.ebon.api.dto;

import de.ebon.persistence.model.ParseRuleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ParseRuleSuggestionUpdateRequest(
        @Size(max = 255)
        String storeName,
        @NotNull
        ParseRuleType ruleType,
        @NotBlank
        @Size(max = 1024)
        String matchRegex,
        @Size(max = 64)
        String extractGroup,
        @DecimalMin("0.000")
        @DecimalMax("1.000")
        @Digits(integer = 1, fraction = 3)
        BigDecimal confidence,
        @NotBlank
        String problemDescription,
        @NotBlank
        String solutionRationale) {
}
