package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.domain.ReceiptItem;
import de.spacerat76.ebon.repository.ReceiptRepository;
import de.spacerat76.ebon.service.PaperlessSyncService;
import de.spacerat76.ebon.web.dto.ReceiptDto;
import de.spacerat76.ebon.web.dto.ReceiptItemDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptRepository receiptRepository;
    private final PaperlessSyncService paperlessSyncService;

    public ReceiptController(ReceiptRepository receiptRepository, PaperlessSyncService paperlessSyncService) {
        this.receiptRepository = receiptRepository;
        this.paperlessSyncService = paperlessSyncService;
    }

    @GetMapping
    public List<ReceiptDto> list() {
        List<Receipt> receipts = receiptRepository.findAll();
        return receipts.stream().map(this::toDto).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReceiptDto> get(@PathVariable Long id) {
        Optional<Receipt> opt = receiptRepository.findById(id);
        return opt.map(r -> ResponseEntity.ok(toDto(r))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> sync() {
        paperlessSyncService.syncNewDocuments();
        return ResponseEntity.accepted().build();
    }

    private ReceiptDto toDto(Receipt r) {
        ReceiptDto dto = new ReceiptDto();
        dto.setId(r.getId());
        dto.setPaperlessDocumentId(r.getPaperlessDocumentId());
        dto.setStoreName(r.getStoreName());
        dto.setTotalAmount(r.getTotalAmount());
        dto.setCurrency(r.getCurrency());
        dto.setParseStatus(r.getParseStatus());
        List<ReceiptItemDto> items = r.getItems().stream().map(this::toItemDto).collect(Collectors.toList());
        dto.setItems(items);
        return dto;
    }

    private ReceiptItemDto toItemDto(ReceiptItem i) {
        ReceiptItemDto dto = new ReceiptItemDto();
        dto.setId(i.getId());
        dto.setPositionIndex(i.getPositionIndex());
        dto.setDescription(i.getDescription());
        dto.setTotalPrice(i.getTotalPrice());
        dto.setCategory(i.getCategory() != null ? i.getCategory().getName() : null);
        return dto;
    }
}
