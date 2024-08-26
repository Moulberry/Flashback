package com.moulberry.flashback.state;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.visuals.ReplayVisuals;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class EditorState {

    transient boolean dirty = false;
    public transient int modCount = ThreadLocalRandom.current().nextInt();

    public final List<KeyframeTrack> keyframeTracks = new ArrayList<>();
    public final ReplayVisuals replayVisuals = new ReplayVisuals();
    private final EditorStateHistory history = new EditorStateHistory();

    public double zoomMin = 0.0;
    public double zoomMax = 1.0;
    public int exportStartTicks = -1;
    public int exportEndTicks = -1;

    public UUID audioSourceEntity = null;

    public void setKeyframe(int trackIndex, int tick, Keyframe keyframe) {
        if (trackIndex >= this.keyframeTracks.size()) {
            return;
        }

        List<EditorStateHistoryAction> undo = new ArrayList<>();
        List<EditorStateHistoryAction> redo = new ArrayList<>();

        String description;

        KeyframeTrack track = this.keyframeTracks.get(trackIndex);
        Keyframe old = track.keyframesByTick.get(tick);
        if (old != null) {
            undo.add(new EditorStateHistoryAction.SetKeyframe(track.keyframeType, trackIndex, tick, old.copy()));
            description = "Replaced " + track.keyframeType + " keyframe";
        } else {
            undo.add(new EditorStateHistoryAction.RemoveKeyframe(track.keyframeType, trackIndex, tick));
            description = "Added " + track.keyframeType + " keyframe";
        }
        redo.add(new EditorStateHistoryAction.SetKeyframe(track.keyframeType, trackIndex, tick, keyframe.copy()));

        this.push(new EditorStateHistoryEntry(undo, redo, description));
    }

    public void push(EditorStateHistoryEntry entry) {
        if (entry.undo().isEmpty() && entry.redo().isEmpty()) {
            return;
        }

        this.history.push(this, entry);
        this.markDirty();
    }

    public void undo(Consumer<String> descriptionConsumer) {
        this.history.undo(this, descriptionConsumer);
        this.markDirty();
    }

    public void redo(Consumer<String> descriptionConsumer) {
        this.history.redo(this, descriptionConsumer);
        this.markDirty();
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

        this.dirty = true;
    }

    public void markDirty() {
        this.dirty = true;
        this.modCount += 1;
    }

    public void save(Path path) {
        this.dirty = false;

        String serialized = FlashbackGson.PRETTY.toJson(this, EditorState.class);

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, serialized, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE,
                    StandardOpenOption.SYNC);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public static EditorState load(Path path) {
        if (!Files.exists(path)) {
            return null;
        }

        String serialized = null;
        try {
            serialized = Files.readString(path);
            return FlashbackGson.PRETTY.fromJson(serialized, EditorState.class);
        } catch (Exception e) {
            Flashback.LOGGER.error("Error loading editor state", e);
            Flashback.LOGGER.error("JSON: {}", serialized);
            return null;
        }
    }

    public EditorState copy() {
        String serialized = FlashbackGson.COMPRESSED.toJson(this, EditorState.class);
        return FlashbackGson.COMPRESSED.fromJson(serialized, EditorState.class);
    }

    public void applyKeyframes(KeyframeHandler keyframeHandler, float tick) {
        EnumSet<KeyframeType> applied = EnumSet.noneOf(KeyframeType.class);
        for (KeyframeTrack keyframeTrack : this.keyframeTracks) {
            // Ignore lines that are disabled
            if (!keyframeTrack.enabled) {
                continue;
            }

            KeyframeType baseType = keyframeTrack.keyframeType == KeyframeType.CAMERA_ORBIT ? KeyframeType.CAMERA : keyframeTrack.keyframeType;

            // Already applied a keyframe of this type earlier, skip
            if (applied.contains(baseType)) {
                continue;
            }

            // Try to apply keyframes, mark applied if successful
            if (keyframeTrack.tryApplyKeyframes(keyframeHandler, tick)) {
                applied.add(baseType);
            }
        }
    }

}
