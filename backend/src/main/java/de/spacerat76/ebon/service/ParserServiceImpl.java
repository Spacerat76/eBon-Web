package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.ParseRule;
import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.domain.ReceiptItem;
import de.spacerat76.ebon.repository.ParseRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

@Service
public class ParserServiceImpl implements ParserService {

    @Autowired(required = false)
    private ParseRuleRepository parseRuleRepository;
    @Autowired(required = false)
    private de.spacerat76.ebon.ai.AiClient aiClient;

    // Setter used by tests that instantiate the service manually
    public void setParseRuleRepository(ParseRuleRepository parseRuleRepository) {
        this.parseRuleRepository = parseRuleRepository;
    }

    // Setter for injecting an AiClient in tests
    public void setAiClient(de.spacerat76.ebon.ai.AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public Receipt parse(Integer paperlessDocumentId, String rawText) {
        Receipt receipt = new Receipt();
        receipt.setPaperlessDocumentId(paperlessDocumentId);
        receipt.setRawText(rawText == null ? "" : rawText);
        receipt.setImportedAt(OffsetDateTime.now());

        boolean appliedRule = false;

        if (parseRuleRepository != null) {
            List<ParseRule> rules = parseRuleRepository.findAll(Sort.by(Sort.Direction.DESC, "priority"));
            for (ParseRule rule : rules) {
                String regex = rule.getRegex();
                if (regex == null || regex.isBlank()) continue;
                try {
                    Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
                    Matcher m = p.matcher(receipt.getRawText());
                    if (m.find()) {
                        // Apply common named capture groups if present
                        tryApplyNamedGroup(m, "store", v -> receipt.setStoreName(v));
                        tryApplyNamedGroup(m, "total", v -> {
                            try {
                                receipt.setTotalAmount(new BigDecimal(v.replaceAll("[^0-9.,-]", "").replace(',', '.')));
                            } catch (NumberFormatException ignored) {
                            }
                        });
                        tryApplyNamedGroup(m, "date", v -> {
                            try {
                                receipt.setReceiptDate(LocalDate.parse(v));
                            } catch (DateTimeParseException ignored) {
                            }
                        });
                        appliedRule = true;
                        break;
                    }
                } catch (Exception ex) {
                    // ignore malformed regexes
                }
            }
        }

        // If no rule applied, attempt heuristic extraction of items, currency and bonus fields
        boolean heuristicFound = tryHeuristicExtract(receipt);

        // If no rule applied and heuristics didn't find items, try AI fallback (if available), otherwise create a minimal placeholder item
        if (!appliedRule && !heuristicFound && aiClient != null) {
            try {
                java.util.Optional<de.spacerat76.ebon.ai.AiParseResult> res = aiClient.parseReceipt(receipt.getRawText());
                if (res.isPresent()) {
                    de.spacerat76.ebon.ai.AiParseResult ar = res.get();
                    if (ar.getStoreName() != null) receipt.setStoreName(ar.getStoreName());
                    if (ar.getCurrency() != null) receipt.setCurrency(ar.getCurrency());
                    if (ar.getTotalAmount() != null) receipt.setTotalAmount(ar.getTotalAmount());
                    if (ar.getReceiptDate() != null) receipt.setReceiptDate(ar.getReceiptDate());
                    if (ar.getBonusBalance() != null) receipt.setBonusBalance(ar.getBonusBalance());
                    if (ar.getBonusPoints() != null) receipt.setBonusPoints(ar.getBonusPoints());

                    // translate AI items into ReceiptItem
                    if (ar.getItems() != null && !ar.getItems().isEmpty()) {
                        int aiPos = 1;
                        for (de.spacerat76.ebon.ai.AiParseItem aiItem : ar.getItems()) {
                            try {
                                ReceiptItem it = new ReceiptItem();
                                it.setPositionIndex(aiPos++);
                                if (aiItem.getDescription() != null) it.setDescription(aiItem.getDescription());
                                if (aiItem.getQuantity() != null) it.setQuantity(aiItem.getQuantity());
                                if (aiItem.getUnit() != null) it.setUnit(aiItem.getUnit());
                                if (aiItem.getUnitPrice() != null) it.setUnitPrice(aiItem.getUnitPrice());
                                if (aiItem.getTotal() != null) it.setTotalPrice(aiItem.getTotal());
                                else if (it.getUnitPrice() != null && it.getQuantity() != null) it.setTotalPrice(it.getUnitPrice().multiply(it.getQuantity()));
                                else if (it.getUnitPrice() != null) it.setTotalPrice(it.getUnitPrice());
                                else it.setTotalPrice(BigDecimal.ZERO);
                                receipt.addItem(it);
                            } catch (Exception ignored) {}
                        }
                    } else {
                        ReceiptItem item = new ReceiptItem();
                        item.setPositionIndex(1);
                        item.setDescription("Parsed by AI");
                        item.setTotalPrice(receipt.getTotalAmount() == null ? BigDecimal.ZERO : receipt.getTotalAmount());
                        receipt.addItem(item);
                    }

                    // persist suggested parse rule if AI provided one
                    try {
                        if (ar.getSuggestedParseRegex() != null && parseRuleRepository != null) {
                            de.spacerat76.ebon.domain.ParseRule pr = new de.spacerat76.ebon.domain.ParseRule();
                            pr.setName(ar.getSuggestedParseName() != null ? ar.getSuggestedParseName() : "AI-suggested rule");
                            pr.setRegex(ar.getSuggestedParseRegex());
                            pr.setStoreNamePattern(ar.getStoreName() != null ? ar.getStoreName() : null);
                            pr.setPriority(ar.getSuggestedParsePriority() != null ? ar.getSuggestedParsePriority() : 100);
                            pr.setCreatedAt(OffsetDateTime.now());
                            pr.setUpdatedAt(OffsetDateTime.now());
                            parseRuleRepository.save(pr);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {
            }
        }

        // If nothing matched/created items yet, add a placeholder item
        if (receipt.getItems() == null || receipt.getItems().isEmpty()) {
            ReceiptItem item = new ReceiptItem();
            item.setPositionIndex(1);
            item.setDescription(appliedRule ? "Parsed by rule" : "Parsed item");
            item.setTotalPrice(receipt.getTotalAmount() == null ? BigDecimal.ZERO : receipt.getTotalAmount());
            receipt.addItem(item);
        }

        // Determine parse status per spec: PARSED only when total_amount, receipt_date and store_name are present
        // and at least one receipt item has a valid total_price. Otherwise set PARSE_ERROR and record missing fields.
        try {
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (receipt.getTotalAmount() == null) missing.add("total_amount");
            if (receipt.getReceiptDate() == null) missing.add("receipt_date");
            if (receipt.getStoreName() == null || receipt.getStoreName().isBlank()) missing.add("store_name");
            boolean hasValidItem = false;
            if (receipt.getItems() != null) {
                for (ReceiptItem it : receipt.getItems()) {
                    if (it != null && it.getTotalPrice() != null) { hasValidItem = true; break; }
                }
            }
            if (!hasValidItem) missing.add("receipt_item.total_price");
            if (missing.isEmpty()) {
                receipt.setParseStatus("PARSED");
            } else {
                receipt.setParseStatus("PARSE_ERROR");
                receipt.setParseErrorMessage("Missing fields: " + String.join(", ", missing));
            }
        } catch (Exception ignored) {
            receipt.setParseStatus("PARSE_ERROR");
            receipt.setParseErrorMessage("Unknown parse error");
        }

        return receipt;
    }

    private boolean tryHeuristicExtract(Receipt receipt) {
        if (receipt.getRawText() == null || receipt.getRawText().isBlank()) return false;
        String text = receipt.getRawText();
        String[] lines = text.split("\\r?\\n");
        int pos = 1;
        boolean foundAny = false;
        String detectedCurrency = null;

        Pattern p1 = Pattern.compile("(?ix)^\\s*(\\d+(?:[.,]\\d+)?)\\s*(kg|g|l|pcs|stk|x)?\\s+(.+?)\\s+(\\d+[.,]\\d{1,2})\\s*(?:\\p{Sc}|[A-Z]{3})?\\s*(?:\\s+(\\d+[.,]\\d{1,2}))?\\s*$");
        Pattern p2 = Pattern.compile("(?ix)^\\s*(.+?)\\s+(\\d+[.,]\\d{1,2})\\s*(?:\\p{Sc}|[A-Z]{3})?\\s*$");

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()) continue;

            Matcher m1 = p1.matcher(line);
            if (m1.find()) {
                try {
                    ReceiptItem it = new ReceiptItem();
                    it.setPositionIndex(pos++);
                    String desc = m1.group(3).trim();
                    it.setDescription(desc);
                    String qtys = m1.group(1);
                    if (qtys != null && !qtys.isBlank()) {
                        it.setQuantity(parseNumber(qtys));
                    }
                    String unit = m1.group(2);
                    if (unit != null) it.setUnit(unit.trim());
                    String ups = m1.group(4);
                    if (ups != null && !ups.isBlank()) it.setUnitPrice(parseNumber(ups));
                    String totalS = m1.group(5);
                    BigDecimal total = null;
                    if (totalS != null && !totalS.isBlank()) {
                        total = parseNumber(totalS);
                    } else if (it.getQuantity() != null && it.getUnitPrice() != null) {
                        total = it.getUnitPrice().multiply(it.getQuantity());
                    } else if (it.getUnitPrice() != null) {
                        total = it.getUnitPrice();
                    }
                    it.setTotalPrice(total == null ? BigDecimal.ZERO : total);
                    if (detectedCurrency == null) {
                        Matcher curM = Pattern.compile("(\\p{Sc}|[A-Z]{3})").matcher(line);
                        if (curM.find()) detectedCurrency = normalizeCurrency(curM.group(1));
                    }
                    receipt.addItem(it);
                    foundAny = true;
                    continue;
                } catch (Exception ignored) {}
            }

            // skip lines that are totals/bonus/points declarations
            String lower = line.toLowerCase(Locale.ROOT).trim();
            if (lower.matches("^(total|subtotal|bonus|points|punkte|guthaben|saldo|bonussaldo)[:\\s].*")) continue;

            Matcher m2 = p2.matcher(line);
            if (m2.find()) {
                try {
                    ReceiptItem it = new ReceiptItem();
                    it.setPositionIndex(pos++);
                    String desc = m2.group(1).trim();
                    it.setDescription(desc);
                    BigDecimal price = parseNumber(m2.group(2));
                    it.setTotalPrice(price == null ? BigDecimal.ZERO : price);
                    if (detectedCurrency == null) {
                        Matcher curM = Pattern.compile("(\\p{Sc}|[A-Z]{3})").matcher(line);
                        if (curM.find()) detectedCurrency = normalizeCurrency(curM.group(1));
                    }
                    receipt.addItem(it);
                    foundAny = true;
                    continue;
                } catch (Exception ignored) {}
            }
        }

        // detect totals and bonus fields across whole text
        if (detectedCurrency != null) {
            receipt.setCurrency(detectedCurrency);
        } else {
            // try to detect currency near the word Total
            Pattern totalCur = Pattern.compile("(?i)total[:\\s]*([0-9.,]+)\\s*(\\p{Sc}|[A-Z]{3})");
            Matcher mtc = totalCur.matcher(text);
            if (mtc.find()) {
                receipt.setCurrency(normalizeCurrency(mtc.group(2)));
            }
        }

        // sum items to set totalAmount if missing
        try {
            if ((receipt.getTotalAmount() == null || receipt.getTotalAmount().compareTo(BigDecimal.ZERO) == 0) && foundAny) {
                BigDecimal sum = BigDecimal.ZERO;
                for (ReceiptItem it : receipt.getItems()) {
                    if (it.getTotalPrice() != null) sum = sum.add(it.getTotalPrice());
                }
                receipt.setTotalAmount(sum);
            }
        } catch (Exception ignored) {}

        // bonus balance
        try {
                Pattern bonusBal = Pattern.compile("(?i)(?:bonus(?: balance)?|guthaben|saldo|bonussaldo)[^0-9\\n\\r]*([0-9]+(?:[.,][0-9]+)?)\\s*(\\p{Sc}|[A-Z]{3})?");
            Matcher mb = bonusBal.matcher(text);
            if (mb.find()) {
                receipt.setBonusBalance(parseNumber(mb.group(1)));
                String cur = mb.group(2);
                if (cur != null) receipt.setCurrency(normalizeCurrency(cur));
            }
        } catch (Exception ignored) {}

        // bonus points
        try {
            Pattern bonusPts = Pattern.compile("(?i)(?:points|punkte|treuepunkte|bonus points)[^0-9\n\r]*([0-9]+)");
            Matcher mp = bonusPts.matcher(text);
            if (mp.find()) {
                try {
                    receipt.setBonusPoints(new BigDecimal(mp.group(1)));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return foundAny;
    }

    private BigDecimal parseNumber(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[^0-9,.-]", "").trim();
        if (s.isBlank()) return null;
        // normalize comma as decimal separator
        if (s.contains(",") && s.indexOf(',') > s.indexOf('.')) {
            // e.g. 1.234,56 - remove dots
            s = s.replaceAll("\\.", "").replace(',', '.');
        } else {
            s = s.replace(',', '.');
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeCurrency(String cur) {
        if (cur == null) return null;
        cur = cur.trim();
        Map<String, String> map = new HashMap<>();
        map.put(String.valueOf('\u20AC'), "EUR"); map.put("EUR", "EUR");
        map.put(String.valueOf('\u0024'), "USD"); map.put("USD", "USD");
        map.put(String.valueOf('\u00A3'), "GBP"); map.put("GBP", "GBP");
        map.put("CHF", "CHF");
        String upper = cur.toUpperCase();
        return map.getOrDefault(cur, map.getOrDefault(upper, upper));
    }

    private void tryApplyNamedGroup(Matcher m, String groupName, java.util.function.Consumer<String> consumer) {
        try {
            String val = null;
            try {
                val = m.group(groupName);
            } catch (IllegalArgumentException e) {
                // group name not present
            }
            if (val != null) {
                consumer.accept(val.trim());
            }
        } catch (Exception ignored) {
        }
    }
}
