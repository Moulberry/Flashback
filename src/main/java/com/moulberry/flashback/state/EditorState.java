package com.moulberry.flashback.state;

import com.google.gson.*;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.impl.TickrateKeyframe;
import com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe;
import com.moulberry.flashback.serialization.QuaternionTypeAdapater;
import com.moulberry.flashback.serialization.Vector3fTypeAdapater;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class EditorState {

    public transient boolean dirty = false;

    public final List<KeyframeTrack> keyframeTracks = new ArrayList<>();

    public float zoomMin = 0.0f;
    public float zoomMax = 1.0f;
    public int exportStartTicks = -1;
    public int exportEndTicks = -1;

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

        if (this.exportStartTicks == 0 && this.exportEndTicks == totalTicks) {
            this.exportStartTicks = -1;
            this.exportEndTicks = -1;
        }

        this.dirty = true;
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

        try {
            String serialized = Files.readString(path);
            return FlashbackGson.PRETTY.fromJson(serialized, EditorState.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public <T> void applyKeyframes(T t, float tick) {
        EnumSet<KeyframeType> applied = EnumSet.noneOf(KeyframeType.class);
        for (KeyframeTrack keyframeTrack : this.keyframeTracks) {
            // Ignore lines that are disabled
            if (!keyframeTrack.enabled) {
                continue;
            }

            // Already applied a keyframe of this type earlier, skip
            if (applied.contains(keyframeTrack.keyframeType)) {
                continue;
            }

            // Try to apply keyframes, mark applied if successful
            if (keyframeTrack.tryApplyKeyframes(t, tick)) {
                applied.add(keyframeTrack.keyframeType);
            }
        }
    }

}
