package com.moulberry.flashback.state;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EditorSceneHistory {

    private final List<EditorSceneHistoryEntry> entries = new ArrayList<>();
    private int position = 0;

    public void push(EditorScene editorScene, EditorSceneHistoryEntry entry) {
        while (this.entries.size() > this.position) {
            this.entries.removeLast();
        }
        while (this.entries.size() >= 256) {
            this.entries.removeFirst();
            this.position -= 1;
        }

        for (EditorSceneHistoryAction redo : entry.redo()) {
            redo.apply(editorScene);
        }

        this.entries.add(entry);
        this.position += 1;
    }

    public void undo(EditorScene editorScene, Consumer<String> descriptionConsumer) {
        if (this.position == 0) {
            return;
        }

        this.position -= 1;
        EditorSceneHistoryEntry entry = this.entries.get(this.position);
        for (EditorSceneHistoryAction undo : entry.undo()) {
            undo.apply(editorScene);
        }
        descriptionConsumer.accept("Undo '" + entry.description() + "'");
    }

    public void redo(EditorScene editorScene, Consumer<String> descriptionConsumer) {
        if (this.position >= this.entries.size()) {
            return;
        }

        EditorSceneHistoryEntry entry = this.entries.get(this.position);
        for (EditorSceneHistoryAction redo : entry.redo()) {
            redo.apply(editorScene);
        }
        descriptionConsumer.accept("Redo '" + entry.description() + "'");

        this.position += 1;
    }

}
