package de.ebon.parser;

import java.util.concurrent.atomic.AtomicInteger;

public class AiParsingBudget {

    private final int limit;
    private final AtomicInteger used = new AtomicInteger();

    private AiParsingBudget(int limit) {
        this.limit = limit;
    }

    public static AiParsingBudget unlimited() {
        return new AiParsingBudget(-1);
    }

    public static AiParsingBudget limited(int limit) {
        return new AiParsingBudget(Math.max(limit, 0));
    }

    public boolean tryAcquire() {
        if (limit < 0) {
            return true;
        }
        while (true) {
            int current = used.get();
            if (current >= limit) {
                return false;
            }
            if (used.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
}
