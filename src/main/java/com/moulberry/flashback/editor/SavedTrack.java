package com.moulberry.flashback.editor;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.KeyframeTrack;

import java.util.Map;
import java.util.TreeMap;

public record SavedTrack(KeyframeType type, int track, boolean copiedFromDisabled,
                         TreeMap<Integer, Keyframe<?>> keyframes) {
    public int applyToEditorState(EditorState editorState, int cursorTicks, int totalTicks) {
        if (this.keyframes == null || this.keyframes.isEmpty()) {
            return 0;
        }

        editorState.dirty = true;

        // Try to apply directly to copied track
        if (this.track >= 0 && this.track < editorState.keyframeTracks.size()) {
            KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(this.track);
            if ((keyframeTrack.enabled || this.copiedFromDisabled) && keyframeTrack.keyframeType == this.type) {
                return this.applyTo(keyframeTrack, cursorTicks, totalTicks);
            }
        }

        // Try to find first eligible enabled track
        for (KeyframeTrack keyframeTrack : editorState.keyframeTracks) {
            if (keyframeTrack.enabled && keyframeTrack.keyframeType == this.type) {
                return this.applyTo(keyframeTrack, cursorTicks, totalTicks);
            }
        }

        // Create new track
        KeyframeTrack keyframeTrack = new KeyframeTrack(this.type);
        int count = this.applyTo(keyframeTrack, cursorTicks, totalTicks);
        if (!keyframeTrack.keyframesByTick.isEmpty()) {
            editorState.keyframeTracks.add(keyframeTrack);
        }
        return count;
    }

    public int applyTo(KeyframeTrack keyframeTrack, int cursorTicks, int totalTicks) {
        int count = 0;
        for (Map.Entry<Integer, Keyframe<?>> entry : this.keyframes.entrySet()) {
            int newTick = entry.getKey() + cursorTicks;
            if (newTick >= 0 && newTick <= totalTicks) {
                keyframeTrack.keyframesByTick.put(newTick, entry.getValue());
                count += 1;
            }
        }
        return count;
    }
}
