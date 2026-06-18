package de.ebon.persistence.model;

public enum AiParsingStatus {
    SUCCESS,
    FAILED,
    SKIPPED_LIMIT,
    INVALID_RESPONSE,
    LOW_CONFIDENCE,
    DISABLED,
    NO_API_KEY
}
