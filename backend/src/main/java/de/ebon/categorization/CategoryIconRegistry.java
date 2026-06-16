package de.ebon.categorization;

import de.ebon.api.dto.CategoryIconDto;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CategoryIconRegistry {

    private static final List<CategoryIconDto> ICONS = List.of(
            icon("apple", "Salat, Obst & Gemuese"),
            icon("baby", "Baby und Kind"),
            icon("beef", "Fleisch und Wurst"),
            icon("camera", "Fotos & Bilder"),
            icon("circle-help", "Sonstiges"),
            icon("cookie", "Suesswaren und Snacks"),
            icon("cup-soda", "Getraenke"),
            icon("fish", "Fisch und Meeresfruechte"),
            icon("fuel", "Mobilitaet und Tanken"),
            icon("hammer", "Baumarkt und Garten"),
            icon("heart-pulse", "Gesundheit"),
            icon("home", "Haushalt"),
            icon("image", "Fotos & Bilder"),
            icon("milk", "Milchprodukte und Eier"),
            icon("package", "Vorrat und Fertiggerichte"),
            icon("paw-print", "Tierbedarf"),
            icon("receipt", "Pfand und Rabatte"),
            icon("salad", "Salat"),
            icon("shopping-basket", "Lebensmittel"),
            icon("sparkles", "Drogerie / Koerperpflege"),
            icon("tag", "Tag / Rabatt"),
            icon("ticket", "Freizeit"),
            icon("utensils", "Gastronomie"),
            icon("wheat", "Brot und Backwaren")
    );

    private static final Set<String> ICON_VALUES = ICONS.stream()
            .map(CategoryIconDto::value)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    public List<CategoryIconDto> list() {
        return ICONS;
    }

    public String normalizeAndValidate(String icon) {
        if (icon == null) {
            return null;
        }
        String normalized = icon.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (!ICON_VALUES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Kategorie-Icon ist nicht erlaubt.");
        }
        return normalized;
    }

    private static CategoryIconDto icon(String value, String label) {
        return new CategoryIconDto(value, label);
    }
}
