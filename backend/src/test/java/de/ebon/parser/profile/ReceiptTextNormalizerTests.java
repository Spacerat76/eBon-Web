package de.ebon.parser.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ReceiptTextNormalizerTests {

    private final ReceiptTextNormalizer normalizer = new ReceiptTextNormalizer();

    @Test
    void preservesOriginalTextAndOneBasedLineNumbersAcrossMixedEndings() {
        NormalizedReceiptDocument document = normalizer.normalize(
                "\uFEFF  REWE Markt  \r\n\r  Bio Apfel  2,49  \n\t\rSUMME EUR 2,49");

        assertThat(document.lines())
                .extracting(
                        NormalizedReceiptLine::originalLineNumber,
                        NormalizedReceiptLine::originalText,
                        NormalizedReceiptLine::matchText)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "  REWE Markt  ", "rewe markt"),
                        org.assertj.core.groups.Tuple.tuple(3, "  Bio Apfel  2,49  ", "bio apfel 2 49"),
                        org.assertj.core.groups.Tuple.tuple(5, "SUMME EUR 2,49", "summe eur 2 49"));
    }

    @Test
    void offsetsSelectTheExactOriginalLineWithoutBomOrLineEndings() {
        String text = "\uFEFFA\r\n\rB😀\n C \r";

        var lines = normalizer.normalize(text).lines();

        assertThat(lines).extracting(NormalizedReceiptLine::startOffset).containsExactly(1, 5, 9);
        assertThat(lines).extracting(NormalizedReceiptLine::endOffset).containsExactly(2, 8, 12);
        assertThat(lines).extracting(NormalizedReceiptLine::originalLineNumber).containsExactly(1, 3, 4);
        for (var line : lines) {
            assertThat(text.substring(line.startOffset(), line.endOffset())).isEqualTo(line.originalText());
        }
    }

    @Test
    void treatsNullAndUnicodeBlankDocumentsAsEmpty() {
        assertThat(normalizer.normalize(null).lines()).isEmpty();
        assertThat(normalizer.normalize("").lines()).isEmpty();
        assertThat(normalizer.normalize("\uFEFF\u00a0\t\u2003\r\n\t").lines()).isEmpty();
    }

    @Test
    void normalizesUnicodeAndPunctuationWithoutChangingOriginalText() {
        var line = normalizer.normalize("  ＡＲＴＩＫＥＬ\u00a0|\u2003PREIS: −2,49 €  ").lines().getFirst();

        assertThat(line.originalText()).isEqualTo("  ＡＲＴＩＫＥＬ\u00a0|\u2003PREIS: −2,49 €  ");
        assertThat(line.matchText()).isEqualTo("artikel preis 2 49");
    }

    @Test
    void documentDefensivelyCopiesItsLines() {
        var source = new ArrayList<>(normalizer.normalize("REWE").lines());
        var document = new NormalizedReceiptDocument(source);
        source.clear();

        assertThat(document.lines()).hasSize(1);
        assertThatThrownBy(() -> document.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void matchingDoesNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(normalizer.normalize("LIDL FILIALE").lines().getFirst().matchText())
                    .isEqualTo("lidl filiale");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
