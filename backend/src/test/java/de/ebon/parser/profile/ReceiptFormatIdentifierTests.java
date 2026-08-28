package de.ebon.parser.profile;

import static org.assertj.core.api.Assertions.assertThat;

import de.ebon.config.ReceiptParserProperties;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReceiptFormatIdentifierTests {

    private final ReceiptTextNormalizer normalizer = new ReceiptTextNormalizer();
    private final ReceiptFormatIdentifier identifier = new ReceiptFormatIdentifier();

    @Test
    void keepsFingerprintStableAcrossVolatileValuesAndSmallOcrVariation() {
        String first = """
                REWE Markt GmbH
                Musterstraße 1
                Bon-Nr. 123456  Kasse 4
                12.07.2026 18:42
                ARTIKEL        PREIS
                Bio Apfel       2,49
                Hafer Drink     1,79
                SUMME EUR       4,28
                Karte           4,28
                MwSt 7%         0,28
                Vielen Dank
                """;
        String second = """
                REWE-MARKT GmbH
                Neue Allee 99
                Bon Nr 987654 / Kasse 8
                03-01-2025 09:07
                ARTIKEL | PREIS
                Roggenbrot | 3,19
                Vollmilch  | 0,99
                SUMNE EUR  | 4,18
                KARTE      | 4,18
                MWST 7 %   | 0,27
                Vieien Dank
                """;

        ReceiptFormatIdentity firstIdentity = identifier.identify(normalizer.normalize(first));
        ReceiptFormatIdentity secondIdentity = identifier.identify(normalizer.normalize(second));

        assertThat(firstIdentity.storeName()).isEqualTo("REWE");
        assertThat(secondIdentity.storeNameKey()).isEqualTo("rewe");
        assertThat(secondIdentity.fingerprint()).isEqualTo(firstIdentity.fingerprint());
    }

    @Test
    void separatesStructuralChangesAndMateriallyDifferentMerchants() {
        String standard = """
                REWE
                Musterstraße 1
                ARTIKEL PREIS
                Bio Apfel 2,49
                SUMME EUR 2,49
                Karte 2,49
                MwSt 7% 0,16
                """;
        String changedFooter = """
                REWE
                Musterstraße 1
                Bio Apfel 2,49
                MwSt 7% 0,16
                SUMME EUR 2,49
                """;
        String otherMerchant = standard.replace("REWE", "ALDI SÜD");

        String standardFingerprint = fingerprint(standard);

        assertThat(fingerprint(changedFooter)).isNotEqualTo(standardFingerprint);
        assertThat(fingerprint(otherMerchant)).isNotEqualTo(standardFingerprint);
    }

    @Test
    void fingerprintsAreVersionedSha256WithoutReceiptText() {
        var identity = identifier.identify(normalizer.normalize("REWE\nGeheimer Artikel 2,49\nSUMME 2,49"));

        assertThat(identity.fingerprintVersion()).isEqualTo(1);
        assertThat(identity.fingerprint()).matches("[a-f0-9]{64}");
        assertThat(identity.toString()).doesNotContain("Geheimer", "2,49");
    }

    @Test
    void nullAndEmptyDocumentsHaveNoInventedMerchantOrBranch() {
        var empty = identifier.identify(normalizer.normalize(null));

        assertThat(identifier.identify(null)).isEqualTo(empty);
        assertThat(identifier.identify(normalizer.normalize(" \n\t"))).isEqualTo(empty);
        assertThat(empty.storeName()).isNull();
        assertThat(empty.storeNameKey()).isEmpty();
        assertThat(empty.storeBranch()).isNull();
        assertThat(empty.storeBranchKey()).isEmpty();
        assertThat(empty.fingerprint()).matches("[a-f0-9]{64}");
    }

    @ParameterizedTest
    @CsvSource({"REWE-Markt GmbH,REWE,rewe", "ALDI SÜD,ALDI,aldi", "LIDL,Lidl,lidl",
        "dm-drogerie markt,dm,dm", "E-CENTER,EDEKA,edeka", "MEDONALDS,McDonald's,mcdonald s",
        "C8A,C&A,c a", "star Tankstelle,star Tankstelle,star tankstelle"})
    void retainsExistingMerchantNamingConventions(String header, String name, String key) {
        var identity = identifier.identify(normalizer.normalize(header + "\nArtikel 1,99\nSUMME 1,99"));

        assertThat(identity.storeName()).isEqualTo(name);
        assertThat(identity.storeNameKey()).isEqualTo(key);
        assertThat(identity.storeBranchKey()).isEmpty();
    }

    @Test
    void unknownMerchantIsNotReplacedByAMerchantMentionedInAnArticle() {
        var identity = identifier.identify(normalizer.normalize("Kleiner Laden\nREWE Geschenk 1,99\nSUMME 1,99"));

        assertThat(identity.storeName()).isEqualTo("Kleiner Laden");
        assertThat(identity.storeNameKey()).isEqualTo("kleiner laden");
        assertThat(fingerprint("Anderer Laden\nREWE Geschenk 1,99\nSUMME 1,99"))
                .isNotEqualTo(identity.fingerprint());
    }

    @Test
    void extractsBranchButDoesNotHashItsAddress() {
        var first = identifier.identify(normalizer.normalize("REWE\nMusterstraße 1\n12345 Musterstadt\nApfel 1,99\nSUMME 1,99"));
        var second = identifier.identify(normalizer.normalize("REWE\nNeue Allee 99\n54321 Andersstadt\nApfel 1,99\nSUMME 1,99"));

        assertThat(first.storeBranch()).isEqualTo("Musterstraße 1");
        assertThat(first.storeBranchKey()).isEqualTo("musterstrasse 1");
        assertThat(second.storeBranchKey()).isEqualTo("neue allee 99");
        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
    }

    @Test
    void resolvesDmBranchMappingAndRetainsTechnicalFallback() {
        var properties = new ReceiptParserProperties();
        properties.setDmBranchMappings(Map.of("D482", "Neuss"));
        var mapped = new ReceiptFormatIdentifier(properties).identify(normalizer.normalize(
                "dm\n12.07.2026 18:42 D482/1\nApfel 1,99\nSUMME 1,99"));
        var fallback = identifier.identify(normalizer.normalize(
                "dm\n12.07.2026 18:42 D482/1\nApfel 1,99\nSUMME 1,99"));

        assertThat(mapped.storeBranch()).isEqualTo("Neuss");
        assertThat(mapped.storeBranchKey()).isEqualTo("neuss");
        assertThat(fallback.storeBranch()).isEqualTo("Filiale D482/1");
        assertThat(fallback.storeBranchKey()).isEqualTo("filiale d482 1");
        assertThat(mapped.fingerprint()).isEqualTo(fallback.fingerprint());
    }

    @Test
    void identityKeysAreLocaleIndependent() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var identity = identifier.identify(normalizer.normalize("LIDL\nFILIALE 42\nApfel 1,99\nSUMME 1,99"));
            assertThat(identity.storeNameKey()).isEqualTo("lidl");
            assertThat(identity.storeBranchKey()).isEqualTo("filiale 42");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void articleNamesAndNumberOfRepeatedItemsDoNotChangeLayout() {
        assertThat(fingerprint("REWE\nARTIKEL PREIS\nApfel 1,99\nSUMME 1,99"))
                .isEqualTo(fingerprint("REWE\nARTIKEL PREIS\nSehr langer Artikelname 2,49\nBrot 3,99\nMilch 1,49\nSUMME 7,97"));
    }

    @Test
    void articleNamesContainingMetadataWordsAreStillItemText() {
        assertThat(fingerprint("REWE\nKasse Spielzeug 1,99\nSUMME 1,99"))
                .isEqualTo(fingerprint("REWE\nApfel 1,99\nSUMME 1,99"));
    }

    @Test
    void articleNamesContainingAddressWordsAreStillItemText() {
        assertThat(fingerprint("REWE\nMarkt Brot 1,99\nSUMME 1,99"))
                .isEqualTo(fingerprint("REWE\nApfel 1,99\nSUMME 1,99"));
    }

    @Test
    void multilineArticleWordsDoNotBecomeMetadata() {
        assertThat(fingerprint("REWE\nARTIKEL PREIS\nKasse Spielzeug\n1,99\nSUMME 1,99"))
                .isEqualTo(fingerprint("REWE\nARTIKEL PREIS\nApfel\n1,99\nSUMME 1,99"));
    }

    @Test
    void paymentWordInsideTheItemTableRemainsAnArticle() {
        assertThat(fingerprint("REWE\nARTIKEL PREIS\nKarte 1,99\nSUMME 1,99"))
                .isEqualTo(fingerprint("REWE\nARTIKEL PREIS\nApfel 1,99\nSUMME 1,99"));
    }

    @Test
    void actualCardPaymentAfterTotalRemainsStructural() {
        assertThat(fingerprint("REWE\nARTIKEL PREIS\nKarte 1,99\nSUMME 1,99\nKarte 1,99"))
                .isNotEqualTo(fingerprint("REWE\nARTIKEL PREIS\nKarte 1,99\nSUMME 1,99"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABCD", "A123", "KASSE"})
    void receiptIdsAreOpaqueRegardlessOfTheirCharacters(String receiptId) {
        assertThat(fingerprint("REWE\nBon Nr " + receiptId + "\nApfel 1,99\nSUMME 1,99"))
                .isEqualTo(fingerprint("REWE\nBon Nr 987654\nApfel 1,99\nSUMME 1,99"));
    }

    @Test
    void dateInsideArticleTextDoesNotBecomeReceiptDateMetadata() {
        assertThat(fingerprint("REWE\nKalender 2026-01-03 1,99\nSUMME 1,99"))
                .isEqualTo(fingerprint("REWE\nKalender 1,99\nSUMME 1,99"));
    }

    @Test
    void unicodeCompatibilityCharactersDoNotChangeIdentity() {
        assertThat(fingerprint("ＲＥＷＥ\nＡｐｆｅｌ １，９９\nＳＵＭＭＥ １，９９"))
                .isEqualTo(fingerprint("REWE\nApfel 1,99\nSUMME 1,99"));
    }

    @Test
    void pricesAndNegativeAmountsDoNotChangeLayout() {
        assertThat(fingerprint("REWE\nArtikel 1,99\nSUMME 1,99"))
                .isEqualTo(fingerprint("REWE\nArtikel -1.234,56\nSUMME -1.234,56"));
    }

    @Test
    void datesTimesAndAlphanumericTransactionIdsDoNotChangeLayout() {
        assertThat(fingerprint("REWE\nBon-Nr. A123 Kasse 4\n12.07.2026 18:42\nApfel 1,99\nSUMME 1,99"))
                .isEqualTo(fingerprint("REWE\nBon Nr Z987654 Kasse 84\n2025-01-03 09:07:15\nApfel 1,99\nSUMME 1,99"));
    }

    @Test
    void punctuationAndWhitespaceAreNotColumns() {
        assertThat(fingerprint("REWE\nARTIKEL   PREIS\nApfel     1,99\nSUMME EUR 1,99"))
                .isEqualTo(fingerprint("REWE\nARTIKEL | PREIS\nApfel\t| 1.99\nSUMME: EUR | 1.99"));
    }

    @Test
    void limitedOcrNoiseInStableAnchorsDoesNotChangeLayout() {
        assertThat(fingerprint("REWE\nARTIKEL PREIS\nApfel 1,99\nSUMME 1,99\nVielen Dank"))
                .isEqualTo(fingerprint("REWE\nARTIKEL PREIS\nApfel 1,99\nSUMNE 1,99\nVieien Dank"));
    }

    @Test
    void reorderedTableColumnsHaveDifferentFingerprints() {
        assertThat(fingerprint("REWE\nARTIKEL PREIS\nApfel 1,99\nSUMME 1,99"))
                .isNotEqualTo(fingerprint("REWE\nPREIS ARTIKEL\n1,99 Apfel\nSUMME 1,99"));
    }

    @Test
    void extraNumericColumnsHaveDifferentFingerprints() {
        assertThat(fingerprint("REWE\nApfel 1,99\nSUMME 1,99"))
                .isNotEqualTo(fingerprint("REWE\nApfel 1,99 3,98\nSUMME 3,98"));
    }

    @Test
    void footerAnchorOrderHasDifferentFingerprints() {
        assertThat(fingerprint("REWE\nApfel 1,99\nSUMME 1,99\nMwSt 7% 0,13\nKarte 1,99"))
                .isNotEqualTo(fingerprint("REWE\nApfel 1,99\nMwSt 7% 0,13\nSUMME 1,99\nKarte 1,99"));
    }

    @Test
    void missingPaymentAnchorHasDifferentFingerprint() {
        assertThat(fingerprint("REWE\nApfel 1,99\nSUMME 1,99\nKarte 1,99"))
                .isNotEqualTo(fingerprint("REWE\nApfel 1,99\nSUMME 1,99"));
    }

    @Test
    void multilineItemStructureDiffersFromSingleLineButIgnoresRepeatedItems() {
        String single = "REWE\nARTIKEL PREIS\nApfel 1,99\nSUMME 1,99";
        String multiline = "REWE\nARTIKEL PREIS\nApfel\n1,99\nSUMME 1,99";
        String repeated = "REWE\nARTIKEL PREIS\nBrot\n2,99\nMilch\n1,49\nSUMME 4,48";

        assertThat(fingerprint(multiline)).isNotEqualTo(fingerprint(single));
        assertThat(fingerprint(repeated)).isEqualTo(fingerprint(multiline));
    }

    private String fingerprint(String text) {
        return identifier.identify(normalizer.normalize(text)).fingerprint();
    }
}
