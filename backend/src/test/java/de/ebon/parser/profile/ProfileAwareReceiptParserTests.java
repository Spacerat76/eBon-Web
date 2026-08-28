package de.ebon.parser.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.ebon.parser.ReceiptParseResult;
import de.ebon.parser.RuleBasedReceiptParser;
import de.ebon.persistence.model.FormatProfileScope;
import de.ebon.persistence.model.FormatProfileSource;
import de.ebon.persistence.model.ParseLineType;
import de.ebon.persistence.model.ParseSource;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.ReceiptFormatProfile;
import de.ebon.persistence.repository.ReceiptFormatProfileRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

class ProfileAwareReceiptParserTests {
    private final ReceiptTextNormalizer normalizer = new ReceiptTextNormalizer();
    private final ReceiptFormatIdentifier identifier = new ReceiptFormatIdentifier();
    private final List<ReceiptFormatProfile> profiles = new ArrayList<>();
    private final ReceiptFormatProfileRepository repository = mock(ReceiptFormatProfileRepository.class);

    @Test
    void activeProfileMayContainAnItemPatternAbsentFromThisBasketButAdmissionRemainsStrict() throws IOException {
        String raw = raw().replace("Unbekannte Ware 0,50\n", "").replace("2,49", "1,99");
        ReceiptFormatDefinitionCodec codec = new ReceiptFormatDefinitionCodec();
        ReceiptFormatDefinition base = codec.read(definition());
        ProfileItemRule apple = base.itemRules().getFirst();
        ProfileItemRule pear = new ProfileItemRule("^(Bio Birne) (.+)$", apple.captures(), apple.region(),
                null, apple.type());
        ReceiptFormatDefinition multiple = new ReceiptFormatDefinition(1, base.anchors(), base.fields(),
                List.of(apple, pear), base.lineRules());
        var admission = new ReceiptFormatDefinitionValidator().validate(multiple, normalizer.normalize(raw));
        assertThat(admission.errors()).containsExactly(
                new ProfileValidationError("itemRules[1]", ProfileValidationError.Code.NO_EXAMPLE_MATCH));
        profile(raw, FormatProfileScope.STORE, 71L, 1, codec.write(multiple));

        ReceiptParseResult result = parser().parse(raw);
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().items()).singleElement().satisfies(item ->
                assertThat(item.description()).isEqualTo("Bio Apfel"));
    }

    @Test
    void runtimeValidationStillRejectsMissingRequiredFieldMatch() throws IOException {
        String raw = raw().replace("13.07.2026\n", "");
        profile(raw, FormatProfileScope.STORE, 72L, 1, definition());
        ReceiptParseResult result = parser().parse(raw);
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.errorMessage()).contains("NO_EXAMPLE_MATCH");
        assertThat(result.receipt()).isNull();
    }

    @Test
    void branchProfilePrecedesStoreProfileForTheSameVersionedFingerprint() throws IOException {
        String raw = raw().replace("13.07.2026", "Filiale Nord\n13.07.2026");
        profile(raw, FormatProfileScope.STORE, 11L, 3, definition());
        profile(raw, FormatProfileScope.BRANCH, 12L, 1, definition());
        ReceiptParseResult result = parser().parse(raw);
        assertThat(result.appliedProfile()).isEqualTo(new AppliedProfile(12L, 1,
                FormatProfileScope.BRANCH, identifier.identify(normalizer.normalize(raw)).fingerprint()));
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        assertThat(result.traces()).anyMatch(trace -> trace.lineType() == ParseLineType.UNRESOLVED);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void storeProfileUsesEmptyBranchKeyWhenNoBranchProfileExists(boolean branchPresent) throws IOException {
        String raw = branchPresent ? raw().replace("13.07.2026", "Filiale Nord\n13.07.2026") : raw();
        profile(raw, FormatProfileScope.STORE, 21L, 4, definition());
        ReceiptParseResult result = parser().parse(raw);
        assertThat(result.appliedProfile().profileId()).isEqualTo(21L);
        assertThat(result.appliedProfile().version()).isEqualTo(4);
        assertThat(result.parseSource()).isEqualTo(ParseSource.RULE);
        assertThat(result.traces()).hasSize(branchPresent ? 8 : 7);
    }

    @Test
    void inactiveDifferentVersionOrDifferentFingerprintProfilesFallBackToLegacy() throws IOException {
        String raw = raw();
        profile(raw, FormatProfileScope.STORE, 31L, 1, definition()).suspend("test");
        ReceiptFormatProfile wrongVersion = profile(raw, FormatProfileScope.STORE, 32L, 2, definition());
        ReflectionTestUtils.setField(wrongVersion, "fingerprintVersion", 2);
        ReceiptFormatProfile wrongFingerprint = profile(raw, FormatProfileScope.STORE, 33L, 3, definition());
        ReflectionTestUtils.setField(wrongFingerprint, "fingerprint", "different");
        ReceiptParseResult result = parser().parse(raw);
        assertThat(result.appliedProfile()).isNull();
        assertThat(result.traces()).isEmpty();
        assertThat(result.receipt()).isEqualTo(new RuleBasedReceiptParser().parse(raw).receipt());
    }

    @Test
    void failedSelectedProfileDoesNotHideBehindSuccessfulLegacyOutput() throws IOException {
        String raw = raw().replace("Unbekannte Ware 0,50\n", "").replace("2,49", "1,99");
        assertThat(new RuleBasedReceiptParser().parse(raw).parsed()).isTrue();
        profile(raw, FormatProfileScope.STORE, 41L, 1, "{\"schemaVersion\":999}");
        ReceiptParseResult result = parser().parse(raw);
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.appliedProfile().profileId()).isEqualTo(41L);
        assertThat(result.receipt()).isNull();
        assertThat(result.errorMessage()).contains("PROFILE");
        assertThat(result.traces()).hasSize(6).allMatch(trace -> trace.needsReview());
        assertThat(result.errorMessage()).doesNotContain(raw, "schemaVersion");
    }

    @Test
    void schemaInvalidDefinitionNeverReachesTheInterpreter() throws IOException {
        String raw = raw();
        profile(raw, FormatProfileScope.STORE, 51L, 1,
                definition().replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
        ReceiptParseResult result = parser().parse(raw);
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.errorMessage()).contains("SCHEMA_VERSION");
        assertThat(result.receipt()).isNull();
    }

    @Test
    void completeProfileRetainsImmutableProvenanceAcrossSourceDecoration() throws IOException {
        String raw = raw().replace("Unbekannte Ware 0,50\n", "").replace("2,49", "1,99");
        profile(raw, FormatProfileScope.STORE, 61L, 2, definition());
        ReceiptParseResult result = parser().parse(raw).withParseSource(ParseSource.RULE);
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.traces()).hasSize(6);
        assertThat(result.appliedProfile().profileId()).isEqualTo(61L);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> result.traces().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ProfileAwareReceiptParser parser() {
        when(repository.findFirstByStateAndStoreNameKeyAndStoreBranchKeyAndFingerprintAndFingerprintVersionOrderByVersionDesc(
                any(), anyString(), anyString(), anyString(), anyInt())).thenAnswer(call -> profiles.stream()
                .filter(profile -> profile.getState() == call.getArgument(0))
                .filter(profile -> profile.getStoreNameKey().equals(call.getArgument(1)))
                .filter(profile -> profile.getStoreBranchKey().equals(call.getArgument(2)))
                .filter(profile -> profile.getFingerprint().equals(call.getArgument(3)))
                .filter(profile -> profile.getFingerprintVersion() == (int) call.getArgument(4))
                .max(java.util.Comparator.comparingInt(ReceiptFormatProfile::getVersion)));
        return new ProfileAwareReceiptParser(repository, normalizer, identifier, new ReceiptFormatDefinitionCodec(),
                new ReceiptFormatDefinitionValidator(), new ReceiptFormatProfileInterpreter(),
                new ProfileParseQualityGate(), new RuleBasedReceiptParser());
    }

    private ReceiptFormatProfile profile(String raw, FormatProfileScope scope, long id, int version, String definition) {
        ReceiptFormatIdentity identity = identifier.identify(normalizer.normalize(raw));
        ReceiptFormatProfile profile = new ReceiptFormatProfile(scope, identity.storeNameKey(),
                scope == FormatProfileScope.STORE ? "" : identity.storeBranchKey(), identity.fingerprint(),
                identity.fingerprintVersion(), version, definition, FormatProfileSource.USER_CORRECTED, null);
        ReflectionTestUtils.setField(profile, "id", id);
        profile.activate();
        profiles.add(profile);
        return profile;
    }

    private String raw() throws IOException {
        return resource("txt");
    }

    private String definition() throws IOException {
        return resource("profile.json");
    }

    private String resource(String suffix) throws IOException {
        return new ClassPathResource("corpus/profile/format_profile_unresolved." + suffix)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
