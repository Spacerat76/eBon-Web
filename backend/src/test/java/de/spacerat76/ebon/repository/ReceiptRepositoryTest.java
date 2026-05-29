package de.spacerat76.ebon.repository;

import de.spacerat76.ebon.domain.Category;
import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.domain.ReceiptItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.flyway.enabled=false","spring.datasource.url=jdbc:h2:mem:ebon;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop"})
@Transactional
class ReceiptRepositoryTest {

    @Autowired
    ReceiptRepository receiptRepository;

    @Autowired
    ReceiptItemRepository receiptItemRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Test
    void saveAndQueryReceipt() {
        Category cat = new Category();
        cat.setName("Groceries");
        categoryRepository.save(cat);

        Receipt r = new Receipt();
        r.setPaperlessDocumentId(123);
        r.setRawText("raw receipt text");
        r.setTotalAmount(new BigDecimal("12.34"));
        r.setParseStatus("PENDING");
        r.setImportedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        r = receiptRepository.save(r);

        ReceiptItem item = new ReceiptItem();
        item.setReceipt(r);
        item.setPositionIndex(1);
        item.setDescription("Milk 1L");
        item.setTotalPrice(new BigDecimal("12.34"));
        item.setCategory(cat);
        receiptItemRepository.save(item);

        var found = receiptRepository.findByPaperlessDocumentId(123);
        assertTrue(found.isPresent());

        var items = receiptItemRepository.findAllByReceiptId(r.getId());
        assertEquals(1, items.size());
        assertEquals("Milk 1L", items.get(0).getDescription());
    }
}
