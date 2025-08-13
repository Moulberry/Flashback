package com.moulberry.flashback.editor;

import com.moulberry.flashback.editor.ui.KeyframeRelativeOffsets;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record SavedTrack(KeyframeType<?> type, int track, boolean copiedFromDisabled,
                         TreeMap<Integer, Keyframe> keyframes) {
    public int applyToScene(EditorScene editorScene, int cursorTicks, int totalTicks, KeyframeRelativeOffsets offsets) {
        if (this.keyframes == null || this.keyframes.isEmpty()) {
            return 0;
        }

        // Try to apply directly to copied track
        if (this.track >= 0 && this.track < editorScene.keyframeTracks.size()) {
            KeyframeTrack keyframeTrack = editorScene.keyframeTracks.get(this.track);
            if ((keyframeTrack.enabled || this.copiedFromDisabled) && keyframeTrack.keyframeType == this.type) {
                return this.applyTo(editorScene, keyframeTrack, this.track, cursorTicks, totalTicks, offsets);
            }
        }

        // Try to find first eligible enabled track
        for (KeyframeTrack keyframeTrack : editorScene.keyframeTracks) {
            if (keyframeTrack.enabled && keyframeTrack.keyframeType == this.type) {
                return this.applyTo(editorScene, keyframeTrack, this.track, cursorTicks, totalTicks, offsets);
            }
        }

        // Create new track
        return this.applyTo(editorScene, null, editorScene.keyframeTracks.size(), cursorTicks, totalTicks, offsets);
    }

    private int applyTo(EditorScene editorScene, @Nullable KeyframeTrack existing, int trackIndex, int cursorTicks, int totalTicks, KeyframeRelativeOffsets offsets) {
        List<EditorSceneHistoryAction> undo = new ArrayList<>();
        List<EditorSceneHistoryAction> redo = new ArrayList<>();

        if (existing == null) {
            undo.add(new EditorSceneHistoryAction.RemoveTrack(this.type, trackIndex));
            redo.add(new EditorSceneHistoryAction.AddTrack(this.type, trackIndex));
        }

        int count = 0;
        for (Map.Entry<Integer, Keyframe> entry : this.keyframes.entrySet()) {
            int newTick = entry.getKey() + cursorTicks;
            if (newTick >= 0 && newTick <= totalTicks) {
                if (existing != null) {
                    Keyframe old = existing.keyframesByTick.get(newTick);

                    if (old != null) {
                        undo.add(new EditorSceneHistoryAction.SetKeyframe(this.type, trackIndex, newTick, old.copy()));
                    } else {
                        undo.add(new EditorSceneHistoryAction.RemoveKeyframe(this.type, trackIndex, newTick));
                    }
                }

                Keyframe newKeyframe = entry.getValue().copy();
                offsets.apply(newKeyframe);
                redo.add(new EditorSceneHistoryAction.SetKeyframe(this.type, trackIndex, newTick, newKeyframe));

                count += 1;
            }
        }

        editorScene.push(new EditorSceneHistoryEntry(undo, redo, "Pasted " + count + " keyframe(s)"));

        return count;
    }
}
