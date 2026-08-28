package de.ebon.parser.profile;

import de.ebon.parser.ReceiptParseResult;
import de.ebon.parser.RuleBasedReceiptParser;
import de.ebon.persistence.model.FormatProfileScope;
import de.ebon.persistence.model.FormatProfileState;
import de.ebon.persistence.model.ParseLineType;
import de.ebon.persistence.model.ParseSource;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.ReceiptFormatProfile;
import de.ebon.persistence.repository.ReceiptFormatProfileRepository;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProfileAwareReceiptParser {
    private final ReceiptFormatProfileRepository repository;
    private final ReceiptTextNormalizer normalizer;
    private final ReceiptFormatIdentifier identifier;
    private final ReceiptFormatDefinitionCodec codec;
    private final ReceiptFormatDefinitionValidator validator;
    private final ReceiptFormatProfileInterpreter interpreter;
    private final ProfileParseQualityGate qualityGate;
    private final RuleBasedReceiptParser legacy;

    public ProfileAwareReceiptParser(ReceiptFormatProfileRepository repository, ReceiptTextNormalizer normalizer,
            ReceiptFormatIdentifier identifier, ReceiptFormatDefinitionCodec codec,
            ReceiptFormatDefinitionValidator validator, ReceiptFormatProfileInterpreter interpreter,
            ProfileParseQualityGate qualityGate, RuleBasedReceiptParser legacy) {
        this.repository = repository;
        this.normalizer = normalizer;
        this.identifier = identifier;
        this.codec = codec;
        this.validator = validator;
        this.interpreter = interpreter;
        this.qualityGate = qualityGate;
        this.legacy = legacy;
    }

    public ReceiptParseResult parse(String rawText) {
        NormalizedReceiptDocument document = normalizer.normalize(rawText);
        ReceiptFormatIdentity identity = identifier.identify(document);
        Optional<ReceiptFormatProfile> selected = identity.storeBranchKey().isEmpty() ? Optional.empty()
                : find(identity, identity.storeBranchKey()).filter(profile -> profile.getScope() == FormatProfileScope.BRANCH);
        if (selected.isEmpty()) {
            selected = find(identity, "").filter(profile -> profile.getScope() == FormatProfileScope.STORE);
        }
        if (selected.isEmpty()) {
            return legacy.parse(rawText);
        }

        ReceiptFormatProfile profile = selected.get();
        AppliedProfile applied = new AppliedProfile(profile.getId(), profile.getVersion(), profile.getScope(),
                profile.getFingerprint());
        try {
            ReceiptFormatDefinition definition = codec.read(profile.getProfileDefinition());
            ProfileValidationResult validation = validator.validateForParsing(definition, document);
            if (!validation.valid() || profile.getProfileSchemaVersion() != definition.schemaVersion()) {
                String codes = validation.errors().stream().map(error -> error.code().name()).distinct()
                        .collect(Collectors.joining(","));
                return invalidProfile(document, applied, "PROFILE_VALIDATION_FAILED:" + codes);
            }
            ProfileParseOutcome outcome = qualityGate.validate(interpreter.interpret(definition, document));
            ReceiptParseResult result = outcome.parseResult();
            return new ReceiptParseResult(result.parseStatus(), result.receipt(), result.errorMessage(),
                    ParseSource.RULE, applied, outcome.traces());
        } catch (ProfileDefinitionException exception) {
            return invalidProfile(document, applied, "PROFILE_DEFINITION_INVALID:"
                    + exception.result().errors().getFirst().code().name());
        }
    }

    private Optional<ReceiptFormatProfile> find(ReceiptFormatIdentity identity, String branchKey) {
        return repository.findFirstByStateAndStoreNameKeyAndStoreBranchKeyAndFingerprintAndFingerprintVersionOrderByVersionDesc(
                FormatProfileState.ACTIVE, identity.storeNameKey(), branchKey, identity.fingerprint(), identity.fingerprintVersion());
    }

    private ReceiptParseResult invalidProfile(NormalizedReceiptDocument document, AppliedProfile applied, String error) {
        return new ReceiptParseResult(ParseStatus.PARSE_ERROR, null, error, ParseSource.RULE, applied,
                document.lines().stream().map(line -> new ParsedLineTrace(line.originalLineNumber(), line.startOffset(),
                        line.endOffset(), ParseLineType.UNRESOLVED, null, Map.of(), "PROFILE_VALIDATION_FAILED")).toList());
    }
}
