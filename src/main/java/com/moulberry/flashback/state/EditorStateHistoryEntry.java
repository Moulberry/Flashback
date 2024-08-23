package com.moulberry.flashback.state;

import java.util.List;

public record EditorStateHistoryEntry(List<EditorStateHistoryAction> undo, List<EditorStateHistoryAction> redo, String description) {
}
