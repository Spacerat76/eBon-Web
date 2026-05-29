package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.Category;
import de.spacerat76.ebon.domain.ReceiptItem;
import de.spacerat76.ebon.repository.CategoryRepository;
import de.spacerat76.ebon.repository.ReceiptItemRepository;
import de.spacerat76.ebon.web.dto.ReceiptItemDto;
import de.spacerat76.ebon.web.dto.ReceiptItemUpdateDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/receipts/{receiptId}/items")
public class ReceiptItemController {

    private final ReceiptItemRepository itemRepository;
    private final CategoryRepository categoryRepository;

    public ReceiptItemController(ReceiptItemRepository itemRepository, CategoryRepository categoryRepository) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<ReceiptItemDto> update(@PathVariable Long receiptId, @PathVariable Long itemId, @RequestBody ReceiptItemUpdateDto dto) {
        Optional<ReceiptItem> opt = itemRepository.findById(itemId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ReceiptItem item = opt.get();
        if (item.getReceipt() == null || !item.getReceipt().getId().equals(receiptId)) {
            return ResponseEntity.notFound().build();
        }

        if (dto.getDescription() != null) item.setDescription(dto.getDescription());
        if (dto.getTotalPrice() != null) item.setTotalPrice(dto.getTotalPrice());
        if (dto.getCategoryId() != null) {
            Optional<Category> copt = categoryRepository.findById(dto.getCategoryId());
            if (copt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            item.setCategory(copt.get());
            item.setCategorySource("MANUAL");
        }

        item.setIsManuallyEdited(true);
        item.setUpdatedAt(OffsetDateTime.now());

        ReceiptItem saved = itemRepository.save(item);
        return ResponseEntity.ok(toDto(saved));
    }

    private ReceiptItemDto toDto(ReceiptItem i) {
        ReceiptItemDto dto = new ReceiptItemDto();
        dto.setId(i.getId());
        dto.setPositionIndex(i.getPositionIndex());
        dto.setDescription(i.getDescription());
        dto.setTotalPrice(i.getTotalPrice());
        dto.setCategory(i.getCategory() != null ? i.getCategory().getName() : null);
        return dto;
    }
}
