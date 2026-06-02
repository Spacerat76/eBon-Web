package de.ebon.parser;

import de.ebon.persistence.model.ParseStatus;
import java.math.BigDecimal;
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
    void parsesApothekeMarkdownTableReceipt() {
        ReceiptParseResult result = parser.parse("""
                alex apotheke
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
        assertThat(result.receipt().totalAmount()).isEqualByComparingTo("6.47");
        assertThat(result.receipt().items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.description()).contains("WICK NASIVIN");
                    assertThat(item.totalPrice()).isEqualByComparingTo("6.47");
                });
    }

    @Test
    void parsesApothekeEndsummeOnFollowingLine() {
        ReceiptParseResult result = parser.parse("""
                Apotheke am Lessingplatz
                Kassenbon
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
    }
}
