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

    @Override
    public Receipt parse(Integer paperlessDocumentId, String rawText) {
        Receipt receipt = new Receipt();
        receipt.setPaperlessDocumentId(paperlessDocumentId);
        receipt.setRawText(rawText == null ? "" : rawText);
        receipt.setImportedAt(OffsetDateTime.now());
        receipt.setParseStatus("PARSED");

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

        // If no rule applied, try AI fallback (if available), otherwise create a minimal placeholder item
        if (!appliedRule && aiClient != null) {
            try {
                java.util.Optional<de.spacerat76.ebon.ai.AiParseResult> res = aiClient.parseReceipt(receipt.getRawText());
                if (res.isPresent()) {
                    de.spacerat76.ebon.ai.AiParseResult ar = res.get();
                    if (ar.getStoreName() != null) receipt.setStoreName(ar.getStoreName());
                    if (ar.getTotalAmount() != null) receipt.setTotalAmount(ar.getTotalAmount());
                    if (ar.getReceiptDate() != null) receipt.setReceiptDate(ar.getReceiptDate());
                    ReceiptItem item = new ReceiptItem();
                    item.setPositionIndex(1);
                    item.setDescription("Parsed by AI");
                    item.setTotalPrice(receipt.getTotalAmount() == null ? BigDecimal.ZERO : receipt.getTotalAmount());
                    receipt.addItem(item);
                    return receipt;
                }
            } catch (Exception ignored) {
            }
        }

        ReceiptItem item = new ReceiptItem();
        item.setPositionIndex(1);
        item.setDescription(appliedRule ? "Parsed by rule" : "Parsed item");
        item.setTotalPrice(receipt.getTotalAmount() == null ? BigDecimal.ZERO : receipt.getTotalAmount());
        receipt.addItem(item);

        return receipt;
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
