package de.ebon.api.dto;

import jakarta.validation.constraints.AssertTrue;

public record ProductRuleApplyRequest(
        @AssertTrue(message = "confirm muss true sein.") Boolean confirm) {
}
