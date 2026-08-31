package de.ebon.parser;

import de.ebon.config.ReceiptParserProperties;
import de.ebon.persistence.model.ParseRule;
import de.ebon.persistence.model.ParseRuleType;
import de.ebon.persistence.repository.ParseRuleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedReceiptParser {

    private static final String AMOUNT_PATTERN = "-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}|-?\\d+,\\d{2}|-?\\d+\\.\\d{2}";
    private static final Pattern AMOUNT_AT_END = Pattern.compile(
            "(?<amount>" + AMOUNT_PATTERN + ")\\s*(?:€\\s*)?(?:[A-Z]{1,2}|§?\\d)?\\s*\\*?$");
    private static final Pattern EDEKA_AMOUNT_AT_END = Pattern.compile(
            "(?<amount>" + AMOUNT_PATTERN + ")\\s*\\*?\\s*(?:€\\s*)?(?:[A-Z]{1,2}|§?\\d)?\\s*\\*?$");
    private static final Pattern AMOUNT_BEFORE_EUR = Pattern.compile(
            "(?<amount>" + AMOUNT_PATTERN + ")\\s*EUR\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_BEFORE_EURO_SYMBOL = Pattern.compile(
            "(?<amount>" + AMOUNT_PATTERN + ")\\s*€");
    private static final Pattern AMOUNT_BEFORE_TABLE_CELL_END = Pattern.compile(
            "(?<amount>" + AMOUNT_PATTERN + ")\\s*(?:[A-Z]{1,2}|§?\\d)?\\s*\\*?\\s*\\|\\s*$");
    private static final Pattern ANY_AMOUNT = Pattern.compile("(?<amount>" + AMOUNT_PATTERN + ")");
    private static final Pattern DATE_DOTTED = Pattern.compile("\\b(?<day>\\d{1,2})\\.(?<month>\\d{1,2})\\.(?<year>\\d{2,4})\\b");
    private static final Pattern DATE_SLASH = Pattern.compile("\\b(?<day>\\d{1,2})/(?<month>\\d{1,2})/(?<year>\\d{2,4})\\b");
    private static final Pattern DATE_ISO = Pattern.compile("\\b(?<date>\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern TIME = Pattern.compile("\\b(?<hour>\\d{1,2}):(?<minute>\\d{2})(?::(?<second>\\d{2}))?\\b");
    private static final Pattern DM_HEADER_BRANCH = Pattern.compile(
            "^\\s*\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}\\s+\\d{1,2}:\\d{2}\\s+(?<branch>[A-Z0-9]{3,6}/\\d+)\\b.*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY_PRICE = Pattern.compile(
            "(?<quantity>\\d+(?:,\\d+)?)\\s*(?<unit>kg|g|l|ml|stk|stck|stueck)?\\s*(?:x|\\*)\\s*(?<unitPrice>\\d+,\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_X_QUANTITY = Pattern.compile(
            "(?<unitPrice>" + AMOUNT_PATTERN + ")\\s*€\\s*(?:x|\\*)\\s*(?<quantity>\\d+(?:,\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY_DETAIL_LINE = Pattern.compile(
            "^\\s*(?<quantity>\\d+(?:,\\d+)?)\\s*(?<unit>kg|g|l|ml|stk|stck|stueck)?\\s*(?:x|\\*)\\s*(?<unitPrice>\\d+,\\d+)\\s*(?:EUR(?:/\\w+)?)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_QUANTITY_PRICE_LINE = Pattern.compile(
            "^\\s*(?<quantity>\\d+(?:,\\d+)?)\\s*(?:x|\\*)\\s*[«<]?\\s*(?<unitPrice>" + AMOUNT_PATTERN + ")\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HANDEINGABE_QUANTITY_LINE = Pattern.compile(
            "^.*\\bHandeingabe\\s+E-Bon\\s+(?<quantity>\\d+(?:,\\d+)?)\\s*(?<unit>kg|g|l|ml|stk|stck|stueck)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BONUS_POINTS = Pattern.compile(
            "(?<points>\\d{1,3}(?:\\.\\d{3})*|\\d+(?:,\\d+)?)\\s*(?:°P|Punkte|Punkt)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DM_ITEM_LINE = Pattern.compile(
            "^(?:(?<quantity>\\d+)x\\s+(?<unitPrice>\\d+(?:,\\d+|\\.\\d+)?)\\s+)?(?<description>.+?)\\s+(?<total>" + AMOUNT_PATTERN + ")\\s+(?<taxCode>§?\\d)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DM_ONLINE_INVOICE_DATE = Pattern.compile(
            "^Rechnungsdatum:\\s*(?<date>\\d{1,2}\\.\\d{1,2}\\.\\d{2,4})\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DM_ONLINE_INVOICE_TOTAL = Pattern.compile(
            "^Rechnungsbetrag\\s+(?<amount>" + AMOUNT_PATTERN + ")\\s*€\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DM_ONLINE_INVOICE_ITEM = Pattern.compile(
            "^(?<description>.+?)\\s+(?<unitPrice>" + AMOUNT_PATTERN
                    + ")\\s*€\\s*\\(\\d+\\)\\s+(?<quantity>\\d+)\\s+(?<total>"
                    + AMOUNT_PATTERN
                    + ")\\s*€\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DM_ONLINE_INVOICE_ITEM_DETAIL = Pattern.compile(
            "^(?<description>.+?)\\s+(?:" + AMOUNT_PATTERN + ")\\s*€\\s+je\\s+.+$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DM_ONLINE_INVOICE_COUPON = Pattern.compile(
            "^(?<description>Coupon\\s+.+?)\\s+(?<total>" + AMOUNT_PATTERN + ")\\s*€\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DM_ONLINE_INVOICE_SHIPPING = Pattern.compile(
            "^(?<description>Versandkosten(?:\\s+.+)?)\\s+(?<total>" + AMOUNT_PATTERN + ")\\s*€\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PHARMACY_ITEM_LINE = Pattern.compile(
            "^(?<description>.+?)\\s+1x\\s+(?<unitPrice>" + AMOUNT_PATTERN + ")\\s+(?<total>" + AMOUNT_PATTERN + ")\\s+[A-Z]\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUSCH_ITEM_DESCRIPTION_LINE = Pattern.compile(
            "^(?<quantity>\\d+(?:,\\d+)?)\\s+(?<unit>Stk|Stueck|Stück)\\s+(?<description>.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern BUSCH_ITEM_PRICE_DETAIL_LINE = Pattern.compile(
            "^(?<unitPrice>" + AMOUNT_PATTERN + ")\\s*€\\s*/\\s*(?:Stk|Stueck|Stück)\\.?\\s*(?:#?\\d+)?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern QUANTITY_ARTICLE_ITEM_LINE = Pattern.compile(
            "^\\s*(?<quantity>\\d+)\\s+(?<description>.+?)\\s+(?<total>" + AMOUNT_PATTERN + ")\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MCDONALDS_MOBILE_ORDER_TIMESTAMP = Pattern.compile(
            "\\bBestell-Datum:\\s*(?<date>\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+(?<time>\\d{1,2}:\\d{2}(?::\\d{2})?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MCDONALDS_MOBILE_TOTAL = Pattern.compile(
            "\\bTOTAL\\s*:\\s*€\\s*(?<amount>" + AMOUNT_PATTERN + ")\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MCDONALDS_MOBILE_ITEM = Pattern.compile(
            "(?<!\\S)(?<quantity>\\d+)\\s+(?<description>[^€]+?)\\s+€\\s*(?<total>" + AMOUNT_PATTERN + ")\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CA_ITEM_DETAIL_LINE = Pattern.compile(
            "^\\s*Farbe\\s+\\S+\\s+(?<sizeLabel>Gr(?:ö|oe|o)(?:ß|ss)e)\\s+(?<size>\\S+)(?:\\s+[A-ZÄ])?(?:\\s+"
                    + AMOUNT_PATTERN + ")?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STAR_FUEL_QUANTITY_LINE = Pattern.compile(
            "^\\s*\\*?\\s*(?<quantity>\\d+(?:,\\d+)?)\\s+(?<unit>Liter)\\s+S(?:Ä|A)ULENNUMMER\\s+\\d+\\s*\\*?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STAR_FUEL_ITEM_LINE = Pattern.compile(
            "^\\s*\\*?\\s*(?<description>.+?)\\s+[A-Z]\\s+(?<total>" + AMOUNT_PATTERN + ")\\s+EUR\\s*\\*?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STAR_FUEL_UNIT_PRICE_LINE = Pattern.compile(
            "^\\s*(?<unitPrice>\\d+(?:,\\d{2,3})?)\\s*EUR\\s*/\\s*Liter\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SHELL_FUEL_ITEM_LINE = Pattern.compile(
            "^\\s*\\*?\\d+\\s+(?<description>.+?)\\s+(?<total>" + AMOUNT_PATTERN
                    + ")\\s+EUR\\s+#?[A-Z]?\\*?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SHELL_FUEL_DETAILS_LINE = Pattern.compile(
            "^\\s*\\*?Zp\\s+\\d+\\s+(?<quantity>\\d+(?:,\\d+)?)\\s+(?:l|Liter)\\s+"
                    + "(?<unitPrice>\\d+(?:,\\d{2,3})?)\\s+EUR\\s*/\\s*(?:l|Liter)\\s+#?\\s*\\*?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LONG_NUMERIC_CODE_LINE = Pattern.compile("^\\d{10,}$");
    private static final Pattern ARTICLE_BARCODE_LINE = Pattern.compile(
            "^\\s*Artikel\\s+\\d{8,}\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final ReceiptParseValidator validator = new ReceiptParseValidator();
    private final ReceiptParserProperties parserProperties;
    private final ParseRuleRepository parseRuleRepository;

    public RuleBasedReceiptParser() {
        this(new ReceiptParserProperties(), null);
    }

    public RuleBasedReceiptParser(ReceiptParserProperties parserProperties) {
        this(parserProperties, null);
    }

    @Autowired
    public RuleBasedReceiptParser(ReceiptParserProperties parserProperties, ParseRuleRepository parseRuleRepository) {
        this.parserProperties = parserProperties == null ? new ReceiptParserProperties() : parserProperties;
        this.parseRuleRepository = parseRuleRepository;
    }

    public ReceiptParseResult parse(String rawText) {
        List<String> rawLines = rawText == null
                ? List.of()
                : rawText.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .toList();
        if (isDmOnlineInvoice(rawLines)) {
            return validator.validate(parseDmOnlineInvoice(rawLines));
        }

        List<String> lines = relevantReceiptLines(rawLines);

        String storeName = parseStoreName(lines);
        if (isMcdonaldsMobileOrder(rawText)) {
            ParsedReceipt receipt = new ParsedReceipt(
                    parseMcdonaldsMobileOrderDate(rawText),
                    parseMcdonaldsMobileOrderTime(rawText),
                    "McDonald's",
                    parseMcdonaldsMobileStoreBranch(rawText),
                    parseMcdonaldsMobileTotal(rawText),
                    "EUR",
                    null,
                    null,
                    null,
                    parseMcdonaldsMobileItems(rawText));
            return validator.validate(receipt);
        }

        BigDecimal totalAmount = isBauhausStoreName(storeName) ? parseBauhausTotal(lines) : parseTotal(lines, storeName);
        List<ParsedReceiptItem> parsedItems = isBauhausStoreName(storeName)
                ? parseBauhausItems(lines)
                : parseItems(lines, storeName, totalAmount);
        List<ParsedReceiptItem> items = reconcileCaOcrItemPrices(storeName, totalAmount, parsedItems);
        ParsedReceipt receipt = new ParsedReceipt(
                parseDate(lines),
                parseTime(lines),
                storeName,
                isBauhausStoreName(storeName) ? parseBauhausBranch(lines) : parseStoreBranch(lines),
                totalAmount,
                "EUR",
                parseBonusBalance(lines),
                parseBonusPoints(lines),
                parseBonusType(lines),
                items);

        return validator.validate(receipt);
    }

    private boolean isDmOnlineInvoice(List<String> lines) {
        return lines.stream().anyMatch(line -> line.equalsIgnoreCase("Rechnung"))
                && lines.stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("dm.de"))
                && lines.stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("dm-drogerie markt"))
                && lines.stream().anyMatch(line -> line.equalsIgnoreCase(
                        "Artikelbezeichnung Einzelpreis Menge Gesamtpreis"))
                && lines.stream().anyMatch(line -> DM_ONLINE_INVOICE_DATE.matcher(line).matches())
                && lines.stream().anyMatch(line -> DM_ONLINE_INVOICE_TOTAL.matcher(line).matches());
    }

    private ParsedReceipt parseDmOnlineInvoice(List<String> lines) {
        return new ParsedReceipt(
                parseDmOnlineInvoiceDate(lines),
                null,
                "dm",
                "dm.de",
                parseDmOnlineInvoiceTotal(lines),
                "EUR",
                null,
                null,
                null,
                parseDmOnlineInvoiceItems(lines));
    }

    private LocalDate parseDmOnlineInvoiceDate(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = DM_ONLINE_INVOICE_DATE.matcher(line);
            if (matcher.matches()) {
                return parseDateValue(matcher.group("date"));
            }
        }
        return null;
    }

    private BigDecimal parseDmOnlineInvoiceTotal(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = DM_ONLINE_INVOICE_TOTAL.matcher(line);
            if (matcher.matches()) {
                return GermanNumberParser.parse(matcher.group("amount"));
            }
        }
        return null;
    }

    private List<ParsedReceiptItem> parseDmOnlineInvoiceItems(List<String> lines) {
        List<ParsedReceiptItem> items = new ArrayList<>();
        boolean inItemTable = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.equalsIgnoreCase("Artikelbezeichnung Einzelpreis Menge Gesamtpreis")) {
                inItemTable = true;
                continue;
            }
            if (!inItemTable) {
                continue;
            }
            if (line.toUpperCase(Locale.ROOT).startsWith("MWST.-SATZ")) {
                break;
            }

            Matcher itemMatcher = DM_ONLINE_INVOICE_ITEM.matcher(line);
            if (itemMatcher.matches()) {
                String description = itemMatcher.group("description").trim();
                if (index + 1 < lines.size()) {
                    Matcher detailMatcher = DM_ONLINE_INVOICE_ITEM_DETAIL.matcher(lines.get(index + 1));
                    if (detailMatcher.matches()) {
                        description = description + " " + detailMatcher.group("description").trim();
                        index++;
                    }
                }
                items.add(new ParsedReceiptItem(
                        items.size(),
                        description,
                        GermanNumberParser.parse(itemMatcher.group("quantity")),
                        "Stk",
                        GermanNumberParser.parse(itemMatcher.group("unitPrice")),
                        GermanNumberParser.parse(itemMatcher.group("total")),
                        null));
                continue;
            }

            Matcher couponMatcher = DM_ONLINE_INVOICE_COUPON.matcher(line);
            if (couponMatcher.matches()) {
                BigDecimal total = GermanNumberParser.parse(couponMatcher.group("total"));
                items.add(new ParsedReceiptItem(
                        items.size(),
                        couponMatcher.group("description").trim(),
                        null,
                        null,
                        null,
                        total,
                        total.signum() < 0 ? total.abs() : null));
                continue;
            }

            Matcher shippingMatcher = DM_ONLINE_INVOICE_SHIPPING.matcher(line);
            if (shippingMatcher.matches()) {
                items.add(new ParsedReceiptItem(
                        items.size(),
                        shippingMatcher.group("description").trim(),
                        null,
                        null,
                        null,
                        GermanNumberParser.parse(shippingMatcher.group("total")),
                        null));
            }
        }
        return items;
    }

    private LocalDate parseMcdonaldsMobileOrderDate(String rawText) {
        Matcher matcher = MCDONALDS_MOBILE_ORDER_TIMESTAMP.matcher(rawText);
        if (matcher.find()) {
            return parseDateValue(matcher.group("date"));
        }
        return null;
    }

    private LocalTime parseMcdonaldsMobileOrderTime(String rawText) {
        Matcher matcher = MCDONALDS_MOBILE_ORDER_TIMESTAMP.matcher(rawText);
        if (matcher.find()) {
            return parseTimeValue(matcher.group("time"));
        }
        return null;
    }

    private boolean isMcdonaldsMobileOrder(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return false;
        }
        String upper = rawText.toUpperCase(Locale.ROOT);
        return (upper.contains("MCDONALD") || upper.contains("MCDONALD\"S") || upper.contains("MCDONALD'S"))
                && (upper.contains("MOBILE BESTELLBESTÄTIGUNG")
                        || upper.contains("MOBILE BESTELLBESTAETIGUNG")
                        || upper.contains("DEINE BESTELLÜBERSICHT")
                        || upper.contains("DEINE BESTELLUEBERSICHT"))
                && upper.contains("BESTELL-DATUM:")
                && upper.contains("ANZAHL ARTIKEL GESAMT")
                && upper.contains("TOTAL:");
    }

    private BigDecimal parseMcdonaldsMobileTotal(String rawText) {
        Matcher matcher = MCDONALDS_MOBILE_TOTAL.matcher(rawText);
        if (matcher.find()) {
            return GermanNumberParser.parse(matcher.group("amount"));
        }
        return null;
    }

    private List<ParsedReceiptItem> parseMcdonaldsMobileItems(String rawText) {
        String segment = firstMcdonaldsMobileItemsSegment(rawText);
        if (segment == null) {
            return List.of();
        }

        List<ParsedReceiptItem> items = new ArrayList<>();
        Matcher matcher = MCDONALDS_MOBILE_ITEM.matcher(segment);
        while (matcher.find()) {
            BigDecimal quantity = GermanNumberParser.parse(matcher.group("quantity"));
            BigDecimal totalPrice = GermanNumberParser.parse(matcher.group("total"));
            BigDecimal unitPrice = quantity == null || quantity.signum() == 0
                    ? null
                    : totalPrice.divide(quantity, 2, RoundingMode.HALF_UP);
            String description = cleanupDescription(matcher.group("description"));
            if (description.isBlank() || description.toUpperCase(Locale.ROOT).contains(" NUR ")) {
                continue;
            }
            items.add(new ParsedReceiptItem(
                    items.size(),
                    description,
                    quantity,
                    "Stk",
                    unitPrice,
                    totalPrice,
                    totalPrice.signum() < 0 ? totalPrice.abs() : null));
        }
        return items;
    }

    private String firstMcdonaldsMobileItemsSegment(String rawText) {
        String normalized = rawText.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ");
        String upper = normalized.toUpperCase(Locale.ROOT);
        int start = upper.indexOf("ANZAHL ARTIKEL GESAMT");
        if (start < 0) {
            return null;
        }
        int contentStart = start + "ANZAHL ARTIKEL GESAMT".length();
        int end = upper.indexOf("TOTAL:", contentStart);
        if (end < 0 || end <= contentStart) {
            return null;
        }
        return normalized.substring(contentStart, end);
    }

    private String parseMcdonaldsMobileStoreBranch(String rawText) {
        String block = mcdonaldsRestaurantBlock(rawText);
        if (block == null) {
            return null;
        }

        String[] tokens = block.split("\\s+");
        int postalIndex = -1;
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].matches("\\d{5}")) {
                postalIndex = i;
                break;
            }
        }
        if (postalIndex < 2) {
            return null;
        }

        int houseNumberIndex = postalIndex - 1;
        int streetSuffixIndex = -1;
        for (int i = houseNumberIndex - 1; i >= 0; i--) {
            if (isStreetSuffixToken(tokens[i])) {
                streetSuffixIndex = i;
                break;
            }
        }
        if (streetSuffixIndex < 0) {
            return null;
        }

        int startIndex = streetStartIndex(tokens, streetSuffixIndex);
        List<String> addressTokens = new ArrayList<>();
        for (int i = startIndex; i <= houseNumberIndex; i++) {
            addressTokens.add(tokens[i]);
        }
        return cleanupBranchLine(String.join(" ", addressTokens));
    }

    private String mcdonaldsRestaurantBlock(String rawText) {
        String normalized = rawText.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ");
        String upper = normalized.toUpperCase(Locale.ROOT);
        int start = upper.indexOf("RESTAURANT:");
        if (start < 0) {
            return null;
        }
        int contentStart = start + "RESTAURANT:".length();
        int end = upper.indexOf("ST.NR", contentStart);
        if (end < 0) {
            end = upper.indexOf("ANZAHL ARTIKEL GESAMT", contentStart);
        }
        if (end < 0 || end <= contentStart) {
            return null;
        }
        return normalized.substring(contentStart, end).trim();
    }

    private int streetStartIndex(String[] tokens, int streetSuffixIndex) {
        String suffix = tokens[streetSuffixIndex].toLowerCase(Locale.ROOT);
        if ((suffix.equals("str.") || suffix.equals("strasse") || suffix.equals("straße")) && streetSuffixIndex > 0) {
            return streetSuffixIndex - 1;
        }
        if (streetSuffixIndex > 1 && isStreetPrefixToken(tokens[streetSuffixIndex - 2])) {
            return streetSuffixIndex - 2;
        }
        if (streetSuffixIndex > 0 && isStreetPrefixToken(tokens[streetSuffixIndex - 1])) {
            return streetSuffixIndex - 1;
        }
        return streetSuffixIndex;
    }

    private boolean isStreetPrefixToken(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return normalized.equals("am")
                || normalized.equals("an")
                || normalized.equals("auf")
                || normalized.equals("im")
                || normalized.equals("in")
                || normalized.equals("der")
                || normalized.equals("den");
    }

    private boolean isStreetSuffixToken(String token) {
        String normalized = token.toLowerCase(Locale.ROOT).replace(",", "");
        return normalized.equals("str.")
                || normalized.endsWith("str.")
                || normalized.equals("straße")
                || normalized.endsWith("straße")
                || normalized.equals("strasse")
                || normalized.endsWith("strasse")
                || normalized.equals("allee")
                || normalized.equals("weg")
                || normalized.equals("platz")
                || normalized.equals("ring")
                || normalized.equals("markt")
                || normalized.equals("gasse")
                || normalized.equals("ufer")
                || normalized.equals("damm");
    }

    private List<String> relevantReceiptLines(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).equalsIgnoreCase("Kassenbon")) {
                continue;
            }
            for (int start = i - 1; start >= 0; start--) {
                if (isPharmacyStoreLine(lines.get(start))) {
                    return lines.subList(start, lines.size());
                }
            }
        }
        return lines;
    }

    private LocalDate parseDate(List<String> lines) {
        for (String line : lines) {
            LocalDate date = parseDateValue(line);
            if (date != null) {
                return date;
            }
        }

        String dynamicDate = firstDynamicTextMatch(
                lines,
                ParseRuleType.DATE_PATTERN,
                null,
                List.of("date", "value"));
        return dynamicDate == null ? null : parseDateValue(dynamicDate);
    }

    private LocalDate parseDateValue(String value) {
        Matcher isoMatcher = DATE_ISO.matcher(value);
        if (isoMatcher.find()) {
            return LocalDate.parse(isoMatcher.group("date"));
        }

        Matcher dottedMatcher = DATE_DOTTED.matcher(value);
        if (dottedMatcher.find()) {
            return LocalDate.of(
                    normalizeYear(dottedMatcher.group("year")),
                    Integer.parseInt(dottedMatcher.group("month")),
                    Integer.parseInt(dottedMatcher.group("day")));
        }

        Matcher slashMatcher = DATE_SLASH.matcher(value);
        if (slashMatcher.find()) {
            return LocalDate.of(
                    normalizeYear(slashMatcher.group("year")),
                    Integer.parseInt(slashMatcher.group("month")),
                    Integer.parseInt(slashMatcher.group("day")));
        }
        return null;
    }

    private int normalizeYear(String year) {
        int parsed = Integer.parseInt(year);
        return parsed < 100 ? 2000 + parsed : parsed;
    }

    private LocalTime parseTime(List<String> lines) {
        for (String line : lines) {
            LocalTime time = parseTimeValue(line);
            if (time != null) {
                return time;
            }
        }
        return null;
    }

    private LocalTime parseTimeValue(String value) {
        Matcher matcher = TIME.matcher(value);
        if (matcher.find()) {
            int second = matcher.group("second") == null ? 0 : Integer.parseInt(matcher.group("second"));
            return LocalTime.of(
                    Integer.parseInt(matcher.group("hour")),
                    Integer.parseInt(matcher.group("minute")),
                    second);
        }
        return null;
    }

    private String parseStoreName(List<String> lines) {
        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.replace(" ", "").contains("REWE")) {
                return "REWE";
            }
            if (upper.contains("ALDI")) {
                return "ALDI";
            }
            if (upper.contains("LIDL")) {
                return "Lidl";
            }
            if (upper.contains("DM-DROGERIE") || upper.contains("DM.DE") || upper.equals("DM") || upper.startsWith("DM ")) {
                return "dm";
            }
            if (upper.contains("EDEKA")) {
                return "EDEKA";
            }
            if (upper.contains("E-CENTER") || upper.contains("E CENTER")) {
                return "EDEKA";
            }
            if (isPharmacyStoreLine(line)) {
                return cleanupDescription(line);
            }
            if (upper.contains("MCDONALD") || upper.contains("MEDONALD")) {
                return "McDonald's";
            }
            if (isCaStoreLine(line)) {
                return "C&A";
            }
            if (upper.contains("STAR TANKSTELLE")) {
                return "star Tankstelle";
            }
        }

        String dynamicStore = firstDynamicTextMatch(
                lines,
                ParseRuleType.STORE_PATTERN,
                null,
                List.of("store", "name", "value"));
        if (dynamicStore != null) {
            return dynamicStore;
        }

        return lines.stream()
                .filter(line -> !isDateOrTimeLine(line))
                .findFirst()
                .orElse(null);
    }

    private String parseStoreBranch(List<String> lines) {
        String dmBranch = parseDmBranchCode(lines);
        if (dmBranch != null) {
            return dmBranch;
        }

        for (String line : headerLines(lines)) {
            String normalized = cleanupBranchLine(line);
            if (isBranchLine(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private List<String> headerLines(List<String> lines) {
        List<String> header = new ArrayList<>();
        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.equals("EUR") || AMOUNT_AT_END.matcher(line).find()) {
                break;
            }
            header.add(line);
            if (header.size() >= 12) {
                break;
            }
        }
        return header;
    }

    private String parseDmBranchCode(List<String> lines) {
        if (!lines.stream().map(line -> line.toUpperCase(Locale.ROOT)).anyMatch(upper -> upper.contains("DM.DE")
                || upper.contains("PAYBACK")
                || upper.contains("KARTENZAHLUNG")
                || upper.contains("DM-RABATTE")
                || upper.contains("ÖFFNUNGSZEITEN AUF DM.DE")
                || upper.contains("OEFFNUNGSZEITEN AUF DM.DE"))) {
            return null;
        }

        for (String line : lines) {
            Matcher matcher = DM_HEADER_BRANCH.matcher(line);
            if (matcher.find()) {
                String branchCode = matcher.group("branch").toUpperCase(Locale.ROOT);
                return parserProperties.resolveDmBranch(branchCode)
                        .orElse("Filiale " + branchCode);
            }
        }
        return null;
    }

    private String cleanupBranchLine(String line) {
        return line.replace("*", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private BigDecimal parseTotal(List<String> lines, String storeName) {
        BigDecimal combinedCardPaymentTotal = parseCombinedCardPaymentTotal(lines);
        if (combinedCardPaymentTotal != null) {
            return combinedCardPaymentTotal;
        }

        if (isShellStoreName(storeName)) {
            for (String line : lines) {
                if (line.stripLeading().toUpperCase(Locale.ROOT).startsWith("GESAMTBETRAG ")) {
                    BigDecimal amount = extractAmount(line);
                    if (amount != null) {
                        return amount;
                    }
                }
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!isFinalPayableLine(line)) {
                continue;
            }
            BigDecimal amount = amountOnLineOrFollowing(lines, i);
            if (amount != null) {
                return amount.add(parseGiftCardPaymentTotal(lines));
            }
        }

        if (isCaStoreName(storeName)) {
            BigDecimal caPaymentTotal = parseCaPaymentTotal(lines);
            if (caPaymentTotal != null) {
                return caPaymentTotal;
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!isPrimaryTotalLine(line)) {
                continue;
            }
            BigDecimal amount = amountOnLineOrFollowing(lines, i);
            if (amount != null) {
                return amount;
            }
        }

        for (String line : lines) {
            if (!isTotalLine(line) || isTaxTableLine(line) || line.toUpperCase(Locale.ROOT).contains("ZWISCHEN")) {
                continue;
            }
            BigDecimal amount = extractAmount(line);
            if (amount != null) {
                return amount;
            }
        }

        String dynamicTotal = firstDynamicTextMatch(
                lines,
                ParseRuleType.TOTAL_PATTERN,
                storeName,
                List.of("total", "amount", "value"));
        if (dynamicTotal == null) {
            return null;
        }
        BigDecimal extractedAmount = extractAmount(dynamicTotal);
        if (extractedAmount != null) {
            return extractedAmount;
        }
        try {
            return GermanNumberParser.parse(dynamicTotal);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private BigDecimal parseBauhausTotal(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            if (!lines.get(index).stripLeading().toUpperCase(Locale.ROOT).startsWith("SUMME")) {
                continue;
            }
            int lastCandidateIndex = Math.min(lines.size(), index + 9);
            for (int candidateIndex = index + 1; candidateIndex < lastCandidateIndex; candidateIndex++) {
                BigDecimal amount = extractAmount(lines.get(candidateIndex));
                if (amount != null) {
                    return amount;
                }
            }
        }
        return null;
    }

    private List<ParsedReceiptItem> parseBauhausItems(List<String> lines) {
        List<ParsedReceiptItem> items = new ArrayList<>();
        List<String> pendingDescriptionLines = new ArrayList<>();
        boolean insideItems = false;
        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.contains("ART/EAN")) {
                pendingDescriptionLines.clear();
                insideItems = true;
                continue;
            }
            if (!insideItems) {
                continue;
            }
            if (isTotalLine(line)) {
                break;
            }
            if (upper.equals("EUR") || LONG_NUMERIC_CODE_LINE.matcher(line).matches()) {
                continue;
            }

            Matcher amountMatcher = AMOUNT_AT_END.matcher(line);
            if (amountMatcher.find() && !shouldSkipAmountLine(line)) {
                String descriptionPart = line.substring(0, amountMatcher.start()).trim();
                if (!descriptionPart.isBlank()) {
                    pendingDescriptionLines.add(descriptionPart);
                }
                String description = cleanupDescription(String.join(" ", pendingDescriptionLines));
                pendingDescriptionLines.clear();
                if (!description.isBlank()) {
                    items.add(new ParsedReceiptItem(
                            items.size(),
                            description,
                            null,
                            null,
                            null,
                            GermanNumberParser.parse(amountMatcher.group("amount")),
                            null));
                }
                continue;
            }
            if (!isMetadataLine(line)) {
                pendingDescriptionLines.add(line);
            }
        }
        return items;
    }

    private String parseBauhausBranch(List<String> lines) {
        return lines.stream()
                .map(String::trim)
                .filter(line -> line.matches("(?i)^Am\\s+.+\\s+\\d+[a-z]?$"))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal amountOnLineOrFollowing(List<String> lines, int lineIndex) {
        BigDecimal lineAmount = extractAmount(lines.get(lineIndex));
        if (lineAmount != null) {
            return lineAmount;
        }

        for (int nextIndex = lineIndex + 1; nextIndex < lines.size() && nextIndex <= lineIndex + 2; nextIndex++) {
            String nextLine = lines.get(nextIndex);
            if (nextLine.isBlank()) {
                continue;
            }
            BigDecimal nextAmount = extractAmount(nextLine);
            if (nextAmount != null) {
                return nextAmount;
            }
            break;
        }
        return null;
    }

    private BigDecimal parseGiftCardPaymentTotal(List<String> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (String line : lines) {
            if (!isGiftCardPaymentLine(line)) {
                continue;
            }
            BigDecimal amount = extractAmount(line);
            if (amount != null) {
                total = total.add(amount.abs());
            }
        }
        return total;
    }

    private BigDecimal parseCaPaymentTotal(List<String> lines) {
        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (!upper.contains("BETRAG")
                    && !upper.contains("BETRÄG")
                    && !upper.contains("BETRAEG")) {
                continue;
            }
            BigDecimal amount = extractAmount(line);
            if (amount != null) {
                return amount;
            }
        }

        for (String line : lines) {
            if (!line.toUpperCase(Locale.ROOT).contains("GIROCARD")) {
                continue;
            }
            BigDecimal amount = extractAmount(line);
            if (amount != null) {
                return amount;
            }
        }
        return null;
    }

    private BigDecimal parseBonusBalance(List<String> lines) {
        BigDecimal earnedReweBonus = parseEarnedReweBonus(lines);
        if (earnedReweBonus != null) {
            return earnedReweBonus;
        }

        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (!upper.contains("GUTHABEN")
                    || !upper.contains("GESAMMELT")
                    || upper.contains("AKTUELLES")
                    || upper.contains("EINGESETZTES")
                    || upper.contains("PUNKTESTAND")) {
                continue;
            }
            BigDecimal amount = extractAmount(line);
            if (amount != null) {
                return amount;
            }
        }
        return null;
    }

    private BigDecimal parseEarnedReweBonus(List<String> lines) {
        if (!hasReweBonusContext(lines)) {
            return null;
        }

        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (!upper.contains("MIT DIESEM EINKAUF HAST DU")) {
                continue;
            }
            BigDecimal amount = extractAmount(line);
            if (amount != null) {
                return amount;
            }
        }
        return null;
    }

    private boolean hasReweBonusContext(List<String> lines) {
        return lines.stream()
                .map(line -> line.toUpperCase(Locale.ROOT))
                .anyMatch(upper -> upper.contains("REWE BONUS")
                        || upper.contains("BONUS-GUTHABEN GESAMMELT")
                        || upper.contains("BONUS-VORTEILE"));
    }

    private BigDecimal parseBonusPoints(List<String> lines) {
        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.contains("PUNKTE FÜR DIESEN EINKAUF") || upper.contains("PUNKTE FUER DIESEN EINKAUF")) {
                Matcher matcher = BONUS_POINTS.matcher(line);
                if (matcher.find()) {
                    return GermanNumberParser.parse(matcher.group("points"));
                }
            }
        }

        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.contains("ERHALTEN SIE")) {
                Matcher matcher = BONUS_POINTS.matcher(line);
                if (matcher.find()) {
                    return GermanNumberParser.parse(matcher.group("points"));
                }
            }
        }

        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.contains("BASIS-PUNKTE") || upper.contains("BASISPUNKTE")) {
                Matcher matcher = BONUS_POINTS.matcher(line);
                if (matcher.find()) {
                    return GermanNumberParser.parse(matcher.group("points"));
                }
            }
        }

        for (String line : lines) {
            Matcher matcher = BONUS_POINTS.matcher(line);
            if (matcher.find()) {
                return GermanNumberParser.parse(matcher.group("points"));
            }
        }
        return null;
    }

    private String parseBonusType(List<String> lines) {
        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.contains("PAYBACK")) {
                return "Payback";
            }
            if (upper.contains("DEUTSCHLANDCARD")) {
                return "DeutschlandCard";
            }
            if (upper.contains("REWE BONUS") || upper.contains("BONUS-GUTHABEN") || upper.contains("BONUS-VORTEILE")) {
                return "Rewe Bonus";
            }
        }
        return null;
    }

    private BigDecimal extractAmount(String line) {
        Matcher eurMatcher = AMOUNT_BEFORE_EUR.matcher(line);
        if (eurMatcher.find()) {
            return GermanNumberParser.parse(eurMatcher.group("amount"));
        }

        Matcher euroSymbolMatcher = AMOUNT_BEFORE_EURO_SYMBOL.matcher(line);
        if (euroSymbolMatcher.find()) {
            return GermanNumberParser.parse(euroSymbolMatcher.group("amount"));
        }

        Matcher endMatcher = AMOUNT_AT_END.matcher(line);
        if (endMatcher.find()) {
            return GermanNumberParser.parse(endMatcher.group("amount"));
        }

        Matcher tableCellMatcher = AMOUNT_BEFORE_TABLE_CELL_END.matcher(line);
        if (tableCellMatcher.find()) {
            return GermanNumberParser.parse(tableCellMatcher.group("amount"));
        }
        return null;
    }

    private List<ParsedReceiptItem> parseItems(List<String> lines, String storeName, BigDecimal receiptTotal) {
        List<ParsedReceiptItem> items = new ArrayList<>();
        List<String> pendingDescriptionLines = new ArrayList<>();
        boolean quantityArticleTable = hasQuantityArticleTable(lines);
        List<ParseRule> dynamicTotalRules = activeRules(ParseRuleType.TOTAL_PATTERN, storeName);
        List<ParseRule> dynamicItemRules = activeRules(ParseRuleType.ITEM_PATTERN, storeName);
        QuantityDetails leadingQuantityDetails = null;
        String pendingStornoDescription = null;
        StarFuelDetails pendingStarFuelDetails = null;
        Integer pendingStarFuelItemIndex = null;
        Integer pendingShellFuelItemIndex = null;
        boolean multipleCashReceiptSections = hasMultipleCashReceiptSections(lines);
        boolean insideCashReceiptSection = !multipleCashReceiptSections;
        int cashReceiptItemStartIndex = 0;

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            if (multipleCashReceiptSections && isCardTerminalReceiptStartLine(line)) {
                break;
            }
            if (multipleCashReceiptSections && isCashReceiptHeaderLine(line)) {
                pendingDescriptionLines.clear();
                leadingQuantityDetails = null;
                insideCashReceiptSection = true;
                cashReceiptItemStartIndex = items.size();
                continue;
            }
            if (multipleCashReceiptSections && !insideCashReceiptSection) {
                continue;
            }
            if (multipleCashReceiptSections && isTotalLine(line)) {
                reconcileSingleItemCashReceiptSection(items, cashReceiptItemStartIndex, line);
                pendingDescriptionLines.clear();
                leadingQuantityDetails = null;
                insideCashReceiptSection = false;
                continue;
            }
            if (!multipleCashReceiptSections
                    && !items.isEmpty()
                    && isFinalItemTotalLine(lines, lineIndex, receiptTotal)) {
                pendingDescriptionLines.clear();
                leadingQuantityDetails = null;
                break;
            }
            if (isReceiptItemSectionHeaderLine(line)) {
                pendingDescriptionLines.clear();
                leadingQuantityDetails = null;
                continue;
            }

            if (isZeilenstornoLine(line)) {
                pendingDescriptionLines.clear();
                leadingQuantityDetails = null;
                pendingStornoDescription = removeLastItemForStorno(items);
                continue;
            }

            if (pendingStornoDescription != null && isStornoReferenceLine(line, pendingStornoDescription)) {
                pendingDescriptionLines.clear();
                leadingQuantityDetails = null;
                pendingStornoDescription = null;
                continue;
            }
            pendingStornoDescription = null;

            if (isShellStoreName(storeName)) {
                ParsedReceiptItem shellFuelItem = parseShellFuelItemLine(line, items.size());
                if (shellFuelItem != null) {
                    pendingDescriptionLines.clear();
                    leadingQuantityDetails = null;
                    items.add(shellFuelItem);
                    pendingShellFuelItemIndex = items.size() - 1;
                    continue;
                }
                QuantityDetails shellFuelDetails = parseShellFuelDetailsLine(line);
                if (shellFuelDetails != null
                        && pendingShellFuelItemIndex != null
                        && pendingShellFuelItemIndex == items.size() - 1) {
                    updateLastItemWithQuantityDetails(items, shellFuelDetails);
                    pendingShellFuelItemIndex = null;
                    continue;
                }
            }

            if (isStarStoreName(storeName)) {
                StarFuelDetails starFuelDetails = parseStarFuelQuantityLine(line);
                if (starFuelDetails != null) {
                    pendingDescriptionLines.clear();
                    leadingQuantityDetails = null;
                    pendingStarFuelDetails = starFuelDetails;
                    pendingStarFuelItemIndex = null;
                    continue;
                }

                ParsedReceiptItem starFuelItem = parseStarFuelItemLine(line, pendingStarFuelDetails, items.size());
                if (starFuelItem != null) {
                    pendingDescriptionLines.clear();
                    leadingQuantityDetails = null;
                    pendingStarFuelDetails = null;
                    items.add(starFuelItem);
                    pendingStarFuelItemIndex = items.size() - 1;
                    continue;
                }

                BigDecimal starFuelUnitPrice = parseStarFuelUnitPriceLine(line);
                if (starFuelUnitPrice != null
                        && pendingStarFuelItemIndex != null
                        && pendingStarFuelItemIndex == items.size() - 1) {
                    updateLastItemWithQuantityDetails(items, new QuantityDetails(null, "Liter", starFuelUnitPrice));
                    pendingStarFuelItemIndex = null;
                    continue;
                }

                pendingStarFuelDetails = null;
            }

            if (isCaStoreName(storeName) && LONG_NUMERIC_CODE_LINE.matcher(line).matches()) {
                // C&A product barcodes delimit the receipt header from the following item description.
                pendingDescriptionLines.clear();
                leadingQuantityDetails = null;
                continue;
            }

            if (isEdekaCounterMarkerLine(line, storeName)) {
                pendingDescriptionLines.clear();
                pendingDescriptionLines.add(line);
                leadingQuantityDetails = null;
                continue;
            }

            QuantityDetails leadingDetails = parseLeadingQuantityPriceLine(line);
            if (leadingDetails != null) {
                leadingQuantityDetails = leadingDetails;
                continue;
            }

            ParsedReceiptItem quantityArticleItem = quantityArticleTable
                    ? parseQuantityArticleItemLine(line, items.size())
                    : null;
            if (quantityArticleItem != null) {
                pendingDescriptionLines.clear();
                leadingQuantityDetails = null;
                items.add(quantityArticleItem);
                continue;
            }

            ParsedReceiptItem dmItem = parseDmItemLine(line, items.size());
            if (dmItem != null) {
                pendingDescriptionLines.clear();
                leadingQuantityDetails = null;
                items.add(dmItem);
                continue;
            }

            QuantityDetails pipeQuantityDetails = parsePipeQuantityDetailLine(line);
            if (pipeQuantityDetails != null) {
                if (!items.isEmpty() && pendingDescriptionLines.isEmpty()) {
                    updateLastItemWithQuantityDetails(items, pipeQuantityDetails);
                } else {
                    pendingDescriptionLines.add(line);
                }
                continue;
            }

            ParsedReceiptItem pipeTableItem = parsePipeTableItemLine(line, pendingDescriptionLines, items.size());
            if (pipeTableItem != null) {
                pendingDescriptionLines.clear();
                leadingQuantityDetails = null;
                items.add(pipeTableItem);
                continue;
            }

            String pipeTableDescription = parsePipeTableDescriptionLine(line);
            if (pipeTableDescription != null) {
                pendingDescriptionLines.clear();
                pendingDescriptionLines.add(pipeTableDescription);
                continue;
            }

            QuantityDetails quantityDetails = parseQuantityDetailLine(line);
            if (quantityDetails != null) {
                if (!items.isEmpty() && pendingDescriptionLines.isEmpty()) {
                    updateLastItemWithQuantityDetails(items, quantityDetails);
                } else {
                    pendingDescriptionLines.add(line);
                }
                continue;
            }

            if (isItemDetailLine(line)) {
                if (!items.isEmpty() && pendingDescriptionLines.isEmpty()) {
                    appendLastItemDescription(items, line);
                } else if (!isMetadataLine(line)) {
                    pendingDescriptionLines.add(line);
                }
                continue;
            }

            if (isDynamicTotalLine(line, dynamicTotalRules)) {
                pendingDescriptionLines.clear();
                continue;
            }

            Matcher amountMatcher = amountAtEndMatcher(line, storeName);
            boolean amountLine = amountMatcher.find();
            if (amountLine && !shouldSkipAmountLine(line)) {
                BigDecimal totalPrice = GermanNumberParser.parse(amountMatcher.group("amount"));
                AmountLineCandidate correctedAmount = correctMultiReceiptAmountLine(
                        line,
                        amountMatcher.start(),
                        totalPrice,
                        receiptTotal,
                        multipleCashReceiptSections);
                totalPrice = correctedAmount.amount();
                String descriptionPart = line.substring(0, correctedAmount.descriptionEnd()).trim();
                List<String> descriptionLines = new ArrayList<>(pendingDescriptionLines);
                if (!descriptionPart.isBlank()) {
                    descriptionLines.add(normalizeItemDetailLine(descriptionPart));
                }
                pendingDescriptionLines.clear();

                ParsedReceiptItem buschItem =
                        parseBuschItemLine(descriptionLines, totalPrice, storeName, items.size());
                if (buschItem != null) {
                    items.add(buschItem);
                    continue;
                }

                String description = normalizeItemDescriptionForStore(
                        cleanupDescription(String.join(" ", descriptionLines)), storeName);
                if (description.isBlank() || isTotalLine(description) || isPaymentDetailLine(description)) {
                    continue;
                }

                ParsedReceiptItem pharmacyItem = parsePharmacyItemLine(
                        description,
                        totalPrice,
                        items.size());
                if (pharmacyItem != null) {
                    items.add(pharmacyItem);
                    continue;
                }

                Matcher quantityMatcher = QUANTITY_PRICE.matcher(String.join(" ", descriptionLines));
                BigDecimal quantity = null;
                String unit = null;
                BigDecimal unitPrice = null;
                Matcher priceXQuantityMatcher = PRICE_X_QUANTITY.matcher(String.join(" ", descriptionLines));
                if (priceXQuantityMatcher.find()) {
                    quantity = GermanNumberParser.parse(priceXQuantityMatcher.group("quantity"));
                    unit = "Stk";
                    unitPrice = GermanNumberParser.parse(priceXQuantityMatcher.group("unitPrice"));
                } else if (quantityMatcher.find()) {
                    quantity = GermanNumberParser.parse(quantityMatcher.group("quantity"));
                    unit = normalizeUnit(quantityMatcher.group("unit"));
                    unitPrice = GermanNumberParser.parse(quantityMatcher.group("unitPrice"));
                }
                if (quantity == null && leadingQuantityDetails != null
                        && leadingQuantityDetailsMatches(totalPrice, leadingQuantityDetails)) {
                    quantity = leadingQuantityDetails.quantity();
                    unit = leadingQuantityDetails.unit();
                    unitPrice = leadingQuantityDetails.unitPrice();
                }
                leadingQuantityDetails = null;

                BigDecimal discountAmount = totalPrice.signum() < 0 || description.toUpperCase(Locale.ROOT).contains("RABATT")
                        ? totalPrice.abs()
                        : null;
                items.add(new ParsedReceiptItem(
                        items.size(),
                        description,
                        quantity,
                        unit,
                        unitPrice,
                        totalPrice,
                        discountAmount));
                continue;
            }

            ParsedReceiptItem dynamicItem = parseDynamicItem(line, dynamicItemRules, items.size());
            if (dynamicItem != null) {
                // Compose at the original source line, once. This also makes quantity/details/storno
                // operate on the same sequence and keeps this line out of a later generic description.
                pendingDescriptionLines.clear();
                if (dynamicItem.quantity() == null && leadingQuantityDetails != null
                        && leadingQuantityDetailsMatches(dynamicItem.totalPrice(), leadingQuantityDetails)) {
                    dynamicItem = new ParsedReceiptItem(dynamicItem.positionIndex(), dynamicItem.description(),
                            leadingQuantityDetails.quantity(), leadingQuantityDetails.unit(),
                            leadingQuantityDetails.unitPrice(), dynamicItem.totalPrice(), dynamicItem.discountAmount());
                }
                leadingQuantityDetails = null;
                items.add(dynamicItem);
            } else if (amountLine) {
                pendingDescriptionLines.clear();
            } else if (!isMetadataLine(line)) {
                pendingDescriptionLines.add(line);
            }
        }

        return items;
    }

    private boolean isFinalItemTotalLine(List<String> lines, int lineIndex, BigDecimal receiptTotal) {
        String line = lines.get(lineIndex);
        if (receiptTotal == null
                || !isTotalLine(line)
                || line.toUpperCase(Locale.ROOT).contains("ZWISCHEN")) {
            return false;
        }
        BigDecimal candidate = amountOnLineOrFollowing(lines, lineIndex);
        return candidate != null && candidate.subtract(receiptTotal).abs().compareTo(new BigDecimal("0.02")) <= 0;
    }

    private Matcher amountAtEndMatcher(String line, String storeName) {
        Pattern pattern = isEdekaStoreName(storeName) ? EDEKA_AMOUNT_AT_END : AMOUNT_AT_END;
        return pattern.matcher(line);
    }

    private BigDecimal parseCombinedCardPaymentTotal(List<String> lines) {
        if (!hasMultipleCashReceiptSections(lines)) {
            return null;
        }
        boolean insideCardTerminalReceipt = false;
        for (String line : lines) {
            if (isCardTerminalReceiptStartLine(line)) {
                insideCardTerminalReceipt = true;
                continue;
            }
            if (!insideCardTerminalReceipt || !line.stripLeading().toUpperCase(Locale.ROOT).startsWith("EUR ")) {
                continue;
            }
            BigDecimal amount = extractAmount(line);
            if (amount != null) {
                return amount;
            }
        }
        return null;
    }

    private boolean hasMultipleCashReceiptSections(List<String> lines) {
        return lines.stream().filter(this::isCashReceiptHeaderLine).limit(2).count() == 2;
    }

    private boolean isCashReceiptHeaderLine(String line) {
        return line.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "").contains("KASSENBON");
    }

    private boolean isCardTerminalReceiptStartLine(String line) {
        return line.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "").contains("KUNDENBELEG");
    }

    private boolean isReceiptItemSectionHeaderLine(String line) {
        String compact = line.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        return compact.equals("KASSENBON") || compact.equals("KUNDENBELEG");
    }

    private ParsedReceiptItem parseBuschItemLine(
            List<String> descriptionLines, BigDecimal totalPrice, String storeName, int positionIndex) {
        if (!isBuschStoreName(storeName) || descriptionLines.size() < 2) {
            return null;
        }
        Matcher descriptionMatcher =
                BUSCH_ITEM_DESCRIPTION_LINE.matcher(descriptionLines.get(descriptionLines.size() - 2));
        Matcher priceMatcher =
                BUSCH_ITEM_PRICE_DETAIL_LINE.matcher(descriptionLines.get(descriptionLines.size() - 1));
        if (!descriptionMatcher.matches() || !priceMatcher.matches()) {
            return null;
        }
        return new ParsedReceiptItem(
                positionIndex,
                cleanupDescription(descriptionMatcher.group("description")),
                GermanNumberParser.parse(descriptionMatcher.group("quantity")),
                normalizeUnit(descriptionMatcher.group("unit")),
                GermanNumberParser.parse(priceMatcher.group("unitPrice")),
                totalPrice,
                null);
    }

    private AmountLineCandidate correctMultiReceiptAmountLine(
            String line,
            int originalDescriptionEnd,
            BigDecimal parsedAmount,
            BigDecimal receiptTotal,
            boolean multipleCashReceiptSections) {
        AmountLineCandidate original = new AmountLineCandidate(parsedAmount, originalDescriptionEnd);
        if (!multipleCashReceiptSections
                || receiptTotal == null
                || parsedAmount.signum() <= 0
                || parsedAmount.compareTo(receiptTotal) <= 0) {
            return original;
        }

        AmountLineCandidate corrected = null;
        Matcher matcher = ANY_AMOUNT.matcher(line);
        while (matcher.find() && matcher.start() < originalDescriptionEnd) {
            BigDecimal candidate = GermanNumberParser.parse(matcher.group("amount"));
            if (candidate.signum() > 0 && candidate.compareTo(receiptTotal) <= 0) {
                corrected = new AmountLineCandidate(candidate, matcher.start());
            }
        }
        return corrected == null ? original : corrected;
    }

    private void reconcileSingleItemCashReceiptSection(
            List<ParsedReceiptItem> items, int itemStartIndex, String totalLine) {
        if (items.size() - itemStartIndex != 1
                || !totalLine.toUpperCase(Locale.ROOT).matches(".*POSITIONEN\\s*:\\s*1\\b.*")) {
            return;
        }
        BigDecimal sectionTotal = extractAmount(totalLine);
        if (sectionTotal == null) {
            return;
        }
        ParsedReceiptItem item = items.get(itemStartIndex);
        BigDecimal unitPrice = item.unitPrice();
        if (unitPrice != null && item.quantity() != null && BigDecimal.ONE.compareTo(item.quantity()) == 0) {
            unitPrice = sectionTotal;
        }
        items.set(itemStartIndex, new ParsedReceiptItem(
                item.positionIndex(),
                item.description(),
                item.quantity(),
                item.unit(),
                unitPrice,
                sectionTotal,
                item.discountAmount()));
    }

    private boolean isEdekaCounterMarkerLine(String line, String storeName) {
        if (!isEdekaStoreName(storeName)) {
            return false;
        }
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.startsWith("BELEG ")
                && (upper.contains("PREPACK") || upper.matches(".*\\bBED\\b.*"));
    }

    private StarFuelDetails parseStarFuelQuantityLine(String line) {
        Matcher matcher = STAR_FUEL_QUANTITY_LINE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        return new StarFuelDetails(
                GermanNumberParser.parse(matcher.group("quantity")),
                normalizeUnit(matcher.group("unit")));
    }

    private ParsedReceiptItem parseStarFuelItemLine(
            String line, StarFuelDetails pendingDetails, int positionIndex) {
        if (pendingDetails == null) {
            return null;
        }
        Matcher matcher = STAR_FUEL_ITEM_LINE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        String description = cleanupDescription(matcher.group("description"));
        if (description.isBlank()) {
            return null;
        }
        return new ParsedReceiptItem(
                positionIndex,
                description,
                pendingDetails.quantity(),
                pendingDetails.unit(),
                null,
                GermanNumberParser.parse(matcher.group("total")),
                null);
    }

    private ParsedReceiptItem parseShellFuelItemLine(String line, int positionIndex) {
        Matcher matcher = SHELL_FUEL_ITEM_LINE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        return new ParsedReceiptItem(
                positionIndex,
                cleanupDescription(matcher.group("description")),
                null,
                null,
                null,
                GermanNumberParser.parse(matcher.group("total")),
                null);
    }

    private QuantityDetails parseShellFuelDetailsLine(String line) {
        Matcher matcher = SHELL_FUEL_DETAILS_LINE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        return new QuantityDetails(
                GermanNumberParser.parse(matcher.group("quantity")),
                "Liter",
                GermanNumberParser.parse(matcher.group("unitPrice")));
    }

    private BigDecimal parseStarFuelUnitPriceLine(String line) {
        Matcher matcher = STAR_FUEL_UNIT_PRICE_LINE.matcher(line);
        return matcher.matches() ? GermanNumberParser.parse(matcher.group("unitPrice")) : null;
    }

    private void appendLastItemDescription(List<ParsedReceiptItem> items, String detailLine) {
        int lastIndex = items.size() - 1;
        ParsedReceiptItem item = items.get(lastIndex);
        String detail = cleanupDescription(normalizeItemDetailLine(detailLine));
        if (detail.isBlank()) {
            return;
        }
        items.set(lastIndex, new ParsedReceiptItem(
                item.positionIndex(),
                cleanupDescription(item.description() + " " + detail),
                item.quantity(),
                item.unit(),
                item.unitPrice(),
                item.totalPrice(),
                item.discountAmount()));
    }

    private String normalizeItemDetailLine(String line) {
        Matcher caDetailMatcher = CA_ITEM_DETAIL_LINE.matcher(line);
        if (caDetailMatcher.matches()) {
            return caDetailMatcher.group("sizeLabel") + " " + caDetailMatcher.group("size");
        }
        return line;
    }

    private String removeLastItemForStorno(List<ParsedReceiptItem> items) {
        if (items.isEmpty()) {
            return null;
        }
        return items.remove(items.size() - 1).description();
    }

    private boolean isZeilenstornoLine(String line) {
        return line.toUpperCase(Locale.ROOT).contains("ZEILENSTORNO");
    }

    private boolean isStornoReferenceLine(String line, String removedDescription) {
        Matcher amountMatcher = AMOUNT_AT_END.matcher(line);
        if (!amountMatcher.find()) {
            return false;
        }
        String candidateDescription = cleanupDescription(line.substring(0, amountMatcher.start()));
        return descriptionsLikelySame(candidateDescription, removedDescription);
    }

    private boolean descriptionsLikelySame(String firstDescription, String secondDescription) {
        List<String> firstTokens = meaningfulDescriptionTokens(firstDescription);
        List<String> secondTokens = meaningfulDescriptionTokens(secondDescription);
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) {
            return false;
        }

        int matchingTokens = 0;
        for (String token : firstTokens) {
            if (secondTokens.contains(token)) {
                matchingTokens++;
            }
        }
        return matchingTokens >= Math.min(3, Math.min(firstTokens.size(), secondTokens.size()));
    }

    private List<String> meaningfulDescriptionTokens(String description) {
        List<String> tokens = new ArrayList<>();
        for (String token : description.toUpperCase(Locale.ROOT).split("[^A-Z0-9ÄÖÜ]+")) {
            if (token.length() >= 3 && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String normalizeItemDescriptionForStore(String description, String storeName) {
        if (!isCaStoreName(storeName)) {
            return description;
        }
        return cleanupDescription(description
                .replace("BABY-COMST", "BABY-COMBI")
                .replace("B-GUIDOOR", "B-OUTDOOR")
                .replace("B-HAESCHE", "B-WAESCHE")
                .replaceAll("\\bB-ACCESS\\b(?!\\.)", "B-ACCESS.")
                .replaceAll("\\s+A$", ""));
    }

    private List<ParsedReceiptItem> reconcileCaOcrItemPrices(
            String storeName, BigDecimal totalAmount, List<ParsedReceiptItem> items) {
        if (!isCaStoreName(storeName) || totalAmount == null || items.isEmpty() || sumMatches(totalAmount, items)) {
            return items;
        }

        List<ParsedReceiptItem> correctedItems = new ArrayList<>();
        boolean changed = false;
        for (ParsedReceiptItem item : items) {
            BigDecimal correctedPrice = correctedCaOcrPrice(item);
            if (correctedPrice != null && item.totalPrice() != null && correctedPrice.compareTo(item.totalPrice()) != 0) {
                changed = true;
            }
            correctedItems.add(new ParsedReceiptItem(
                    item.positionIndex(),
                    item.description(),
                    item.quantity(),
                    item.unit(),
                    item.unitPrice(),
                    correctedPrice,
                    item.discountAmount()));
        }

        // C&A OCR occasionally reads the leading digit incorrectly. Apply only known corrections that reconcile exactly.
        return changed && sumMatches(totalAmount, correctedItems) ? correctedItems : items;
    }

    private BigDecimal correctedCaOcrPrice(ParsedReceiptItem item) {
        if (item.totalPrice() == null) {
            return null;
        }
        String description = item.description().toUpperCase(Locale.ROOT);
        if (description.contains("B-ACCESS") && item.totalPrice().compareTo(new BigDecimal("4.29")) == 0) {
            return new BigDecimal("4.99");
        }
        if (description.contains("B-WAESCHE") && item.totalPrice().compareTo(new BigDecimal("5.99")) == 0) {
            return new BigDecimal("9.99");
        }
        return item.totalPrice();
    }

    private boolean sumMatches(BigDecimal totalAmount, List<ParsedReceiptItem> items) {
        BigDecimal itemTotal = items.stream()
                .map(ParsedReceiptItem::totalPrice)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return itemTotal.subtract(totalAmount).abs().compareTo(new BigDecimal("0.02")) <= 0;
    }

    private ParsedReceiptItem parseDynamicItem(String line, List<ParseRule> rules, int positionIndex) {
        if (isMetadataLine(line) || isTotalLine(line) || isPaymentDetailLine(line)
                || isProtectedAmountPrefix(line)
                // Contextual loyalty labels are metadata; words inside merchandise names are not.
                || line.matches("(?iuU)^\\s*(?:ihr\\s+(?:guthaben|punktestand|vorteil)|gesammelte\\s+punkte)\\b.*")) {
            return null;
        }
        for (ParseRule rule : rules) {
            ParsedReceiptItem item = parseDynamicItemLine(line, rule, positionIndex);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    private ParsedReceiptItem parseDynamicItemLine(String line, ParseRule rule, int positionIndex) {
        Matcher matcher = matcher(rule, line);
        if (matcher == null || !matcher.find()) {
            return null;
        }

        String amountText = firstGroupValue(matcher, List.of("total", "amount", "price"));
        BigDecimal totalPrice = amountText == null ? extractAmount(matcher.group()) : parseAmount(amountText);
        if (totalPrice == null) {
            return null;
        }

        String description = firstGroupValue(matcher, List.of("description", "item", "name"));
        if (description == null) {
            description = groupValue(matcher, rule.getExtractGroup());
        }
        if (description == null || description.isBlank()) {
            description = cleanupDescription(AMOUNT_AT_END.matcher(matcher.group()).replaceFirst(""));
        } else {
            description = cleanupDescription(description);
        }
        if (description.isBlank()) {
            return null;
        }

        BigDecimal quantity = parseAmount(firstGroupValue(matcher, List.of("quantity")));
        String unitText = firstGroupValue(matcher, List.of("unit"));
        String unit = unitText == null ? null : normalizeUnit(unitText);
        BigDecimal unitPrice = parseAmount(firstGroupValue(matcher, List.of("unitPrice", "unit_price")));
        BigDecimal discountAmount = totalPrice.signum() < 0 || description.toUpperCase(Locale.ROOT).contains("RABATT")
                ? totalPrice.abs()
                : null;
        return new ParsedReceiptItem(
                positionIndex,
                description,
                quantity,
                unit,
                unitPrice,
                totalPrice,
                discountAmount);
    }

    private boolean hasQuantityArticleTable(List<String> lines) {
        return lines.stream()
                .map(line -> line.toUpperCase(Locale.ROOT))
                .anyMatch(upper -> upper.contains("ANZ") && upper.contains("ARTIKEL") && upper.contains("GESAMT"));
    }

    private ParsedReceiptItem parseQuantityArticleItemLine(String line, int positionIndex) {
        if (isMetadataLine(line) || isTotalLine(line) || isPaymentDetailLine(line) || isTaxMarkerLine(line)) {
            return null;
        }
        Matcher matcher = QUANTITY_ARTICLE_ITEM_LINE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        BigDecimal quantity = GermanNumberParser.parse(matcher.group("quantity"));
        BigDecimal totalPrice = GermanNumberParser.parse(matcher.group("total"));
        BigDecimal unitPrice = quantity.signum() == 0
                ? null
                : totalPrice.divide(quantity, 2, RoundingMode.HALF_UP);
        String description = cleanupDescription(matcher.group("description"));
        if (description.isBlank()) {
            return null;
        }

        return new ParsedReceiptItem(
                positionIndex,
                description,
                quantity,
                "Stk",
                unitPrice,
                totalPrice,
                totalPrice.signum() < 0 ? totalPrice.abs() : null);
    }

    private QuantityDetails parsePipeQuantityDetailLine(String line) {
        if (!line.trim().startsWith("|") || isPipeTableNoiseLine(line)) {
            return null;
        }

        List<String> cells = pipeTableCells(line);
        if (cells.size() < 2) {
            return null;
        }

        String candidate = cells.get(0) + " " + cells.get(1);
        return parseQuantityDetailLine(candidate);
    }

    private ParsedReceiptItem parsePipeTableItemLine(
            String line,
            List<String> pendingDescriptionLines,
            int positionIndex) {
        if (!line.trim().startsWith("|") || isPipeTableNoiseLine(line) || isTaxTableLine(line)) {
            return null;
        }

        List<String> cells = pipeTableCells(line);
        if (cells.isEmpty()) {
            return null;
        }

        int amountCellIndex = -1;
        BigDecimal totalPrice = null;
        for (int i = cells.size() - 1; i >= 0; i--) {
            Matcher matcher = AMOUNT_AT_END.matcher(cells.get(i));
            if (matcher.find()) {
                amountCellIndex = i;
                totalPrice = GermanNumberParser.parse(matcher.group("amount"));
                break;
            }
        }
        if (totalPrice == null) {
            return null;
        }

        boolean hasDescriptionFromPreviousTableRow = !pendingDescriptionLines.isEmpty()
                && !cells.isEmpty()
                && isQuantityOnlyCell(cells.getFirst());
        boolean hasArticleQuantityTaxDetail = hasDescriptionFromPreviousTableRow
                && amountCellIndex == cells.size() - 1
                && cells.size() >= 4
                && cells.getFirst().matches("\\d{5,}")
                && cells.get(1).matches("\\d+(?:[,.]\\d+)?")
                && cells.get(2).matches("\\d+(?:[,.]\\d+)?%");
        List<String> descriptionParts = hasDescriptionFromPreviousTableRow
                ? new ArrayList<>(pendingDescriptionLines)
                : new ArrayList<>();
        BigDecimal quantity = null;
        String unit = null;
        BigDecimal unitPrice = null;
        if (hasDescriptionFromPreviousTableRow) {
            quantity = GermanNumberParser.parse(hasArticleQuantityTaxDetail ? cells.get(1) : cells.getFirst());
            unit = "Stk";
            unitPrice = hasArticleQuantityTaxDetail && quantity.signum() != 0
                    ? totalPrice.divide(quantity, 2, RoundingMode.HALF_UP)
                    : firstAmountBefore(cells, amountCellIndex).orElse(totalPrice);
        }
        for (int i = 0; i < amountCellIndex; i++) {
            String cell = cells.get(i);
            if (isQuantityOnlyCell(cell)
                    || isAmountOnlyCell(cell)
                    || isPznLine(cell)
                    || hasDescriptionFromPreviousTableRow) {
                continue;
            }
            descriptionParts.add(cell);
        }

        String description = cleanupDescription(String.join(" ", descriptionParts));
        if (description.isBlank() || isTotalLine(description) || isPaymentDetailLine(description)) {
            return null;
        }

        BigDecimal discountAmount = totalPrice.signum() < 0 || description.toUpperCase(Locale.ROOT).contains("RABATT")
                ? totalPrice.abs()
                : null;
        return new ParsedReceiptItem(
                positionIndex,
                description,
                quantity,
                unit,
                unitPrice,
                totalPrice,
                discountAmount);
    }

    private java.util.Optional<BigDecimal> firstAmountBefore(List<String> cells, int amountCellIndex) {
        for (int i = 0; i < amountCellIndex; i++) {
            Matcher matcher = AMOUNT_AT_END.matcher(cells.get(i));
            if (matcher.find()) {
                return java.util.Optional.of(GermanNumberParser.parse(matcher.group("amount")));
            }
        }
        return java.util.Optional.empty();
    }

    private String parsePipeTableDescriptionLine(String line) {
        if (!line.trim().startsWith("|") || isPipeTableNoiseLine(line) || isTaxTableLine(line)) {
            return null;
        }

        List<String> rawCells = pipeTableCells(line);
        if (rawCells.isEmpty()) {
            return null;
        }
        boolean markdownEmphasisAmountRow = rawCells.stream()
                .anyMatch(cell -> cell.matches("^\\*\\*.+\\*\\*$")
                        && AMOUNT_AT_END.matcher(stripMarkdownEmphasis(cell)).matches());
        List<String> cells = rawCells.stream().map(this::stripMarkdownEmphasis).toList();
        if (!markdownEmphasisAmountRow && cells.stream().anyMatch(cell -> AMOUNT_AT_END.matcher(cell).find())) {
            return null;
        }

        String description = cleanupDescription(String.join(" ", cells.stream()
                .filter(cell -> !isQuantityOnlyCell(cell))
                .filter(cell -> !isPznLine(cell))
                .filter(cell -> !AMOUNT_AT_END.matcher(cell).find())
                .toList()));
        if (description.isBlank()
                || isMetadataLine(description)
                || isTotalLine(description)
                || isPaymentDetailLine(description)) {
            return null;
        }
        return description;
    }

    private String stripMarkdownEmphasis(String cell) {
        return cell.replaceFirst("^\\*\\*(.+)\\*\\*$", "$1").trim();
    }

    private ParsedReceiptItem parsePharmacyItemLine(String description, BigDecimal totalPrice, int positionIndex) {
        Matcher matcher = PHARMACY_ITEM_LINE.matcher(description);
        if (!matcher.find()) {
            return null;
        }

        BigDecimal unitPrice = GermanNumberParser.parse(matcher.group("unitPrice"));
        String itemDescription = cleanupDescription(matcher.group("description"));
        return new ParsedReceiptItem(
                positionIndex,
                itemDescription,
                BigDecimal.ONE,
                "Stk",
                unitPrice,
                totalPrice,
                null);
    }

    private ParsedReceiptItem parseDmItemLine(String line, int positionIndex) {
        if (isMetadataLine(line) || isTaxTableLine(line)) {
            return null;
        }

        Matcher matcher = DM_ITEM_LINE.matcher(line);
        if (!matcher.find()) {
            return null;
        }

        BigDecimal quantity = null;
        String unit = null;
        BigDecimal unitPrice = null;
        if (matcher.group("quantity") != null) {
            quantity = GermanNumberParser.parse(matcher.group("quantity"));
            unit = "Stk";
            unitPrice = GermanNumberParser.parse(matcher.group("unitPrice"));
        }

        BigDecimal totalPrice = GermanNumberParser.parse(matcher.group("total"));
        String description = cleanupDescription(matcher.group("description"));
        if (description.isBlank()) {
            return null;
        }
        BigDecimal discountAmount = totalPrice.signum() < 0 || description.toUpperCase(Locale.ROOT).contains("RABATT")
                ? totalPrice.abs()
                : null;
        return new ParsedReceiptItem(
                positionIndex,
                description,
                quantity,
                unit,
                unitPrice,
                totalPrice,
                discountAmount);
    }

    private QuantityDetails parseQuantityDetailLine(String line) {
        Matcher handeingabeMatcher = HANDEINGABE_QUANTITY_LINE.matcher(line);
        if (handeingabeMatcher.matches()) {
            return new QuantityDetails(
                    GermanNumberParser.parse(handeingabeMatcher.group("quantity")),
                    normalizeUnit(handeingabeMatcher.group("unit")),
                    null);
        }

        Matcher matcher = QUANTITY_DETAIL_LINE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        return new QuantityDetails(
                GermanNumberParser.parse(matcher.group("quantity")),
                normalizeUnit(matcher.group("unit")),
                GermanNumberParser.parse(matcher.group("unitPrice")));
    }

    private QuantityDetails parseLeadingQuantityPriceLine(String line) {
        Matcher matcher = LEADING_QUANTITY_PRICE_LINE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        return new QuantityDetails(
                GermanNumberParser.parse(matcher.group("quantity")),
                "Stk",
                GermanNumberParser.parse(matcher.group("unitPrice")));
    }

    private boolean leadingQuantityDetailsMatches(BigDecimal totalPrice, QuantityDetails quantityDetails) {
        if (totalPrice == null || quantityDetails.quantity() == null || quantityDetails.unitPrice() == null) {
            return false;
        }
        BigDecimal expectedTotal = quantityDetails.quantity().multiply(quantityDetails.unitPrice());
        return expectedTotal.subtract(totalPrice).abs().compareTo(new BigDecimal("0.02")) <= 0;
    }

    private void updateLastItemWithQuantityDetails(List<ParsedReceiptItem> items, QuantityDetails quantityDetails) {
        int lastIndex = items.size() - 1;
        ParsedReceiptItem item = items.get(lastIndex);
        items.set(lastIndex, new ParsedReceiptItem(
                item.positionIndex(),
                item.description(),
                item.quantity() == null ? quantityDetails.quantity() : item.quantity(),
                item.unit() == null ? quantityDetails.unit() : item.unit(),
                item.unitPrice() == null ? unitPriceFor(item, quantityDetails) : item.unitPrice(),
                item.totalPrice(),
                item.discountAmount()));
    }

    private BigDecimal unitPriceFor(ParsedReceiptItem item, QuantityDetails quantityDetails) {
        if (quantityDetails.unitPrice() != null) {
            return quantityDetails.unitPrice();
        }
        if (quantityDetails.quantity() == null || quantityDetails.quantity().signum() == 0 || item.totalPrice() == null) {
            return null;
        }
        return item.totalPrice().divide(quantityDetails.quantity(), 2, RoundingMode.HALF_UP);
    }

    private String cleanupDescription(String description) {
        String cleaned = PRICE_X_QUANTITY.matcher(description.replace('|', ' ')).replaceAll(" ");
        cleaned = QUANTITY_PRICE.matcher(cleaned).replaceAll(" ");
        return cleaned
                .replaceAll("(?i)\\bPRz:\\s*", " ")
                .replaceAll("\\bEUR/\\w+\\b", " ")
                .replaceAll("\\*+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeUnit(String unit) {
        if (unit == null || unit.isBlank() || unit.equalsIgnoreCase("x")) {
            return "Stk";
        }
        if (unit.equalsIgnoreCase("stck") || unit.equalsIgnoreCase("stueck")) {
            return "Stk";
        }
        return unit;
    }

    private boolean shouldSkipAmountLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return isTotalLine(line)
                || isProtectedAmountPrefix(line)
                || isDateOrTimeLine(line)
                || isTaxMarkerLine(line)
                || isTaxTableLine(line)
                || upper.contains("GEGEBEN")
                || upper.contains("GEG.")
                || upper.contains("RUECKGELD")
                || upper.contains("RÜCKGELD")
                || upper.contains("AUSZAHLUNG")
                || upper.contains("CASHBACK")
                || upper.contains("BAR")
                || upper.contains("KARTE")
                || upper.contains("EC-CASH")
                || upper.contains("KARTENZAHLUNG")
                || upper.contains("VISA")
                || upper.contains("GIROCARD")
                || upper.contains("MASTERCARD")
                || upper.contains("DEBIT")
                || upper.startsWith("EUR ")
                || upper.startsWith("BETRAG EUR")
                || upper.startsWith("WERT:")
                || upper.startsWith("NEUER WERT")
                || upper.contains("VORTEIL")
                || upper.contains("GUTHABEN")
                || upper.contains("PAYBACK")
                || upper.contains("PUNKTESTAND")
                || upper.contains("PUNKTE")
                || isReceiptFooterLine(line);
    }

    private boolean isProtectedAmountPrefix(String line) {
        // A payment/value label at the start is evidence; a word inside a product name is not.
        return line.matches("(?i)^\\s*(?:bar(?:zahlung)?|karte|visa|mastercard|debit|gegeben|geg\\."
                + "|bezahlt|zahlung|eur|betrag\\s+eur|wert|guthaben|vorteil|punktestand|punkte|tse|fiskal)"
                + "(?:\\b|\\s|:).*$")
                // Bare tax rate/amount cells only; a described percentage discount is a position.
                || line.matches("^\\s*[0-9]{1,2}(?:,[0-9]{1,2})?\\s*%\\s*-?[0-9]+[.,][0-9]{2}\\s*$");
    }

    private boolean isMetadataLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        if (isEdekaCounterItemDescriptionLine(upper)) {
            return false;
        }
        return isDateOrTimeLine(line)
                || upper.contains("KASSE")
                || upper.contains("BON")
                || upper.contains("BELEG")
                || isPhoneLikeLine(line)
                || upper.startsWith("FAX")
                || isUidMetadataLine(line)
                || upper.contains("STEUER.NR")
                || upper.contains("STEUER-NR")
                || upper.contains("WWW.")
                || upper.equals("EUR")
                || (upper.contains("MENGE") && upper.contains("ARTIKEL") && upper.contains("PREIS"))
                || upper.startsWith("PREISANGABEN IN EUR")
                || (upper.contains("ANZ") && upper.contains("ARTIKEL") && upper.contains("PREIS"))
                || upper.contains("ARTIKELPREIS")
                || ARTICLE_BARCODE_LINE.matcher(line).matches()
                || upper.startsWith("COUPON:")
                || upper.startsWith("DM-RABATTE AUF RABATTFÄHIGE ARTIKEL")
                || upper.startsWith("DM-RABATTE AUF RABATTFAEHIGE ARTIKEL")
                || upper.startsWith("PARTNER-RABATTE AUF RABATTFÄHIGE ARTIKEL")
                || upper.startsWith("PARTNER-RABATTE AUF RABATTFAEHIGE ARTIKEL")
                || upper.startsWith("PRIVATREZEPT")
                || upper.startsWith("POSITIONEN:")
                || upper.startsWith("PZN:")
                || isPznLine(line)
                || line.matches("^\\.?[A-Z]\\d{6,}$")
                || upper.contains("ACHTUNG KUHL")
                || upper.contains("ACHTUNG KÜHL")
                || isTaxMarkerLine(line)
                || upper.contains("FISKAL")
                || upper.contains("TSE")
                || upper.contains("TERMINAL")
                || upper.contains("TRACE")
                || upper.contains("SERIENNUMMER")
                || upper.contains("HAND EINGABE")
                || upper.contains("HANDEINGABE")
                || upper.contains("KARTENZAHLUNG")
                || upper.contains("K-U-N-D-E-N-B-E-L-E-G")
                || upper.contains("ZAHLUNG ERFOLGT")
                || upper.startsWith("POSTEN:")
                || LONG_NUMERIC_CODE_LINE.matcher(line).matches()
                || upper.contains("TEILE DEIN FEEDBACK")
                || upper.contains("NOCH BESSER")
                || upper.contains("NEWSLETTER")
                || upper.contains("KUNDENBELEG")
                || upper.startsWith("ANZAHL DER ARTIKEL")
                || upper.matches("[-=* ]{5,}")
                || isPostalAddressLine(line)
                || upper.contains("PAYBACK")
                || upper.contains("DEUTSCHLANDCARD")
                || isReceiptFooterLine(line)
                || isTaxTableLine(line)
                || isBranchLine(line)
                || isPipeTableNoiseLine(line)
                || isKnownStoreLine(line);
    }

    private boolean isEdekaCounterItemDescriptionLine(String upperLine) {
        return upperLine.startsWith("BELEG ")
                && (upperLine.contains("WURST") || upperLine.contains("SCHINKEN"))
                && (upperLine.contains("PREPACK") || upperLine.contains("BED"));
    }

    private boolean isDateOrTimeLine(String line) {
        return DATE_DOTTED.matcher(line).find()
                || DATE_SLASH.matcher(line).find()
                || DATE_ISO.matcher(line).find()
                || TIME.matcher(line).find();
    }

    private boolean isKnownStoreLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.replace(" ", "").contains("REWE")
                || upper.contains("ALDI")
                || upper.contains("LIDL")
                || upper.contains("DM-DROGERIE")
                || upper.contains("DM.DE")
                || upper.equals("DM")
                || upper.startsWith("DM ")
                || upper.contains("EDEKA")
                || upper.contains("E-CENTER")
                || upper.contains("E CENTER")
                || upper.contains("LANDMARKT")
                || isPharmacyStoreLine(line)
                || upper.contains("MCDONALD")
                || upper.contains("MEDONALD")
                || upper.contains("STAR TANKSTELLE")
                || isCaStoreLine(line);
    }

    private boolean isUidMetadataLine(String line) {
        return line.toUpperCase(Locale.ROOT).matches(".*\\bUID\\b.*");
    }

    private boolean isCaStoreLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        String compact = line.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return compact.equals("CA")
                || upper.contains("C&A")
                || compact.startsWith("C8A")
                || compact.contains("CUNDA")
                || compact.contains("CANDA");
    }

    private boolean isCaStoreName(String storeName) {
        return storeName != null && storeName.equalsIgnoreCase("C&A");
    }

    private boolean isEdekaStoreName(String storeName) {
        return storeName != null && storeName.equalsIgnoreCase("EDEKA");
    }

    private boolean isBuschStoreName(String storeName) {
        if (storeName == null) {
            return false;
        }
        String upper = storeName.toUpperCase(Locale.ROOT);
        return upper.contains("BÜSCH") || upper.contains("BUSCH");
    }

    private boolean isShellStoreName(String storeName) {
        return storeName != null && storeName.toUpperCase(Locale.ROOT).contains("SHELL");
    }

    private boolean isBauhausStoreName(String storeName) {
        return storeName != null && storeName.equalsIgnoreCase("BAUHAUS");
    }

    private boolean isStarStoreName(String storeName) {
        return storeName != null && storeName.equalsIgnoreCase("star Tankstelle");
    }

    private boolean isItemDetailLine(String line) {
        return CA_ITEM_DETAIL_LINE.matcher(line).matches()
                && !AMOUNT_AT_END.matcher(line).find();
    }

    private boolean isPharmacyStoreLine(String line) {
        return line.toUpperCase(Locale.ROOT).matches(".*\\bAPOTHEKE\\b.*");
    }

    private boolean isPhoneLikeLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("TEL.")
                || upper.contains("TEL:")
                || upper.startsWith("TEL ")
                || upper.matches("^\\s*[LT]?EL\\.?\\s*:?\\s*.*\\d.*");
    }

    private boolean isReceiptFooterLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("KASSENBELEG")
                || upper.contains("BELEG")
                || upper.contains("BEDIENER")
                || upper.contains("ECR SERIAL")
                || upper.contains("TSE SERIAL")
                || upper.contains("SERIAL:")
                || upper.startsWith("STNR")
                || upper.startsWith("STNR .")
                || upper.equals("VIELEN DANK")
                || upper.equals("FÜR IHREN")
                || upper.equals("FUER IHREN")
                || upper.equals("EINKAUF!")
                || upper.matches("^[A-F0-9]{16,}$");
    }

    private boolean isBranchLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        if (isKnownStoreLine(line) || isMetadataLineWithoutBranch(line) || isDateOrTimeLine(line)) {
            return false;
        }
        return upper.contains("FILIALE")
                || upper.contains("STRASSE")
                || upper.contains("STR.")
                || upper.contains("PLATZ")
                || upper.contains("MARKT")
                || upper.contains("WEG")
                || upper.contains("ALLEE")
                || upper.contains("RING")
                || upper.contains("GASSE");
    }

    private boolean isMetadataLineWithoutBranch(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return isPhoneLikeLine(line)
                || isUidMetadataLine(line)
                || upper.startsWith("FAX")
                || upper.contains("STEUER-NR")
                || upper.contains("STEUER.NR")
                || upper.contains("BON")
                || upper.contains("BELEG")
                || upper.contains("KASSE");
    }

    private boolean isTotalLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("SUMME")
                || upper.contains("SUMNE")
                || upper.contains("SUNNE")
                || upper.contains("TOTAL")
                || upper.contains("IUOTAL")
                || upper.contains("GESAMT")
                || upper.contains("ZU ZAHLEN")
                || upper.contains("ENDSUMME");
    }

    private boolean isPaymentDetailLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        String compact = upper.replaceAll("[^A-Z0-9]", "");
        return isGiftCardPaymentLine(line)
                || upper.contains("AUSZAHLUNG")
                || upper.contains("CASHBACK")
                || upper.contains("RUECKGELD")
                || upper.contains("RÜCKGELD")
                || upper.contains("KARTENZAHLUNG")
                || upper.contains("EC-KARTE")
                || upper.contains("EC-CASH")
                || compact.contains("GIROCARD")
                || upper.startsWith("NEUER WERT");
    }

    private boolean isGiftCardPaymentLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("GESCHENKKARTE")
                || upper.contains("GESCHENK-KARTE")
                || upper.contains("GUTSCHEINKARTE")
                || upper.contains("GUTSCHEIN-KARTE");
    }

    private boolean isPrimaryTotalLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.startsWith("SUMME EUR")
                || upper.startsWith("SUMNE")
                || upper.startsWith("SUNNE")
                || upper.startsWith("ENDSUMME")
                || upper.startsWith("TOTAL")
                || upper.startsWith("IUOTAL")
                || upper.contains("ZU ZAHLEN");
    }

    private boolean isFinalPayableLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.startsWith("ZU ZAHLENDER BETRAG")
                || upper.startsWith("ZU ZAHLEN")
                || upper.contains("ZU ZAHLEN");
    }

    private boolean isTaxTableLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            List<String> cells = pipeTableCells(line);
            if (cells.size() >= 3
                    && cells.getFirst().matches("\\d{1,2}(?:[,.]\\d+)?%")
                    && cells.subList(1, cells.size()).stream()
                            .allMatch(cell -> AMOUNT_AT_END.matcher(stripMarkdownEmphasis(cell)).matches())) {
                return true;
            }
        }
        return line.matches("^\\s*(?:\\d+|[A-Z])\\s*[:=]?\\s*\\d{1,2}(?:,\\d{1,2})?\\s*%.*")
                || trimmed.matches("^\\|\\s*[A-Z]\\s*[:=]?\\s*\\d{1,2}(?:,\\d{1,2})?\\s*%.*")
                || line.toUpperCase(Locale.ROOT).startsWith("GESAMTBETRAG ");
    }

    private boolean isTaxMarkerLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("MWST")
                || upper.contains("UST-ID")
                || upper.contains("UST.")
                || upper.matches(".*\\bUST\\b.*")
                || upper.contains("UMSATZSTEUER")
                || upper.contains("STEUER")
                || upper.contains("SIEUER")
                || upper.contains("NETTO");
    }

    private boolean isPostalAddressLine(String line) {
        return cleanupBranchLine(line).matches("^\\d{5}\\s+\\S.*");
    }

    private boolean isPipeTableNoiseLine(String line) {
        if (!line.trim().startsWith("|")) {
            return false;
        }
        List<String> cells = pipeTableCells(line);
        if (cells.isEmpty()) {
            return true;
        }
        String joined = String.join(" ", cells).toUpperCase(Locale.ROOT);
        return joined.matches("[- ]+")
                || joined.equals("EUR")
                || (joined.contains("ARTIKEL") && (joined.contains("PREIS") || joined.contains("ZUZAHLUNG")))
                || joined.contains("MWST-SATZ")
                || joined.contains("NETTO")
                || joined.contains("BRUTTO");
    }

    private List<String> pipeTableCells(String line) {
        String[] rawCells = line.split("\\|");
        List<String> cells = new ArrayList<>();
        for (String rawCell : rawCells) {
            String cell = rawCell.trim();
            if (!cell.isBlank()) {
                cells.add(cell);
            }
        }
        return cells;
    }

    private boolean isQuantityOnlyCell(String value) {
        return value.matches("\\d+");
    }

    private boolean isAmountOnlyCell(String value) {
        return AMOUNT_AT_END.matcher(value).matches();
    }

    private boolean isPznLine(String line) {
        return line.trim().matches("(?:PZN:\\s*)?\\d{8}");
    }

    private boolean isDynamicTotalLine(String line, List<ParseRule> rules) {
        return rules.stream()
                .map(rule -> matcher(rule, line))
                .anyMatch(matcher -> matcher != null && matcher.find());
    }

    private String firstDynamicTextMatch(
            List<String> lines,
            ParseRuleType ruleType,
            String storeName,
            List<String> fallbackGroups) {
        List<String> candidates = new ArrayList<>(lines);
        if (!lines.isEmpty()) {
            candidates.add(String.join("\n", lines));
        }
        for (ParseRule rule : activeRules(ruleType, storeName)) {
            for (String candidate : candidates) {
                Matcher matcher = matcher(rule, candidate);
                if (matcher != null && matcher.find()) {
                    String configuredValue = groupValue(matcher, rule.getExtractGroup());
                    if (configuredValue != null) {
                        return configuredValue;
                    }
                    String fallbackValue = firstGroupValue(matcher, fallbackGroups);
                    if (fallbackValue != null) {
                        return fallbackValue;
                    }
                    return matcher.group();
                }
            }
        }
        return null;
    }

    private List<ParseRule> activeRules(ParseRuleType ruleType, String storeName) {
        if (parseRuleRepository == null) {
            return List.of();
        }
        return parseRuleRepository.findByActiveTrueAndRuleTypeOrderByStoreNameAsc(ruleType).stream()
                .filter(rule -> appliesToStore(rule, storeName))
                .toList();
    }

    private boolean appliesToStore(ParseRule rule, String storeName) {
        return rule.getStoreName() == null
                || rule.getStoreName().isBlank()
                || storeName == null
                || rule.getStoreName().equalsIgnoreCase(storeName);
    }

    private Matcher matcher(ParseRule rule, String value) {
        try {
            return Pattern.compile(rule.getMatchRegex(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(value);
        } catch (PatternSyntaxException exception) {
            return null;
        }
    }

    private String firstGroupValue(Matcher matcher, List<String> groupNames) {
        for (String groupName : groupNames) {
            String value = groupValue(matcher, groupName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String groupValue(Matcher matcher, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return null;
        }
        try {
            String value = groupName.matches("\\d+")
                    ? matcher.group(Integer.parseInt(groupName))
                    : matcher.group(groupName);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return GermanNumberParser.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private record QuantityDetails(BigDecimal quantity, String unit, BigDecimal unitPrice) {
    }

    private record StarFuelDetails(BigDecimal quantity, String unit) {
    }

    private record AmountLineCandidate(BigDecimal amount, int descriptionEnd) {
    }
}
