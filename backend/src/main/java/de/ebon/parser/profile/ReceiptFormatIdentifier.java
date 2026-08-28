package de.ebon.parser.profile;

import de.ebon.config.ReceiptParserProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class ReceiptFormatIdentifier {

    private static final int FINGERPRINT_VERSION = 1;
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\p{L}\\d])[-+−]?(?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)[.,]\\d{2}(?!\\d)");
    private static final Pattern DATE = Pattern.compile(
            "\\b(?:\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}|\\d{4}[./-]\\d{1,2}[./-]\\d{1,2})\\b");
    private static final Pattern TIME = Pattern.compile("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b");
    private static final Pattern DM_BRANCH = Pattern.compile(
            "^\\s*\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}\\s+\\d{1,2}:\\d{2}\\s+([A-Z0-9]{3,6}/\\d+)\\b.*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDRESS = Pattern.compile(
            ".*\\b(?:filiale|[\\p{L}]*strasse|[\\p{L}]*str|[\\p{L}]*platz|[\\p{L}]*markt|"
                    + "[\\p{L}]*weg|[\\p{L}]*allee|[\\p{L}]*ring|[\\p{L}]*gasse)\\b.*");
    private static final Pattern POSTCODE = Pattern.compile("^\\d{5}\\s+\\p{L}.*");
    private static final Pattern METADATA = Pattern.compile(
            "\\b(bon|beleg|kasse|transaktion|terminal|tse|bediener|tel|telefon|fax|uid|stnr)\\b");
    private static final Pattern QUALIFIED_METADATA_ID = Pattern.compile(
            METADATA.pattern() + "[\\s\\p{Punct}]*(?:nr|nummer|id|code|serial)[\\s\\p{Punct}]+\\S+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Set<String> COLUMNS = Set.of(
            "artikel", "bezeichnung", "preis", "einzelpreis", "gesamtpreis", "menge", "anzahl", "eur");

    private final ReceiptParserProperties parserProperties;

    public ReceiptFormatIdentifier() {
        this(new ReceiptParserProperties());
    }

    @Autowired
    public ReceiptFormatIdentifier(ReceiptParserProperties parserProperties) {
        this.parserProperties = parserProperties == null ? new ReceiptParserProperties() : parserProperties;
    }

    public ReceiptFormatIdentity identify(NormalizedReceiptDocument document) {
        List<NormalizedReceiptLine> lines = document == null ? List.of() : document.lines();
        NormalizedReceiptLine merchantLine = merchantLine(lines);
        String storeName = merchantLine == null ? null : merchantName(merchantLine.originalText());
        String storeKey = key(storeName);
        String branch = branch(lines, merchantLine, storeKey);
        String structure = structure(lines, merchantLine);
        return new ReceiptFormatIdentity(
                storeName, storeKey, branch, key(branch),
                sha256("receipt-format-v" + FINGERPRINT_VERSION + "\n" + storeKey + "\n" + structure),
                FINGERPRINT_VERSION);
    }

    private NormalizedReceiptLine merchantLine(List<NormalizedReceiptLine> lines) {
        for (NormalizedReceiptLine line : lines) {
            String original = ReceiptTextNormalizer.displayText(line.originalText());
            if (line.matchText().isEmpty() || isTemporalLine(original)
                    || metadata(original) != null) {
                continue;
            }
            if (AMOUNT.matcher(original).find() || tableColumns(line.matchText()) != null
                    || footerAnchor(line.matchText()) != null) {
                return null;
            }
            return line;
        }
        return null;
    }

    // Keep legacy display names while limiting recognition to the selected merchant header.
    private String merchantName(String original) {
        String text = ReceiptTextNormalizer.matchText(original);
        String compact = text.replace(" ", "");
        if (compact.contains("rewe")) return "REWE";
        if (hasWord(text, "aldi")) return "ALDI";
        if (hasWord(text, "lidl")) return "Lidl";
        if (text.equals("dm") || text.startsWith("dm ")) return "dm";
        if (hasWord(text, "edeka") || text.startsWith("e center")) return "EDEKA";
        if (text.contains("mcdonald") || text.contains("medonald")) return "McDonald's";
        if (compact.equals("ca") || compact.startsWith("c8a") || compact.contains("cunda")
                || compact.contains("canda") || text.startsWith("c a ")) return "C&A";
        if (text.contains("star tankstelle")) return "star Tankstelle";
        return ReceiptTextNormalizer.displayText(original);
    }

    private String branch(List<NormalizedReceiptLine> lines, NormalizedReceiptLine merchant, String storeKey) {
        if (storeKey.equals("dm")) {
            for (NormalizedReceiptLine line : lines) {
                Matcher code = DM_BRANCH.matcher(ReceiptTextNormalizer.displayText(line.originalText()));
                if (code.matches()) {
                    String value = code.group(1).toUpperCase(Locale.ROOT);
                    return parserProperties.resolveDmBranch(value)
                            .map(ReceiptTextNormalizer::displayText).orElse("Filiale " + value);
                }
            }
        }
        for (NormalizedReceiptLine line : lines.stream().limit(12).toList()) {
            String original = ReceiptTextNormalizer.displayText(line.originalText());
            if (line == merchant || isTemporalLine(original)) continue;
            if (AMOUNT.matcher(original).find() || tableColumns(line.matchText()) != null) break;
            if (metadata(original) == null && ADDRESS.matcher(line.matchText()).matches()) {
                return ReceiptTextNormalizer.displayText(line.originalText().replace('*', ' '));
            }
        }
        return null;
    }

    private String structure(List<NormalizedReceiptLine> lines, NormalizedReceiptLine merchant) {
        List<String> parts = new ArrayList<>();
        Set<String> itemShapes = new LinkedHashSet<>();
        String region = "HEADER";
        for (NormalizedReceiptLine line : lines) {
            String text = line.matchText();
            if (line == merchant || text.isEmpty()) continue;
            String original = ReceiptTextNormalizer.displayText(line.originalText());
            boolean hasAmount = AMOUNT.matcher(original).find();
            boolean date = DATE.matcher(original).find();
            boolean time = TIME.matcher(original).find();
            if (isTemporalLine(original)) {
                flushItems(parts, itemShapes);
                parts.add(region + ":" + (date ? "DATE" : "") + (time ? "TIME" : ""));
                continue;
            }
            String metadata = hasAmount ? null : metadata(original);
            if (metadata != null) {
                flushItems(parts, itemShapes);
                parts.add(region + ":META:" + metadata);
                continue;
            }
            if (region.equals("HEADER") && !hasAmount
                    && (ADDRESS.matcher(text).matches() || POSTCODE.matcher(text).matches())) {
                continue;
            }
            String columns = tableColumns(text);
            if (columns != null) {
                flushItems(parts, itemShapes);
                parts.add("TABLE:" + columns);
                region = "ITEMS";
                continue;
            }
            String footer = footerAnchor(text);
            if (footer != null && (region.equals("FOOTER") || footer.equals("TOTAL") || footer.equals("TAX"))) {
                flushItems(parts, itemShapes);
                region = "FOOTER";
                parts.add("FOOTER:" + footer + ":" + lineShape(line.originalText()));
                continue;
            }
            if (region.equals("HEADER") && hasAmount) region = "ITEMS";
            if (region.equals("ITEMS")) {
                // A basket may repeat any row template; the template's first occurrence fixes its order.
                itemShapes.add(lineShape(line.originalText()));
            } else {
                String shape = region + ":" + lineShape(line.originalText());
                if (parts.isEmpty() || !parts.getLast().equals(shape)) parts.add(shape);
            }
        }
        flushItems(parts, itemShapes);
        return String.join("\n", parts);
    }

    private void flushItems(List<String> parts, Set<String> shapes) {
        if (!shapes.isEmpty()) {
            parts.add("ITEMS:" + String.join("|", shapes));
            shapes.clear();
        }
    }

    private String metadata(String text) {
        // Qualified IDs are opaque values, including IDs that spell another metadata anchor.
        text = ReceiptTextNormalizer.matchText(QUALIFIED_METADATA_ID.matcher(text).replaceAll("$1"));
        Matcher matcher = METADATA.matcher(text);
        List<String> anchors = new ArrayList<>();
        while (matcher.find()) anchors.add(matcher.group());
        String remainder = METADATA.matcher(text).replaceAll(" ")
                .replaceAll("\\b(?:nr|nummer|id|code|serial)\\b", " ");
        return anchors.isEmpty() || !onlyValues(remainder) ? null : String.join(",", anchors);
    }

    private boolean isTemporalLine(String text) {
        if (!DATE.matcher(text).find() && !TIME.matcher(text).find()) return false;
        String remainder = TIME.matcher(DATE.matcher(text).replaceAll(" ")).replaceAll(" ");
        return onlyValues(ReceiptTextNormalizer.matchText(remainder)
                .replaceAll("\\b(?:datum|zeit|uhr)\\b", " "));
    }

    private boolean onlyValues(String text) {
        for (String token : text.trim().split("\\s+")) {
            if (!token.isEmpty() && token.codePoints().noneMatch(Character::isDigit)) return false;
        }
        return true;
    }

    private String tableColumns(String text) {
        List<String> words = List.of(text.split(" "));
        if (words.size() < 2 || !COLUMNS.containsAll(words)) return null;
        return String.join(",", words);
    }

    private String footerAnchor(String text) {
        String words = text.replaceAll("\\b\\d+\\b", " ")
                .replaceAll("\\b(?:eur|euro)\\b", " ").replaceAll("\\s+", " ").trim();
        return switch (words) {
            case "summe", "sumne", "gesamt", "gesamtbetrag", "total", "zu zahlen", "betrag" -> "TOTAL";
            case "karte", "kartenzahlung", "ec karte", "girocard", "kreditkarte" -> "CARD";
            case "bar", "barzahlung", "gegeben" -> "CASH";
            case "ruckgeld", "rückgeld", "wechselgeld" -> "CHANGE";
            case "mwst", "ust", "steuer", "mehrwertsteuer" -> "TAX";
            case "vielen dank", "vieien dank" -> "THANKS";
            default -> null;
        };
    }

    private String lineShape(String original) {
        String normalized = Normalizer.normalize(original, Normalizer.Form.NFKC);
        normalized = TIME.matcher(DATE.matcher(normalized).replaceAll(" ")).replaceAll(" ");
        Matcher amounts = AMOUNT.matcher(normalized);
        List<String> columns = new ArrayList<>();
        int offset = 0;
        while (amounts.find()) {
            addTextShape(columns, normalized.substring(offset, amounts.start()));
            columns.add("AMOUNT");
            offset = amounts.end();
        }
        addTextShape(columns, normalized.substring(offset));
        return String.join(",", columns);
    }

    private void addTextShape(List<String> columns, String value) {
        String text = ReceiptTextNormalizer.matchText(value).replaceAll("\\b(?:eur|euro)\\b", "").trim();
        if (!text.isEmpty()) columns.add(text.matches("\\d+(?: \\d+)*") ? "NUMBER" : "TEXT");
    }

    private boolean hasWord(String text, String word) {
        return (" " + text + " ").contains(" " + word + " ");
    }

    private String key(String text) {
        return text == null ? "" : ReceiptTextNormalizer.matchText(text);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
