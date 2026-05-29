package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Category;
import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CategorizationServiceImpl implements CategorizationService {

    private final CategoryRepository categoryRepository;

    public CategorizationServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void categorize(Receipt receipt) {
        String raw = receipt.getRawText() == null ? "" : receipt.getRawText().toLowerCase();
        String categoryName = raw.contains("supermarket") ? "Groceries" : "Uncategorized";

        Optional<Category> opt = categoryRepository.findByName(categoryName);
        Category cat = opt.orElseGet(() -> {
            Category c = new Category();
            c.setName(categoryName);
            c.setIsActive(true);
            return categoryRepository.save(c);
        });

        if (!receipt.getItems().isEmpty()) {
            receipt.getItems().get(0).setCategory(cat);
            receipt.getItems().get(0).setCategorySource("AUTOMATIC");
        }
    }
}
