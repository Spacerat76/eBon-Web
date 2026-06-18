package de.ebon.parser;

import de.ebon.persistence.model.AiParsingTrigger;

public record ParseExecutionOptions(
        AiParsingTrigger trigger,
        boolean useAiFallback,
        AiParsingTextMode requestedTextMode,
        boolean fullTextConfirmed,
        AiParsingBudget budget) {

    public static ParseExecutionOptions sync(AiParsingBudget budget) {
        return new ParseExecutionOptions(AiParsingTrigger.SYNC_AUTO, true, null, false, budget);
    }

    public static ParseExecutionOptions manual(boolean useAiFallback, AiParsingTextMode textMode, boolean fullTextConfirmed) {
        AiParsingTrigger trigger = textMode == AiParsingTextMode.FULL_TEXT
                ? AiParsingTrigger.MANUAL_REPARSE_FORCE_FULL_TEXT
                : AiParsingTrigger.MANUAL_REPARSE;
        return new ParseExecutionOptions(trigger, useAiFallback, textMode, fullTextConfirmed, AiParsingBudget.unlimited());
    }

    public static ParseExecutionOptions bulk() {
        return new ParseExecutionOptions(AiParsingTrigger.BULK_REPARSE, true, null, false, AiParsingBudget.unlimited());
    }
}
