package de.ebon.parser;

import de.ebon.config.ReceiptParserProperties;
import de.ebon.persistence.model.ParseStatus;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedReceiptParserEdgeTests {

    private final RuleBasedReceiptParser parser = new RuleBasedReceiptParser();

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
}
