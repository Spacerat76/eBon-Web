package de.ebon.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.parser")
public class ReceiptParserProperties {

    private Map<String, String> dmBranchMappings = new LinkedHashMap<>();

    public Optional<String> resolveDmBranch(String branchCode) {
        String normalizedBranchCode = normalizeCode(branchCode);
        if (normalizedBranchCode == null) {
            return Optional.empty();
        }

        Optional<String> exactMatch = findDmBranch(normalizedBranchCode);
        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        String baseCode = baseCode(normalizedBranchCode);
        if (baseCode.equals(normalizedBranchCode)) {
            return Optional.empty();
        }
        return findDmBranch(baseCode);
    }

    private Optional<String> findDmBranch(String normalizedBranchCode) {
        return dmBranchMappings.entrySet().stream()
                .filter(mapping -> normalizedBranchCode.equals(normalizeCode(mapping.getKey())))
                .map(Map.Entry::getValue)
                .filter(branch -> branch != null && !branch.isBlank())
                .findFirst();
    }

    private String baseCode(String branchCode) {
        int slashIndex = branchCode.indexOf('/');
        return slashIndex < 0 ? branchCode : branchCode.substring(0, slashIndex);
    }

    private String normalizeCode(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) {
            return null;
        }
        return branchCode.trim().toUpperCase(Locale.ROOT);
    }

    public Map<String, String> getDmBranchMappings() {
        return dmBranchMappings;
    }

    public void setDmBranchMappings(Map<String, String> dmBranchMappings) {
        this.dmBranchMappings = dmBranchMappings == null ? new LinkedHashMap<>() : dmBranchMappings;
    }
}
