package de.ebon.parser.profile;

/** Adjacent description lines only; maxLines includes the item's price-bearing line. */
public record ProfileMultilineRule(String regex, int descriptionGroup, int maxLines, Placement placement) {
    public enum Placement {
        BEFORE, AFTER
    }
}
