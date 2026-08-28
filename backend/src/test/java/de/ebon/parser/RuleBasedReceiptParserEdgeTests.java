package de.ebon.parser;

import de.ebon.config.ReceiptParserProperties;
import de.ebon.persistence.model.ParseRule;
import de.ebon.persistence.model.ParseRuleType;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.RuleSource;
import de.ebon.persistence.repository.ParseRuleRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleBasedReceiptParserEdgeTests {

    private final RuleBasedReceiptParser parser = new RuleBasedReceiptParser();

    @Test
    void composesPartialDynamicItemsInSourceOrderWithoutContaminatingDescriptions() {
        ReceiptParseResult result = parserWithItemRules().parse("""
                REWE
                18.06.2026
                # Dynamic item :: 2,00 ::
                Generic item 1,00
                # Dynamic item :: 2,00 ::
                SUMME EUR 5,00
                """);
        assertThat(result.receipt().items()).extracting(ParsedReceiptItem::description)
                .containsExactly("Dynamic item", "Generic item", "Dynamic item");
        assertThat(result.receipt().items()).extracting(ParsedReceiptItem::positionIndex)
                .containsExactly(0, 1, 2);
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
    }

    @Test
    void sameSourceGenericAndDynamicMatchesAreNotDuplicatedButRepeatedPurchasesRemain() {
        ReceiptParseResult result = parserWithItemRules().parse("""
                REWE
                18.06.2026
                Generic item 1,00
                Generic item 1,00
                # Dynamic item :: 2,00 ::
                SUMME EUR 4,00
                """);
        assertThat(result.receipt().items()).extracting(ParsedReceiptItem::description)
                .containsExactly("Generic item", "Generic item", "Dynamic item");
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
    }

    @Test
    void dynamicItemsParticipateInStornoWithoutReappearingFromASecondPass() {
        ReceiptParseResult result = parserWithItemRules().parse("""
                REWE
                18.06.2026
                # Dynamic item :: 2,00 ::
                ZEILENSTORNO
                Generic item 1,00
                SUMME EUR 1,00
                """);
        assertThat(result.receipt().items()).extracting(ParsedReceiptItem::description)
                .containsExactly("Generic item");
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
    }

    private RuleBasedReceiptParser parserWithItemRules() {
        ParseRuleRepository repository = mock(ParseRuleRepository.class);
        when(repository.findByActiveTrueAndRuleTypeOrderByStoreNameAsc(ParseRuleType.ITEM_PATTERN))
                .thenReturn(List.of(
                        new ParseRule("REWE", ParseRuleType.ITEM_PATTERN,
                                "^#\\s*(?<description>.+?)\\s*::\\s*(?<total>\\d+,\\d{2})\\s*::$",
                                null, RuleSource.AI_ADAPTED),
                        new ParseRule("REWE", ParseRuleType.ITEM_PATTERN,
                                "^(?<description>Generic item) (?<total>\\d+,\\d{2})$",
                                null, RuleSource.AI_ADAPTED)));
        return new RuleBasedReceiptParser(new ReceiptParserProperties(), repository);
    }

    @Test
    void dynamicItemConsumesMatchingLeadingQuantityDetails() {
        ReceiptParseResult result = parserWithItemRules().parse("""
                REWE
                18.06.2026
                2 x 1,00
                # Dynamic item :: 2,00 ::
                Generic item 2,00
                SUMME EUR 4,00
                """);
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().items()).hasSize(2);
        ParsedReceiptItem dynamicItem = result.receipt().items().getFirst();
        assertThat(dynamicItem.description()).isEqualTo("Dynamic item");
        assertThat(dynamicItem.quantity()).isEqualByComparingTo("2");
        assertThat(dynamicItem.unit()).isEqualTo("Stk");
        assertThat(dynamicItem.unitPrice()).isEqualByComparingTo("1.00");
        assertThat(result.receipt().items().get(1).quantity()).isNull();
    }

    @Test
    void mismatchedLeadingQuantityIsNotAppliedToDynamicOrFollowingGenericItem() {
        ReceiptParseResult result = parserWithItemRules().parse("""
                REWE
                18.06.2026
                3 x 1,00
                # Dynamic item :: 2,00 ::
                Generic item 3,00
                SUMME EUR 5,00
                """);
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().items()).hasSize(2).allSatisfy(item -> {
            assertThat(item.quantity()).isNull();
            assertThat(item.unitPrice()).isNull();
        });
    }

    // Verifies REWE markdown-table OCR output is parsed into real items, including quantity rows.
    @Test
    void parsesMarkdownTableItemsFromReweReceipts() {
        ReceiptParseResult result = parser.parse("""
                REWE Markt
                30.04.2026 10:00
                |  | EUR  |  |
                | --- | --- |
                | SERVICE GEW | 2,15 B |
                | GOURMET SPIESS | 3,86 B |
                | ZUCKERMAIS | 7,47 B |
                | 3 Stk x | 2,49 |
                KINDER RIEGEL 11,10 B
                SUMME EUR 24,58
                Zahlung erfolgt
                | A= 19,0% | 10,23 | 1,94 | 12,17 |
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().totalAmount()).isEqualByComparingTo("24.58");
        assertThat(result.receipt().items())
                .extracting(ParsedReceiptItem::description)
                .containsExactly("SERVICE GEW", "GOURMET SPIESS", "ZUCKERMAIS", "KINDER RIEGEL");
        assertThat(result.receipt().items().get(2).quantity()).isEqualByComparingTo("3");
        assertThat(result.receipt().items().get(2).unitPrice()).isEqualByComparingTo("2.49");
    }

    // Verifies decorative REWE header lines still yield the normalized store name and branch address.
    @Test
    void parsesStarDecoratedReweMarketAddressAsBranch() {
        ReceiptParseResult result = parser.parse("""
                ** Rewe Marco Pfeffel oHG **
                ** Am Reuschenberger Markt 1 **
                ** 41466 Neuss **
                Tel.: 02131 1249939
                UID Nr.: DE325262840
                EUR
                GEFLUEGELSALAT 9,05 B
                SUMME EUR 9,05
                Datum: 18.10.2025
                Uhrzeit: 09:10:11 Uhr
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().storeName()).isEqualTo("REWE");
        assertThat(result.receipt().storeBranch()).isEqualTo("Am Reuschenberger Markt 1");
        assertThat(result.receipt().items()).singleElement()
                .extracting(ParsedReceiptItem::description)
                .isEqualTo("GEFLUEGELSALAT");
    }

    // Verifies REWE phone/tax headers are ignored and weight rows enrich the preceding item.
    @Test
    void ignoresReweTelAndTaxNumberHeadersAndParsesHandeingabeQuantity() {
        ReceiptParseResult result = parser.parse("""
                REWE Beispielmarkt
                Lessingplatz 4
                41469 Neuss
                Tel: 02137-000000
                Steuer.Nr:122/0000/0000
                UID Nr.: DE000000000
                EUR
                GEFLUEGELSALAT 9,05 B
                Handeingabe E-Bon 0,455 kg
                SCHINKENW. 2,00 B
                Handeingabe E-Bon 0,144 kg
                SUMME EUR 11,05
                Datum: 26.02.2026
                Uhrzeit: 09:04:38 Uhr
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().storeBranch()).isEqualTo("Lessingplatz 4");
        assertThat(result.receipt().items())
                .extracting(ParsedReceiptItem::description)
                .containsExactly("GEFLUEGELSALAT", "SCHINKENW.");
        assertThat(result.receipt().items().getFirst().quantity()).isEqualByComparingTo("0.455");
        assertThat(result.receipt().items().getFirst().unit()).isEqualTo("kg");
        assertThat(result.receipt().items().getFirst().unitPrice()).isEqualByComparingTo("19.89");
    }

    // Verifies dm receipts expose the branch code even when the address exists only as an image.
    @Test
    void parsesDmHeaderStoreCodeAsBranchIdentifier() {
        ReceiptParseResult result = parser.parse("""
                03.01.2025 14:28 D2C9/1 012046/1 6478
                3x 0,95 Prof. Spülschwämme 3 2,85 1
                Prof. Staubtücher 48St 2,45 1
                Zwischensumme 5,30
                SUMME EUR 5,30
                KARTENZAHLUNG EUR -5,30
                PAYBACK Punkte auf punktefähige Artikel
                Basis-Punkte 2°P
                Öffnungszeiten auf dm.de
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().storeName()).isEqualTo("dm");
        assertThat(result.receipt().storeBranch()).isEqualTo("Filiale D2C9/1");
    }

    // Verifies configurable dm branch mappings replace branch-code fallbacks with real addresses.
    @Test
    void mapsDmHeaderStoreCodeToConfiguredBranch() {
        ReceiptParserProperties properties = new ReceiptParserProperties();
        properties.setDmBranchMappings(Map.of("D482", "Am Reuschenberger Markt 3, 41466 Neuss"));
        RuleBasedReceiptParser configuredParser = new RuleBasedReceiptParser(properties);

        ReceiptParseResult result = configuredParser.parse("""
                15.05.2026 14:51 D482/1 331465/1 3692
                Denkmit Spuelmittel 0,95 1
                SUMME EUR 0,95
                KARTENZAHLUNG EUR -0,95
                PAYBACK Punkte auf punktefähige Artikel
                Basis-Punkte 1°P
                Öffnungszeiten auf dm.de
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().storeName()).isEqualTo("dm");
        assertThat(result.receipt().storeBranch()).isEqualTo("Am Reuschenberger Markt 3, 41466 Neuss");
    }

    // Verifies REWE cashback and cash payout lines are not counted as purchased items.
    @Test
    void ignoresReweCashbackAndAuszahlungAsItems() {
        ReceiptParseResult result = parser.parse("""
                REWE Markt
                22.05.2026 10:00
                ARTIKEL A 100,00 B
                ARTIKEL B 43,95 B
                SUMME EUR 143,95
                Geg. EC-Cash EUR 193,95
                AUSZAHLUNG EUR 50,00
                Cashback EUR 50,00
                Gesamt EUR 193,95
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().totalAmount()).isEqualByComparingTo("143.95");
        assertThat(result.receipt().items()).hasSize(2);
    }

    // Verifies dm gift cards are treated as payment tender while coupon discounts remain receipt items.
    @Test
    void treatsDmGiftCardPaymentAsTenderAndKeepsDiscountAsItem() {
        ReceiptParseResult result = parser.parse("""
                dm
                28.03.2026 10:00
                Fotoexpress 38,20 1
                SUMME EUR 38,20
                Partner-Rabatte auf rabattfähige Artikel
                Coupon 20% HiPP -0,25
                dm-Geschenkkarte EUR -25,00
                Nr. XXXX6225
                neuer Wert: 0,00
                Zu zahlender Betrag EUR 12,95
                KARTENZAHLUNG EUR -12,95
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().totalAmount()).isEqualByComparingTo("37.95");
        assertThat(result.receipt().items())
                .extracting(ParsedReceiptItem::totalPrice)
                .containsExactly(new BigDecimal("38.20"), new BigDecimal("-0.25"));
    }

    // Verifies dm discount section headings do not get merged into coupon item descriptions.
    @Test
    void ignoresDmDiscountHeadingsBeforeCouponItems() {
        ReceiptParseResult result = parser.parse("""
                dm
                12.03.2026 16:14 D482/1 000000/1 0000
                Profissimo Schwämme 3,80 1
                dm-Rabatte auf rabattfähige Artikel
                Coupon ProfissimoSchwämme -0,95
                Partner-Rabatte auf rabattfähige Artikel
                Coupon 20% WELEDA Baby -1,40
                SUMME EUR 1,45
                KARTENZAHLUNG EUR -1,45
                Öffnungszeiten auf dm.de
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().items())
                .extracting(ParsedReceiptItem::description)
                .containsExactly(
                        "Profissimo Schwämme",
                        "Coupon ProfissimoSchwämme",
                        "Coupon 20% WELEDA Baby");
    }

    // Verifies pharmacy markdown tables keep only the medicine name and derive quantity/unit price from the price row.
    @Test
    void parsesApothekeMarkdownTableReceipt() {
        ReceiptParseResult result = parser.parse("""
                alex apotheke
                reuschenberg
                Apothekerin Andrea Dutine
                Am Reuschenberger Markt 2
                41466 Neuss
                Tel: 02131 - 125 979 0
                Daniela Baas
                Karlstr. 23
                41469 Neuss
                Kassenbon
                Preisangaben in EUR
                |  Anz | Artikel | Preis | Zuzahlung  |
                | --- | --- | --- | --- |
                |  WICK NASIVIN DOSTR OK BABY (N1)  |   |   |   |
                |  1 | NTR 5 ML | 6,47 | 6,47  |
                **Total EUR:** 6,47
                Datum: 15.05.26
                Uhrzeit: 14:48:18
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().storeName()).isEqualTo("alex apotheke");
        assertThat(result.receipt().storeBranch()).isEqualTo("Am Reuschenberger Markt 2");
        assertThat(result.receipt().totalAmount()).isEqualByComparingTo("6.47");
        assertThat(result.receipt().items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.description()).isEqualTo("WICK NASIVIN DOSTR OK BABY (N1)");
                    assertThat(item.quantity()).isEqualByComparingTo("1");
                    assertThat(item.unit()).isEqualTo("Stk");
                    assertThat(item.unitPrice()).isEqualByComparingTo("6.47");
                    assertThat(item.totalPrice()).isEqualByComparingTo("6.47");
                });
    }

    // Verifies embedded prescription text before the real pharmacy receipt does not leak into receipt metadata or item text.
    @Test
    void parsesEmbeddedApothekeReceiptAfterPrescriptionBlock() {
        ReceiptParseResult result = parser.parse("""
                GD Kranken
                Baas
                Patrick
                Karlstr. 23
                41469 Neuss
                30.08.76
                Dermatologie
                Drususallee
                Dr. med. Peter von Zons
                Drususallee 1, 41460 Neuss
                Fon 02131/25451
                Rezept
                22.05.26
                FLUCONACOL - CT 50MG HARTK, KAP, 14 St, N1
                400 mg (8 Stück als Einmaldosierung)
                30.07 (15890459)
                30.07 #306635 / 13 alex apo. rauschenberg
                Unterschrift des Arztes
                alex apotheke
                reuschenberg
                Apothekerin Andrea Dutine
                Am Reuschenberger Markt 2
                41466 Neuss
                Tel: 02131 - 125 979 0
                Daniela Baas
                Karlstr. 23
                41469 Neuss
                Kassenbon
                Preisangaben in EUR
                |  Anz | Artikel | Preis | Zuzahlung  |
                | --- | --- | --- | --- |
                |  PRz: FLUCONAZOL ACCORD 50MG | 1 HKP 14 ST | 30,07 | 30,07  |
                |  PZN: 15890459 |  |  | BESTELLUNG  |
                Positionen: 1
                Total EUR: 30,07
                MwSt 19% von 30,07: 4,80
                Zahlungsart: EC
                Datum: 22.05.26
                Uhrzeit: 16:42:58
                Vielen Dank für Ihren Einkauf.
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().storeName()).isEqualTo("alex apotheke");
        assertThat(result.receipt().storeBranch()).isEqualTo("Am Reuschenberger Markt 2");
        assertThat(result.receipt().receiptDate()).isEqualTo("2026-05-22");
        assertThat(result.receipt().receiptTime()).isEqualTo("16:42:58");
        assertThat(result.receipt().totalAmount()).isEqualByComparingTo("30.07");
        assertThat(result.receipt().items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.description()).isEqualTo("FLUCONAZOL ACCORD 50MG 1 HKP 14 ST");
                    assertThat(item.quantity()).isNull();
                    assertThat(item.unit()).isNull();
                    assertThat(item.unitPrice()).isNull();
                    assertThat(item.totalPrice()).isEqualByComparingTo("30.07");
                });
    }

    // Verifies pharmacy receipts with a following-line total parse all medicines without header leakage.
    @Test
    void parsesApothekeEndsummeOnFollowingLine() {
        ReceiptParseResult result = parser.parse("""
                Apotheke am Lessingplatz
                Kassenbon
                .A849237
                29.05.2026 08:56
                Artikelpreis Ihr Preis
                Privatrezept (1)
                BEXSEROINJEKTIONSSUSP FER
                ISU 1X0.5ml 1x 122,52 122,52 V
                VAXNEUVANCE ISU I E FER
                ISU 1St 1x 91,44 91,44 V
                HEXYON INJ.-SUSP.O.KANUELE
                ISU 0.5ml 1x 79,54 79,54 V
                Zwischensumme: 293,50
                Privatrezept (2)
                ADVANTAN CREME
                CRE 25g 1x 15,52 15,52 V
                Zwischensumme: 15,52
                Endsumme
                309,02
                Zu zahlen
                EUR 309,02
                EC-Karte
                EUR 309,02
                Rückgeld
                EUR 0,00
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().totalAmount()).isEqualByComparingTo("309.02");
        assertThat(result.receipt().items()).hasSize(4);
        assertThat(result.receipt().items().getFirst().description())
                .isEqualTo("BEXSEROINJEKTIONSSUSP FER ISU 1X0.5ml");
        assertThat(result.receipt().items().getFirst().unitPrice()).isEqualByComparingTo("122.52");
    }

    // Verifies noisy OCR receipts can still use a leading quantity/price line and ignore tax/payment lines.
    @Test
    void parsesLandmarktOfferOcrReceipt() {
        ReceiptParseResult result = parser.parse("""
                LANDMARKT OFFER
                Gubisrather Str. 23
                41516 Grevenbroich
                lel.: 02182 828 94 03
                23/05/2026 SA 10:30
                2X «11.50
                Einstreu T2 23.00
                Einstreu T2 4.50
                Einstreu 12 4.50
                SIEUERRAIE 2 7.000%
                NETTO 2 29.91
                STEUER 2 2.09
                IuoTAl 32.00
                EC-Cash 32.00
                Ust-IdNr. DE
                BEDIENER 01 024473 00000
                StNr .:114/5713/1583
                VIELEN DANK
                FÜR IHREN
                EINKAUF!
                ECR Serial: X030B5AN900032
                TSE Serial:C31AAF39E2CA118B2CF5
                A3DOBAIFBOGL2ECBF7B3
                265 109666 196F955A765
                g9F8
                VO; X030B5AN900032; Kassenbeleg-V1
                ;Beleg“0.00_32.00_0.00_0.00_0.00
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().storeName()).isEqualTo("LANDMARKT OFFER");
        assertThat(result.receipt().storeBranch()).isEqualTo("Gubisrather Str. 23");
        assertThat(result.receipt().receiptDate()).isEqualTo("2026-05-23");
        assertThat(result.receipt().receiptTime()).isEqualTo("10:30");
        assertThat(result.receipt().totalAmount()).isEqualByComparingTo("32.00");
        assertThat(result.receipt().items()).hasSize(3);
        assertThat(result.receipt().items().getFirst())
                .satisfies(item -> {
                    assertThat(item.description()).isEqualTo("Einstreu T2");
                    assertThat(item.quantity()).isEqualByComparingTo("2");
                    assertThat(item.unit()).isEqualTo("Stk");
                    assertThat(item.unitPrice()).isEqualByComparingTo("11.50");
                    assertThat(item.totalPrice()).isEqualByComparingTo("23.00");
                });
        assertThat(result.receipt().items())
                .extracting(ParsedReceiptItem::description)
                .containsExactly("Einstreu T2", "Einstreu T2", "Einstreu 12");
        assertThat(result.receipt().items())
                .extracting(ParsedReceiptItem::totalPrice)
                .containsExactly(new BigDecimal("23.00"), new BigDecimal("4.50"), new BigDecimal("4.50"));
    }

    // Verifies accepted parse_rule rows can act as item-parser fallback after a user approves an AI suggestion.
    @Test
    void usesActiveDatabaseItemRulesAsFallback() {
        ParseRuleRepository parseRuleRepository = mock(ParseRuleRepository.class);
        ParseRule itemRule = new ParseRule(
                "REWE",
                ParseRuleType.ITEM_PATTERN,
                "^#\\s*(?<description>.+?)\\s*::\\s*(?<total>\\d+,\\d{2})\\s*::$",
                null,
                RuleSource.AI_ADAPTED);
        when(parseRuleRepository.findByActiveTrueAndRuleTypeOrderByStoreNameAsc(ParseRuleType.ITEM_PATTERN))
                .thenReturn(List.of(itemRule));
        RuleBasedReceiptParser parserWithDbRules = new RuleBasedReceiptParser(
                new ReceiptParserProperties(),
                parseRuleRepository);

        ReceiptParseResult result = parserWithDbRules.parse("""
                REWE
                18.06.2026
                # Spezialartikel :: 4,20 ::
                SUMME EUR 4,20
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.description()).isEqualTo("Spezialartikel");
                    assertThat(item.totalPrice()).isEqualByComparingTo("4.20");
                });
    }

    // Verifies accepted parse_rule rows can fill missing totals without replacing the existing parser first.
    @Test
    void usesActiveDatabaseTotalRulesAsFallback() {
        ParseRuleRepository parseRuleRepository = mock(ParseRuleRepository.class);
        ParseRule totalRule = new ParseRule(
                "REWE",
                ParseRuleType.TOTAL_PATTERN,
                "^ZAHLBETRAG==(?<total>\\d+,\\d{2})$",
                null,
                RuleSource.AI_ADAPTED);
        when(parseRuleRepository.findByActiveTrueAndRuleTypeOrderByStoreNameAsc(ParseRuleType.TOTAL_PATTERN))
                .thenReturn(List.of(totalRule));
        RuleBasedReceiptParser parserWithDbRules = new RuleBasedReceiptParser(
                new ReceiptParserProperties(),
                parseRuleRepository);

        ReceiptParseResult result = parserWithDbRules.parse("""
                REWE
                18.06.2026
                ARTIKEL A 1,00
                ZAHLBETRAG==1,00
                """);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().totalAmount()).isEqualByComparingTo("1.00");
    }
}
