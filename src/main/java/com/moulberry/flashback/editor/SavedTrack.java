package com.moulberry.flashback.editor;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateHistoryAction;
import com.moulberry.flashback.state.EditorStateHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record SavedTrack(KeyframeType<?> type, int track, boolean copiedFromDisabled,
                         TreeMap<Integer, Keyframe> keyframes) {
    public int applyToEditorState(EditorState editorState, int cursorTicks, int totalTicks) {
        if (this.keyframes == null || this.keyframes.isEmpty()) {
            return 0;
        }

        // Try to apply directly to copied track
        if (this.track >= 0 && this.track < editorState.keyframeTracks.size()) {
            KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(this.track);
            if ((keyframeTrack.enabled || this.copiedFromDisabled) && keyframeTrack.keyframeType == this.type) {
                return this.applyTo(editorState, this.type, keyframeTrack, this.track, cursorTicks, totalTicks);
            }
        }

        // Try to find first eligible enabled track
        for (KeyframeTrack keyframeTrack : editorState.keyframeTracks) {
            if (keyframeTrack.enabled && keyframeTrack.keyframeType == this.type) {
                return this.applyTo(editorState, this.type, keyframeTrack, this.track, cursorTicks, totalTicks);
            }
        }

        // Create new track
        return this.applyTo(editorState, this.type, null, this.track, cursorTicks, totalTicks);
    }

    private int applyTo(EditorState editorState, KeyframeType type, @Nullable KeyframeTrack existing, int trackIndex, int cursorTicks, int totalTicks) {
        List<EditorStateHistoryAction> undo = new ArrayList<>();
        List<EditorStateHistoryAction> redo = new ArrayList<>();

        if (existing == null) {
            undo.add(new EditorStateHistoryAction.RemoveTrack(type, trackIndex));
            redo.add(new EditorStateHistoryAction.AddTrack(type, trackIndex));
        }

        int count = 0;
        for (Map.Entry<Integer, Keyframe> entry : this.keyframes.entrySet()) {
            int newTick = entry.getKey() + cursorTicks;
            if (newTick >= 0 && newTick <= totalTicks) {
                if (existing != null) {
                    Keyframe old = existing.keyframesByTick.get(newTick);

                    if (old != null) {
                        undo.add(new EditorStateHistoryAction.SetKeyframe(type, trackIndex, newTick, old.copy()));
                    } else {
                        undo.add(new EditorStateHistoryAction.RemoveKeyframe(type, trackIndex, newTick));
                    }
                }
                redo.add(new EditorStateHistoryAction.SetKeyframe(type, trackIndex, newTick, entry.getValue().copy()));

                count += 1;
            }
        }

        editorState.push(new EditorStateHistoryEntry(undo, redo, "Pasted " + count + " keyframe(s)"));

        return count;
    }
}
