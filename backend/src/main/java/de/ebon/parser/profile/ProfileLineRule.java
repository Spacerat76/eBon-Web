package de.ebon.parser.profile;

/** Positions must use item rules. Unknown plausible lines cannot be declared resolved here. */
public record ProfileLineRule(String regex, Type type) {
    public enum Type {
        METADATA, PAYMENT, TOTAL, TAX, TSE, IGNORED_SAFE
    }
}
