package com.moulberry.flashback.state;

import java.util.List;

public record EditorSceneHistoryEntry(List<EditorSceneHistoryAction> undo, List<EditorSceneHistoryAction> redo, String description) {
}
