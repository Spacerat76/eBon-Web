package de.ebon.persistence.model;

public enum ParseRuleValidationStatus {
    VALID,
    INVALID_REGEX,
    NO_MATCH,
    WRONG_EXTRACTION,
    COLLISION_RISK
}
