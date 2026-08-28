package de.ebon.parser.profile;

import de.ebon.persistence.model.FormatProfileScope;

/** Immutable provenance of the selected profile, including unsuccessful evaluations. */
public record AppliedProfile(Long profileId, int version, FormatProfileScope scope, String fingerprint) {
}
