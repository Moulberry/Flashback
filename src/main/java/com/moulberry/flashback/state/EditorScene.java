package com.moulberry.flashback.state;

import com.moulberry.flashback.keyframe.Keyframe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EditorScene {

    public String name;
    public final List<KeyframeTrack> keyframeTracks = new ArrayList<>();
    public int exportStartTicks = -1;
    public int exportEndTicks = -1;

    private final EditorSceneHistory history = new EditorSceneHistory();

    public EditorScene(String name) {
        this.name = name;
    }

    public void setKeyframe(int trackIndex, int tick, Keyframe keyframe) {
        if (trackIndex >= this.keyframeTracks.size()) {
            return;
        }

        List<EditorSceneHistoryAction> undo = new ArrayList<>();
        List<EditorSceneHistoryAction> redo = new ArrayList<>();

        String description;

        KeyframeTrack track = this.keyframeTracks.get(trackIndex);
        Keyframe old = track.keyframesByTick.get(tick);
        if (old != null) {
            undo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, trackIndex, tick, old.copy()));
            description = "Replaced " + track.keyframeType.name() + " keyframe";
        } else {
            undo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, trackIndex, tick));
            description = "Added " + track.keyframeType.name() + " keyframe";
        }
        redo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, trackIndex, tick, keyframe.copy()));

        this.push(new EditorSceneHistoryEntry(undo, redo, description));
    }

    public void push(EditorSceneHistoryEntry entry) {
        if (entry.undo().isEmpty() && entry.redo().isEmpty()) {
            return;
        }

        this.history.push(this, entry);
    }

    public void undo(Consumer<String> descriptionConsumer) {
        this.history.undo(this, descriptionConsumer);
    }

    public void redo(Consumer<String> descriptionConsumer) {
        this.history.redo(this, descriptionConsumer);
    }

    public void setExportTicks(int start, int end, int totalTicks) {
        if (this.exportEndTicks < 0) {
            this.exportEndTicks = totalTicks;
        }
        this.exportStartTicks = Math.max(0, Math.min(totalTicks, this.exportStartTicks));
        this.exportEndTicks = Math.max(0, Math.min(totalTicks, this.exportEndTicks));

        if (start >= 0) {
            this.exportStartTicks = start;
            if (this.exportEndTicks < start) {
                this.exportEndTicks = start;
            }
        }

        if (end >= 0) {
            this.exportEndTicks = end;
            if (this.exportStartTicks > end) {
                this.exportStartTicks = end;
            }
        }

        if (this.exportStartTicks <= 0 && this.exportEndTicks >= totalTicks) {
            this.exportStartTicks = -1;
            this.exportEndTicks = -1;
        }
    }


}
