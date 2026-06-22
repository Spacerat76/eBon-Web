package de.ebon.product;

public interface AiProductAssignmentClient {

    boolean isAvailable();

    AiProductAssignmentResponse assign(AiProductAssignmentRequest request);
}
