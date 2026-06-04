package de.ebon.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptParserPropertiesTests {

    // Verifies dm branch mappings resolve both full scanner codes and their stable base code.
    @Test
    void resolveDmBranchUsesExactMatchAndFallbackToBaseCode() {
        ReceiptParserProperties properties = new ReceiptParserProperties();
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("D482", "Neuss");
        mappings.put("D5A4", "Holzheimer Weg 44");
        properties.setDmBranchMappings(mappings);

        assertThat(properties.resolveDmBranch("d482/1")).contains("Neuss");
        assertThat(properties.resolveDmBranch(" D5A4/3 ")).contains("Holzheimer Weg 44");
        assertThat(properties.resolveDmBranch("D482")).contains("Neuss");
    }

    // Verifies unknown or blank dm branch mappings fail closed instead of returning misleading branches.
    @Test
    void resolveDmBranchReturnsEmptyForUnknownBlankAndBlankMappedValues() {
        ReceiptParserProperties properties = new ReceiptParserProperties();
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("D482", "  ");
        properties.setDmBranchMappings(mappings);

        assertThat(properties.resolveDmBranch(null)).isEmpty();
        assertThat(properties.resolveDmBranch("   ")).isEmpty();
        assertThat(properties.resolveDmBranch("UNKNOWN/1")).isEmpty();
        assertThat(properties.resolveDmBranch("D482")).isEmpty();
    }

    // Verifies null configuration is normalized to an empty mapping for safe property binding.
    @Test
    void setDmBranchMappingsAcceptsNullAsEmptyMap() {
        ReceiptParserProperties properties = new ReceiptParserProperties();
        properties.setDmBranchMappings(null);

        assertThat(properties.getDmBranchMappings()).isEmpty();
    }
}
