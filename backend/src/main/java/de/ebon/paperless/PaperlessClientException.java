package de.ebon.paperless;

public class PaperlessClientException extends RuntimeException {

    public PaperlessClientException(String message) {
        super(message);
    }

    public PaperlessClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
