package de.ebon.parser.profile;

/** The region excludes both boundary lines, which reference required, unique anchors. */
public record ProfileItemRegion(String startAnchor, String endAnchor) {
}
