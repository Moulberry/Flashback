package com.moulberry.flashback.state;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.types.CameraKeyframeType;
import com.moulberry.flashback.keyframe.types.CameraOrbitKeyframeType;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.visuals.ReplayVisuals;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
    public Set<UUID> hideDuringExport = new HashSet<>();

    public transient int recordingCameraMovementTrack = -1;
    public transient int recordingCameraMovementMinTick = -1;
    public transient int recordingCameraMovementMaxTick = -1;
    public transient Int2ObjectMap<CameraKeyframe> recordingCameraKeyframes = new Int2ObjectOpenHashMap<>();

    public void recordCameraMovement() {
        if (recordingCameraMovementTrack < 0) {
            return;
        }

        if (!ReplayUI.recordCameraMovement) {
            finishRecordingCameraMovement();
            return;
        }

        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            if (replayServer.replayPaused) {
                finishRecordingCameraMovement();
                return;
            }

            Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
            if (cameraEntity == null) {
                return;
            }

            int tick = replayServer.getReplayTick();
            if (recordingCameraMovementMaxTick >= 0) {
                int nextCapture = recordingCameraMovementMaxTick + 4;
                if (tick >= nextCapture) {
                    tick = nextCapture;
                } else {
                    return;
                }
            }

            recordingCameraKeyframes.put(tick, new CameraKeyframe(Minecraft.getInstance().getCameraEntity()));

            if (recordingCameraMovementMinTick < 0) {
                recordingCameraMovementMinTick = tick;
            }

            recordingCameraMovementMinTick = Math.min(recordingCameraMovementMinTick, tick);
            recordingCameraMovementMaxTick = Math.max(recordingCameraMovementMaxTick, tick);
        }
    }

    public void finishRecordingCameraMovement() {
        int trackIndex = recordingCameraMovementTrack;
        if (trackIndex >= 0 && trackIndex < this.keyframeTracks.size()) {
            List<EditorStateHistoryAction> undo = new ArrayList<>();
            List<EditorStateHistoryAction> redo = new ArrayList<>();

            KeyframeTrack track = this.keyframeTracks.get(trackIndex);

            for (Int2ObjectMap.Entry<CameraKeyframe> entry : recordingCameraKeyframes.int2ObjectEntrySet()) {
                int tick = entry.getIntKey();

                Keyframe old = track.keyframesByTick.get(entry.getIntKey());
                if (old != null) {
                    undo.add(new EditorStateHistoryAction.SetKeyframe(track.keyframeType, trackIndex, tick, old.copy()));
                } else {
                    undo.add(new EditorStateHistoryAction.RemoveKeyframe(track.keyframeType, trackIndex, tick));
                }
                redo.add(new EditorStateHistoryAction.SetKeyframe(track.keyframeType, trackIndex, tick, entry.getValue()));
            }

            this.push(new EditorStateHistoryEntry(undo, redo, "Record movement as keyframes"));
        }

        recordingCameraMovementTrack = -1;
        recordingCameraMovementMinTick = -1;
        recordingCameraMovementMaxTick = -1;
        recordingCameraKeyframes.clear();
    }

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
            description = "Replaced " + track.keyframeType.name() + " keyframe";
        } else {
            undo.add(new EditorStateHistoryAction.RemoveKeyframe(track.keyframeType, trackIndex, tick));
            description = "Added " + track.keyframeType.name() + " keyframe";
        }
        redo.add(new EditorStateHistoryAction.SetKeyframe(track.keyframeType, trackIndex, tick, keyframe.copy()));

        this.push(new EditorStateHistoryEntry(undo, redo, description));
    }

    @Nullable
    public Camera getAudioCamera() {
        if (this.audioSourceEntity == null) {
            return null;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }

        Entity sourceEntity = level.getEntities().get(this.audioSourceEntity);
        if (sourceEntity == null) {
            return null;
        }

        Camera dummyCamera = new Camera();
        dummyCamera.eyeHeight = sourceEntity.getEyeHeight();
        dummyCamera.setup(level, sourceEntity, false, false, 1.0f);
        return dummyCamera;
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
        Set<KeyframeType<?>> applied = new HashSet<>();
        for (KeyframeTrack keyframeTrack : this.keyframeTracks) {
            // Ignore lines that are disabled
            if (!keyframeTrack.enabled) {
                continue;
            }

            KeyframeType<?> baseType = keyframeTrack.keyframeType == CameraOrbitKeyframeType.INSTANCE ? CameraKeyframeType.INSTANCE : keyframeTrack.keyframeType;

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
