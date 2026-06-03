package de.ebon.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptParserPropertiesTests {

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

    @Test
    void setDmBranchMappingsAcceptsNullAsEmptyMap() {
        ReceiptParserProperties properties = new ReceiptParserProperties();
        properties.setDmBranchMappings(null);

        assertThat(properties.getDmBranchMappings()).isEmpty();
    }
}
