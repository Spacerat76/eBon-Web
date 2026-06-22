package de.ebon.product;

import org.springframework.stereotype.Component;

@Component
class NoopAiProductAssignmentClient implements AiProductAssignmentClient {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public AiProductAssignmentResponse assign(AiProductAssignmentRequest request) {
        throw new IllegalStateException("Produkt-KI ist nicht konfiguriert.");
    }
}
