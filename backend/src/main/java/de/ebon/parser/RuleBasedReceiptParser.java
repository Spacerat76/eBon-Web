package de.ebon.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedReceiptParser {

    private static final Pattern AMOUNT_AT_END = Pattern.compile(
            "(?<amount>-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}|-?\\d+,\\d{2})\\s*(?:[A-Z]|§?\\d)?\\s*\\*?$");
    private static final Pattern AMOUNT_BEFORE_EUR = Pattern.compile(
            "(?<amount>-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}|-?\\d+,\\d{2})\\s*EUR\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_DOTTED = Pattern.compile("\\b(?<day>\\d{1,2})\\.(?<month>\\d{1,2})\\.(?<year>\\d{2,4})\\b");
    private static final Pattern DATE_ISO = Pattern.compile("\\b(?<date>\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern TIME = Pattern.compile("\\b(?<hour>\\d{1,2}):(?<minute>\\d{2})(?::(?<second>\\d{2}))?\\b");
    private static final Pattern QUANTITY_PRICE = Pattern.compile(
            "(?<quantity>\\d+(?:,\\d+)?)\\s*(?<unit>kg|g|l|ml|stk|stck|stueck)?\\s*(?:x|\\*)\\s*(?<unitPrice>\\d+(?:,\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY_DETAIL_LINE = Pattern.compile(
            "^\\s*(?<quantity>\\d+(?:,\\d+)?)\\s*(?<unit>kg|g|l|ml|stk|stck|stueck)?\\s*(?:x|\\*)\\s*(?<unitPrice>\\d+(?:,\\d+)?)\\s*(?:EUR(?:/\\w+)?)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BONUS_POINTS = Pattern.compile(
            "(?<points>\\d{1,3}(?:\\.\\d{3})*|\\d+(?:,\\d+)?)\\s*(?:°P|Punkte|Punkt)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DM_ITEM_LINE = Pattern.compile(
            "^(?:(?<quantity>\\d+)x\\s+(?<unitPrice>\\d+(?:,\\d+)?)\\s+)?(?<description>.+?)\\s+(?<total>-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}|-?\\d+,\\d{2})\\s+(?<taxCode>§?\\d)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PHARMACY_ITEM_LINE = Pattern.compile(
            "^(?<description>.+?)\\s+1x\\s+(?<unitPrice>\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+,\\d{2})\\s+(?<total>\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+,\\d{2})\\s+[A-Z]\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final ReceiptParseValidator validator = new ReceiptParseValidator();

    public ReceiptParseResult parse(String rawText) {
        List<String> lines = rawText == null
                ? List.of()
                : rawText.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .toList();

        ParsedReceipt receipt = new ParsedReceipt(
                parseDate(lines),
                parseTime(lines),
                parseStoreName(lines),
                parseStoreBranch(lines),
                parseTotal(lines),
                "EUR",
                parseBonusBalance(lines),
                parseBonusPoints(lines),
                parseBonusType(lines),
                parseItems(lines));

        return validator.validate(receipt);
    }

    private LocalDate parseDate(List<String> lines) {
        for (String line : lines) {
            Matcher isoMatcher = DATE_ISO.matcher(line);
            if (isoMatcher.find()) {
                return LocalDate.parse(isoMatcher.group("date"));
            }

            Matcher dottedMatcher = DATE_DOTTED.matcher(line);
            if (dottedMatcher.find()) {
                return LocalDate.of(
                        normalizeYear(dottedMatcher.group("year")),
                        Integer.parseInt(dottedMatcher.group("month")),
                        Integer.parseInt(dottedMatcher.group("day")));
            }
        }
        return null;
    }

    private int normalizeYear(String year) {
        int parsed = Integer.parseInt(year);
        return parsed < 100 ? 2000 + parsed : parsed;
    }

    private LocalTime parseTime(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = TIME.matcher(line);
            if (matcher.find()) {
                int second = matcher.group("second") == null ? 0 : Integer.parseInt(matcher.group("second"));
                return LocalTime.of(
                        Integer.parseInt(matcher.group("hour")),
                        Integer.parseInt(matcher.group("minute")),
                        second);
            }
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
        }

        return lines.stream()
                .filter(line -> !isDateOrTimeLine(line))
                .findFirst()
                .orElse(null);
    }

    private String parseStoreBranch(List<String> lines) {
        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.contains("FILIALE") || upper.contains("STRASSE") || upper.contains("STR.") || upper.contains("PLATZ")) {
                return line;
            }
        }
        return null;
    }

    private BigDecimal parseTotal(List<String> lines) {
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
            Matcher matcher = AMOUNT_AT_END.matcher(line);
            if (matcher.find()) {
                return GermanNumberParser.parse(matcher.group("amount"));
            }
        }
        return null;
    }

    private BigDecimal amountOnLineOrFollowing(List<String> lines, int lineIndex) {
        Matcher matcher = AMOUNT_AT_END.matcher(lines.get(lineIndex));
        if (matcher.find()) {
            return GermanNumberParser.parse(matcher.group("amount"));
        }

        for (int nextIndex = lineIndex + 1; nextIndex < lines.size() && nextIndex <= lineIndex + 2; nextIndex++) {
            String nextLine = lines.get(nextIndex);
            if (nextLine.isBlank()) {
                continue;
            }
            Matcher nextMatcher = AMOUNT_AT_END.matcher(nextLine);
            if (nextMatcher.find()) {
                return GermanNumberParser.parse(nextMatcher.group("amount"));
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

        Matcher endMatcher = AMOUNT_AT_END.matcher(line);
        if (endMatcher.find()) {
            return GermanNumberParser.parse(endMatcher.group("amount"));
        }
        return null;
    }

    private List<ParsedReceiptItem> parseItems(List<String> lines) {
        List<ParsedReceiptItem> items = new ArrayList<>();
        List<String> pendingDescriptionLines = new ArrayList<>();

        for (String line : lines) {
            ParsedReceiptItem dmItem = parseDmItemLine(line, items.size());
            if (dmItem != null) {
                pendingDescriptionLines.clear();
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
                items.add(pipeTableItem);
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

            Matcher amountMatcher = AMOUNT_AT_END.matcher(line);
            if (amountMatcher.find()) {
                if (shouldSkipAmountLine(line)) {
                    pendingDescriptionLines.clear();
                    continue;
                }

                BigDecimal totalPrice = GermanNumberParser.parse(amountMatcher.group("amount"));
                String descriptionPart = line.substring(0, amountMatcher.start()).trim();
                List<String> descriptionLines = new ArrayList<>(pendingDescriptionLines);
                if (!descriptionPart.isBlank()) {
                    descriptionLines.add(descriptionPart);
                }
                pendingDescriptionLines.clear();

                String description = cleanupDescription(String.join(" ", descriptionLines));
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
                if (quantityMatcher.find()) {
                    quantity = GermanNumberParser.parse(quantityMatcher.group("quantity"));
                    unit = normalizeUnit(quantityMatcher.group("unit"));
                    unitPrice = GermanNumberParser.parse(quantityMatcher.group("unitPrice"));
                }

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

            if (!isMetadataLine(line)) {
                pendingDescriptionLines.add(line);
            }
        }

        return items;
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

        List<String> descriptionParts = new ArrayList<>(pendingDescriptionLines);
        for (int i = 0; i < amountCellIndex; i++) {
            String cell = cells.get(i);
            if (isQuantityOnlyCell(cell) || isAmountOnlyCell(cell) || isPznLine(cell)) {
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
                null,
                null,
                null,
                totalPrice,
                discountAmount);
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
        Matcher matcher = QUANTITY_DETAIL_LINE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        return new QuantityDetails(
                GermanNumberParser.parse(matcher.group("quantity")),
                normalizeUnit(matcher.group("unit")),
                GermanNumberParser.parse(matcher.group("unitPrice")));
    }

    private void updateLastItemWithQuantityDetails(List<ParsedReceiptItem> items, QuantityDetails quantityDetails) {
        int lastIndex = items.size() - 1;
        ParsedReceiptItem item = items.get(lastIndex);
        items.set(lastIndex, new ParsedReceiptItem(
                item.positionIndex(),
                item.description(),
                item.quantity() == null ? quantityDetails.quantity() : item.quantity(),
                item.unit() == null ? quantityDetails.unit() : item.unit(),
                item.unitPrice() == null ? quantityDetails.unitPrice() : item.unitPrice(),
                item.totalPrice(),
                item.discountAmount()));
    }

    private String cleanupDescription(String description) {
        String cleaned = QUANTITY_PRICE.matcher(description.replace('|', ' ')).replaceAll(" ");
        return cleaned
                .replaceAll("\\bEUR/\\w+\\b", " ")
                .replaceAll("\\*+", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("\\s+[*A-Z]$", "")
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
                || upper.contains("GUTHABEN");
    }

    private boolean isMetadataLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return isDateOrTimeLine(line)
                || upper.contains("KASSE")
                || upper.contains("BON")
                || upper.contains("BELEG")
                || upper.contains("TEL.")
                || upper.startsWith("FAX")
                || upper.contains("UID")
                || upper.contains("WWW.")
                || upper.equals("EUR")
                || upper.contains("ARTIKELPREIS")
                || upper.startsWith("PRIVATREZEPT")
                || upper.startsWith("POSITIONEN:")
                || upper.startsWith("PZN:")
                || isPznLine(line)
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
                || upper.matches("[-=* ]{5,}")
                || isPostalAddressLine(line)
                || upper.contains("PAYBACK")
                || upper.contains("DEUTSCHLANDCARD")
                || isTaxTableLine(line)
                || isBranchLine(line)
                || isPipeTableNoiseLine(line)
                || isKnownStoreLine(line);
    }

    private boolean isDateOrTimeLine(String line) {
        return DATE_DOTTED.matcher(line).find() || DATE_ISO.matcher(line).find() || TIME.matcher(line).find();
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
                || upper.contains("EDEKA");
    }

    private boolean isBranchLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("FILIALE")
                || upper.contains("STRASSE")
                || upper.contains("STR.")
                || upper.contains("PLATZ");
    }

    private boolean isTotalLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("SUMME")
                || upper.contains("TOTAL")
                || upper.contains("GESAMT")
                || upper.contains("ZU ZAHLEN")
                || upper.contains("ENDSUMME");
    }

    private boolean isPaymentDetailLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return isGiftCardPaymentLine(line)
                || upper.contains("AUSZAHLUNG")
                || upper.contains("CASHBACK")
                || upper.contains("RUECKGELD")
                || upper.contains("RÜCKGELD")
                || upper.contains("KARTENZAHLUNG")
                || upper.contains("EC-KARTE")
                || upper.contains("EC-CASH")
                || upper.contains("GIROCARD")
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
                || upper.startsWith("ENDSUMME")
                || upper.startsWith("TOTAL")
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
        return line.matches("^\\s*(?:\\d+|[A-Z])\\s*=?\\s*\\d{1,2},\\d{1,2}%.*")
                || trimmed.matches("^\\|\\s*[A-Z]\\s*=?\\s*\\d{1,2},\\d{1,2}%.*")
                || line.toUpperCase(Locale.ROOT).startsWith("GESAMTBETRAG ");
    }

    private boolean isTaxMarkerLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("MWST")
                || upper.contains("UST-ID")
                || upper.contains("UST.")
                || upper.matches(".*\\bUST\\b.*")
                || upper.contains("UMSATZSTEUER");
    }

    private boolean isPostalAddressLine(String line) {
        return line.matches("^\\s*\\d{5}\\s+.*");
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

    private record QuantityDetails(BigDecimal quantity, String unit, BigDecimal unitPrice) {
    }
}
