package de.ebon.parser;

import static org.assertj.core.api.Assertions.assertThat;

import de.ebon.parser.profile.ReceiptFormatIdentifier;
import de.ebon.parser.profile.ReceiptFormatIdentity;
import de.ebon.parser.profile.ReceiptTextNormalizer;
import de.ebon.persistence.model.*;
import de.ebon.persistence.repository.*;
import de.ebon.support.PostgresIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@TestPropertySource(properties = "app.sync.scheduler.enabled=false")
class ReceiptParsePersistenceTests extends PostgresIntegrationTestSupport {
    @Autowired ReceiptParserService parser;
    @Autowired ReceiptParseApplier applier;
    @Autowired ReceiptRepository receipts;
    @Autowired ReceiptFormatProfileRepository profiles;
    @Autowired ReceiptParseTraceRepository traces;
    @Autowired EntityManager entityManager;

    enum Replacement { LEGACY, AI, NO_OUTPUT }

    @ParameterizedTest
    @EnumSource(Replacement.class)
    void repeatedApplyReplacesRowsAndEveryNonProfilePathClearsProvenance(Replacement replacement) throws Exception {
        String raw = new ClassPathResource("corpus/profile/format_profile_unresolved.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        String definition = new ClassPathResource("corpus/profile/format_profile_unresolved.profile.json")
                .getContentAsString(StandardCharsets.UTF_8);
        ReceiptFormatIdentity identity = new ReceiptFormatIdentifier().identify(new ReceiptTextNormalizer().normalize(raw));
        ReceiptFormatProfile predecessor = profiles.saveAndFlush(new ReceiptFormatProfile(FormatProfileScope.STORE,
                identity.storeNameKey(), "", identity.fingerprint(), identity.fingerprintVersion(), 1, definition,
                FormatProfileSource.USER_CORRECTED, null));
        ReceiptFormatProfile profile = new ReceiptFormatProfile(FormatProfileScope.STORE, identity.storeNameKey(), "",
                identity.fingerprint(), identity.fingerprintVersion(), 2, definition, FormatProfileSource.USER_CORRECTED, predecessor);
        profile.activate();
        profiles.saveAndFlush(profile);
        Receipt receipt = receipts.saveAndFlush(new Receipt(890001 + replacement.ordinal(), raw));
        ReceiptParseResult result = parser.parse(raw);
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);

        applier.apply(receipt, result);
        receipts.flush();
        List<Long> oldIds = traces.findByReceipt_IdOrderByLineNumberAsc(receipt.getId()).stream()
                .map(ReceiptParseTrace::getId).toList();
        assertThat(oldIds).hasSize(7);
        applier.apply(receipt, result);
        receipts.flush();
        Long id = receipt.getId();
        entityManager.clear();
        receipt = receipts.findById(id).orElseThrow();
        assertThat(receipt.getReceiptFormatProfile().getId()).isEqualTo(profile.getId());
        assertThat(receipt.getFormatProfileVersion()).isEqualTo(2);
        assertThat(receipt.getItems()).singleElement().satisfies(item ->
                assertThat(item.getExtractionStatus()).isEqualTo(ExtractionStatus.CONFIRMED));
        assertThat(traces.findByReceipt_IdOrderByLineNumberAsc(id)).hasSize(7).allSatisfy(trace -> {
            assertThat(trace.getId()).isNotIn(oldIds);
            assertThat(trace.getFormatProfileVersion()).isEqualTo(2);
        });

        ReceiptParseResult next = switch (replacement) {
            case LEGACY -> new RuleBasedReceiptParser().parse(raw).withParseSource(ParseSource.RULE);
            case AI -> new RuleBasedReceiptParser().parse(raw).withParseSource(ParseSource.AI);
            case NO_OUTPUT -> new ReceiptParseResult(ParseStatus.PARSE_ERROR, null, "missing");
        };
        applier.apply(receipt, next);
        receipts.flush();
        entityManager.clear();
        Receipt saved = receipts.findById(id).orElseThrow();
        assertThat(saved.getReceiptFormatProfile()).isNull();
        assertThat(saved.getFormatProfileVersion()).isNull();
        assertThat(traces.countByReceipt_Id(id)).isZero();
        if (replacement == Replacement.NO_OUTPUT) assertThat(saved.getItems()).isEmpty();
    }
}
