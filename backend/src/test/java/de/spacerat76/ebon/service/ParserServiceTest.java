package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Receipt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.Mockito;
import de.spacerat76.ebon.ai.AiParseResult;
import de.spacerat76.ebon.ai.AiParseItem;
import de.spacerat76.ebon.ai.AiClient;
import de.spacerat76.ebon.repository.ParseRuleRepository;
import de.spacerat76.ebon.domain.ParseRule;
import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;

public class ParserServiceTest {

    @Test
    void parse_createsReceiptWithParsedStatusAndItem() {
        ParserService parser = new ParserServiceImpl();
        Receipt r = parser.parse(123, "Sample receipt text");

        assertThat(r).isNotNull();
        assertThat(r.getParseStatus()).isEqualTo("PARSE_ERROR");
        assertThat(r.getItems()).hasSize(1);
        assertThat(r.getPaperlessDocumentId()).isEqualTo(123);
    }

    @Test
    void parse_ai_fallback_parsesItemsAndPersistsSuggestedRule() {
        ParserServiceImpl parser = new ParserServiceImpl();

        // prepare AI parse result
        AiParseResult ar = new AiParseResult();
        AiParseItem i1 = new AiParseItem();
        i1.setDescription("Apples");
        i1.setQuantity(new BigDecimal("1"));
        i1.setUnit("kg");
        i1.setUnitPrice(new BigDecimal("2.50"));
        i1.setTotal(new BigDecimal("2.50"));
        AiParseItem i2 = new AiParseItem();
        i2.setDescription("Milk");
        i2.setQuantity(new BigDecimal("2"));
        i2.setUnit("x");
        i2.setUnitPrice(new BigDecimal("1.20"));
        i2.setTotal(new BigDecimal("2.40"));
        ar.setItems(List.of(i1, i2));
        ar.setCurrency("EUR");
        ar.setTotalAmount(new BigDecimal("4.90"));
        ar.setBonusBalance(new BigDecimal("0.50"));
        ar.setBonusPoints(new BigDecimal("12"));
        ar.setSuggestedParseRegex("^ITEM_REGEX$");
        ar.setSuggestedParseName("AI Rule");

        AiClient aiClient = Mockito.mock(AiClient.class);
        Mockito.when(aiClient.parseReceipt(Mockito.anyString())).thenReturn(Optional.of(ar));

        ParseRuleRepository pr = Mockito.mock(ParseRuleRepository.class);
        Mockito.when(pr.save(Mockito.any(ParseRule.class))).thenAnswer(i -> i.getArgument(0));

        parser.setAiClient(aiClient);
        parser.setParseRuleRepository(pr);

        Receipt r = parser.parse(1000, "raw text from OCR");

        assertThat(r).isNotNull();
        assertThat(r.getItems()).hasSize(2);
        assertThat(r.getCurrency()).isEqualTo("EUR");
        assertThat(r.getTotalAmount()).isEqualByComparingTo("4.90");
        assertThat(r.getBonusBalance()).isEqualByComparingTo("0.50");
        assertThat(r.getBonusPoints()).isEqualByComparingTo("12");

        Mockito.verify(pr, Mockito.times(1)).save(Mockito.argThat(p -> ((ParseRule)p).getRegex() != null && ((ParseRule)p).getRegex().equals("^ITEM_REGEX$")));
    }

    @Test
    void parse_extractsItemsCurrencyAndBonus() {
        ParserService parser = new ParserServiceImpl();
        String raw = String.join("\n",
                "Store Example",
                "1 kg Apples 2.50 2.50",
                "2 x Milk 1.20 2.40",
                "Total: 4.90 EUR",
                "Bonus: 0.50 EUR",
                "Points: 12"
        );

        Receipt r = parser.parse(999, raw);

        assertThat(r).isNotNull();
        assertThat(r.getItems()).hasSize(2);
        assertThat(r.getCurrency()).isEqualTo("EUR");
        assertThat(r.getTotalAmount()).isEqualByComparingTo("4.90");
        assertThat(r.getBonusBalance()).isEqualByComparingTo("0.50");
        assertThat(r.getBonusPoints()).isEqualByComparingTo("12");
    }
}
