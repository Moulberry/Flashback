package com.moulberry.flashback.editor;

import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.KeyframeTrack;
import it.unimi.dsi.fastutil.ints.IntSet;

public record SelectedKeyframes(KeyframeType<?> type, int trackIndex, IntSet keyframeTicks) {
    public boolean checkValid(EditorScene scene) {
        if (this.trackIndex < 0 || this.trackIndex >= scene.keyframeTracks.size()) {
            return false;
        }

        KeyframeTrack keyframeTrack = scene.keyframeTracks.get(this.trackIndex);
        if (keyframeTrack.keyframeType != this.type) {
            return false;
        }

        this.keyframeTicks.removeIf(tick -> !keyframeTrack.keyframesByTick.containsKey(tick));
        return !this.keyframeTicks.isEmpty();
    }
}
