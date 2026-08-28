package de.ebon.parser.profile;

import java.util.List;

public record ReceiptFormatDefinition(
        int schemaVersion,
        List<ProfileAnchor> anchors,
        List<ProfileFieldRule> fields,
        List<ProfileItemRule> itemRules,
        List<ProfileLineRule> lineRules) {
    public ReceiptFormatDefinition {
        anchors = List.copyOf(anchors == null ? List.of() : anchors);
        fields = List.copyOf(fields == null ? List.of() : fields);
        itemRules = List.copyOf(itemRules == null ? List.of() : itemRules);
        lineRules = List.copyOf(lineRules == null ? List.of() : lineRules);
    }
}
