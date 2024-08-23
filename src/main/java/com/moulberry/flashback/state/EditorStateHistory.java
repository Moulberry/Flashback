package com.moulberry.flashback.state;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EditorStateHistory {

    private final List<EditorStateHistoryEntry> entries = new ArrayList<>();
    private int position = 0;

    public void push(EditorState editorState, EditorStateHistoryEntry entry) {
        while (this.entries.size() > this.position) {
            this.entries.removeLast();
        }
        while (this.entries.size() >= 256) {
            this.entries.removeFirst();
            this.position -= 1;
        }

        for (EditorStateHistoryAction redo : entry.redo()) {
            redo.apply(editorState);
        }

        this.entries.add(entry);
        this.position += 1;
    }

    public void undo(EditorState editorState, Consumer<String> descriptionConsumer) {
        if (this.position == 0) {
            return;
        }

        this.position -= 1;
        EditorStateHistoryEntry entry = this.entries.get(this.position);
        for (EditorStateHistoryAction undo : entry.undo()) {
            undo.apply(editorState);
        }
        descriptionConsumer.accept("Undo '" + entry.description() + "'");
    }

    public void redo(EditorState editorState, Consumer<String> descriptionConsumer) {
        if (this.position >= this.entries.size()) {
            return;
        }

        EditorStateHistoryEntry entry = this.entries.get(this.position);
        for (EditorStateHistoryAction redo : entry.redo()) {
            redo.apply(editorState);
        }
        descriptionConsumer.accept("Redo '" + entry.description() + "'");

        this.position += 1;
    }

}
