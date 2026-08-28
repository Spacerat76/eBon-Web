package de.ebon.parser.profile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class ReceiptTextNormalizer {

    private static final Pattern LINE_ENDING = Pattern.compile("\\R");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

    public NormalizedReceiptDocument normalize(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return new NormalizedReceiptDocument(List.of());
        }
        List<NormalizedReceiptLine> lines = new ArrayList<>();
        int start = rawText.charAt(0) == '\uFEFF' ? 1 : 0;
        int lineNumber = 1;
        Matcher endings = LINE_ENDING.matcher(rawText);
        while (endings.find()) {
            addLine(lines, rawText, start, endings.start(), lineNumber++);
            start = endings.end();
        }
        addLine(lines, rawText, start, rawText.length(), lineNumber);
        return new NormalizedReceiptDocument(lines);
    }

    private void addLine(List<NormalizedReceiptLine> lines, String text, int start, int end, int number) {
        String original = text.substring(start, end);
        if (!displayText(original).isEmpty()) {
            lines.add(new NormalizedReceiptLine(number, original, matchText(original), start, end));
        }
    }

    static String matchText(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("ß", "ss");
        return NON_WORD.matcher(normalized).replaceAll(" ").trim();
    }

    static String displayText(String text) {
        return WHITESPACE.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll(" ").trim();
    }
}
