package com.moulberry.flashback.state;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.FilePlayerSkin;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.combo_options.GlowingOverride;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTickrate;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.visuals.ReplayVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

public class EditorState {

    volatile transient boolean dirty = false;
    public volatile transient int modCount = ThreadLocalRandom.current().nextInt();
    private volatile transient int lastRealTimeMappingModCount = this.modCount;
    private volatile transient RealTimeMapping realTimeMapping = null;

    public final ReplayVisuals replayVisuals = new ReplayVisuals();

    private final StampedLock sceneLock = new StampedLock();
    private final List<EditorScene> scenes;
    private int sceneIndex = 0;

    public double zoomMin = 0.0;
    public double zoomMax = 1.0;

    public UUID audioSourceEntity = null;
    public Set<UUID> hideDuringExport = new HashSet<>();
    public Set<UUID> hideNametags = new HashSet<>();
    public Map<UUID, GameProfile> skinOverride = new HashMap<>();
    public Map<UUID, FilePlayerSkin> skinOverrideFromFile = new HashMap<>();
    public Map<UUID, String> nameOverride = new HashMap<>();
    public Map<UUID, GlowingOverride> glowingOverride = new HashMap<>();
    public Set<UUID> hideTeamPrefix = new HashSet<>();
    public Set<UUID> hideTeamSuffix = new HashSet<>();
    public Set<UUID> hideBelowName = new HashSet<>();
    public Set<UUID> hideCape = new HashSet<>();
    public Set<String> filteredEntities = new HashSet<>();
    public Set<String> filteredParticles = new HashSet<>();

    public EditorState() {
        this.scenes = new ArrayList<>();
        this.scenes.add(new EditorScene("Scene 1"));

        FlashbackConfig config = Flashback.getConfig();
        if (config.enableOverrideFovByDefault) {
            this.replayVisuals.overrideFov = true;
            if (this.replayVisuals.overrideFovAmount < 0) {
                this.replayVisuals.overrideFovAmount = config.defaultOverrideFov;
            }
        }
    }

    @ApiStatus.Internal
    public long acquireRead() {
        return this.sceneLock.readLock();
    }

    @ApiStatus.Internal
    public long acquireWrite() {
        return this.sceneLock.writeLock();
    }

    @ApiStatus.Internal
    public void release(long stamp) {
        this.sceneLock.unlock(stamp);
    }

    @ApiStatus.Internal
    public List<EditorScene> getScenes(long stamp) {
        if (!this.sceneLock.validate(stamp)) {
            throw new IllegalStateException("Invalid stamp!");
        }
        return this.scenes;
    }

    public int getSceneIndex() {
        return this.sceneIndex;
    }

    @ApiStatus.Internal
    public void setSceneIndex(int sceneIndex, long stamp) {
        if (!this.sceneLock.validate(stamp)) {
            throw new IllegalStateException("Invalid stamp!");
        }
        this.sceneIndex = sceneIndex;
    }

    @ApiStatus.Internal
    public EditorScene getCurrentScene(long stamp) {
        if (!this.sceneLock.validate(stamp)) {
            throw new IllegalStateException("Invalid stamp!");
        }
        return this.scenes.get(this.sceneIndex);
    }

    private EditorScene currentScene() {
        return this.scenes.get(this.sceneIndex);
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

    public void markDirty() {
        this.dirty = true;
        this.modCount += 1;
    }

    public void save(Path path) {
        this.dirty = false;

        String serialized = FlashbackGson.COMPRESSED.toJson(this, EditorState.class);

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
            return FlashbackGson.COMPRESSED.fromJson(serialized, EditorState.class);
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

    public EditorState copyWithoutKeyframes() {
        String serialized = FlashbackGson.COMPRESSED.toJson(this, EditorState.class);
        EditorState editorState = FlashbackGson.COMPRESSED.fromJson(serialized, EditorState.class);
        for (EditorScene scene : editorState.scenes) {
            scene.keyframeTracks.clear();
        }
        return editorState;
    }

    public void applyKeyframes(KeyframeHandler keyframeHandler, float tick) {
        Set<Class<? extends KeyframeChange>> applied = new HashSet<>();
        Map<Class<? extends KeyframeChange>, KeyframeTrack> maybeApplyLastTick = new HashMap<>();

        updateRealtimeMappingsIfNeeded();

        long stamp = this.sceneLock.readLock();
        try {
            for (KeyframeTrack keyframeTrack : this.currentScene().keyframeTracks) {
                // Ignore lines that are disabled
                if (!keyframeTrack.enabled) {
                    continue;
                }

                Class<? extends KeyframeChange> keyframeChangeType = keyframeTrack.keyframeType.keyframeChangeType();

                // Already applied a keyframe of this type earlier, skip
                if (keyframeChangeType == null || applied.contains(keyframeChangeType)) {
                    continue;
                }

                if (!keyframeTrack.keyframeType.supportsHandler(keyframeHandler)) {
                    continue;
                }

                // Try to apply keyframes, mark applied if successful

                KeyframeChange change = keyframeTrack.createKeyframeChange(tick, this.realTimeMapping);
                if (change == null) {
                    if (keyframeHandler.alwaysApplyLastKeyframe() && !keyframeTrack.keyframeType.neverApplyLastKeyframe() && !keyframeTrack.keyframesByTick.isEmpty()) {
                        if (keyframeTrack.keyframesByTick.lastKey() <= tick) {
                            KeyframeTrack oldTrack = maybeApplyLastTick.get(keyframeChangeType);
                            if (oldTrack == null || keyframeTrack.keyframesByTick.lastKey() > oldTrack.keyframesByTick.lastKey()) {
                                maybeApplyLastTick.put(keyframeChangeType, keyframeTrack);
                            }
                        }
                    }
                    continue;
                }

                if (change.getClass() != keyframeChangeType) {
                    throw new IllegalStateException("Expected " + keyframeChangeType + ", got " + change.getClass() + ". Caused by: " + keyframeTrack.keyframeType.id());
                }

                applied.add(keyframeChangeType);
                maybeApplyLastTick.remove(keyframeChangeType);
                change.apply(keyframeHandler);
            }

            if (keyframeHandler.alwaysApplyLastKeyframe() && !maybeApplyLastTick.isEmpty()) {
                for (Map.Entry<Class<? extends KeyframeChange>, KeyframeTrack> entry : maybeApplyLastTick.entrySet()) {
                    KeyframeTrack keyframeTrack = entry.getValue();
                    KeyframeChange change = keyframeTrack.createKeyframeChange(keyframeTrack.keyframesByTick.lastKey(), this.realTimeMapping);

                    if (change == null) {
                        continue;
                    }

                    if (change.getClass() != entry.getKey()) {
                        throw new IllegalStateException("Expected " + entry.getKey() + ", got " + change.getClass() + ". Caused by: " + keyframeTrack.keyframeType.id());
                    }

                    change.apply(keyframeHandler);
                }
            }
        } finally {
            this.sceneLock.unlock(stamp);
        }
    }

    private void updateRealtimeMappingsIfNeeded() {
        long stamp = this.sceneLock.readLock();
        try {
            FlashbackConfig config = Flashback.getConfig();
            if (!config.useRealtimeInterpolation) {
                this.sceneLock.unlock(stamp);
                stamp = this.sceneLock.writeLock();

                this.lastRealTimeMappingModCount = this.modCount;
                this.realTimeMapping = null;
            } else if (this.realTimeMapping == null || this.lastRealTimeMappingModCount != this.modCount) {
                this.sceneLock.unlock(stamp);
                stamp = this.sceneLock.writeLock();

                if (this.realTimeMapping == null || this.lastRealTimeMappingModCount != this.modCount) {
                    this.calculateRealtimeMappings();
                }
            }
        } finally {
            this.sceneLock.unlock(stamp);
        }
    }

    private void calculateRealtimeMappings() {
        this.lastRealTimeMappingModCount = this.modCount;
        this.realTimeMapping = new RealTimeMapping();

        List<KeyframeTrack> applicableTracks = new ArrayList<>();
        int start = -1;
        int end = -1;
        int lastApplicableKeyframe = -1;

        for (KeyframeTrack keyframeTrack : this.currentScene().keyframeTracks) {
            // Ignore tracks that are disabled
            if (!keyframeTrack.enabled || keyframeTrack.keyframesByTick.isEmpty()) {
                continue;
            }

            // We only care about tickrate changes
            Class<? extends KeyframeChange> keyframeChangeType = keyframeTrack.keyframeType.keyframeChangeType();
            if (keyframeChangeType == null || !KeyframeChangeTickrate.class.isAssignableFrom(keyframeChangeType)) {
                continue;
            }

            applicableTracks.add(keyframeTrack);

            int trackStart = keyframeTrack.keyframesByTick.firstKey();
            int trackEnd = keyframeTrack.keyframesByTick.lastKey();

            if (start < 0 || trackStart < start) {
                start = trackStart;
            }
            if (end < 0 || trackEnd > end) {
                end = trackEnd;
            }

            if (!keyframeTrack.keyframeType.neverApplyLastKeyframe()) {
                if (lastApplicableKeyframe < 0 || trackEnd > lastApplicableKeyframe) {
                    lastApplicableKeyframe = trackEnd;
                }
            }
        }

        if (applicableTracks.isEmpty() || start < 0 || end < 0) {
            return;
        }

        float lastSpeed = Float.NaN;

        for (int tick = start; tick <= end; tick++) {
            for (KeyframeTrack keyframeTrack : applicableTracks) {
                KeyframeChange change = keyframeTrack.createKeyframeChange(tick, this.realTimeMapping);
                if (!(change instanceof KeyframeChangeTickrate changeTickrate)) {
                    continue;
                }

                float newSpeed = changeTickrate.tickrate() / 20.0f;
                if (newSpeed != lastSpeed) {
                    lastSpeed = newSpeed;
                    this.realTimeMapping.addMapping(tick, newSpeed);
                }
                break;
            }
        }

        // Check if the tick afterwards has a change, if it does then the last keyframe is probably a hold keyframe
        // So we can just apply that speed for the remainder
        for (KeyframeTrack keyframeTrack : applicableTracks) {
            KeyframeChange change = keyframeTrack.createKeyframeChange(end+1, this.realTimeMapping);
            if (!(change instanceof KeyframeChangeTickrate changeTickrate)) {
                continue;
            }

            float newSpeed = changeTickrate.tickrate() / 20.0f;
            if (newSpeed != lastSpeed) {
                this.realTimeMapping.addMapping(end+1, newSpeed);
            }
            return;
        }

        // We need to try applying the last keyframe for the remainder
        for (KeyframeTrack keyframeTrack : applicableTracks) {
            if (!keyframeTrack.keyframeType.neverApplyLastKeyframe() && keyframeTrack.keyframesByTick.lastKey() == lastApplicableKeyframe) {
                KeyframeChange change = keyframeTrack.createKeyframeChange(lastApplicableKeyframe, this.realTimeMapping);
                if (!(change instanceof KeyframeChangeTickrate changeTickrate)) {
                    break;
                }

                float newSpeed = changeTickrate.tickrate() / 20.0f;
                if (newSpeed != lastSpeed) {
                    this.realTimeMapping.addMapping(end+1, newSpeed);
                }
                return;
            }
        }

        // Failing to apply the last keyframe, we reset the speed to normal
        this.realTimeMapping.addMapping(end+1, 1.0f);
    }

    public void setExportTicks(int start, int end, int totalTicks) {
        long stamp = this.sceneLock.writeLock();
        try {
            this.currentScene().setExportTicks(start, end, totalTicks);
            this.markDirty();
        } finally {
            this.sceneLock.unlock(stamp);
        }
    }

    public record StartAndEnd(int start, int end){}

    public StartAndEnd getExportStartAndEnd() {
        int start = -1;
        int end = -1;

        long stamp = this.sceneLock.readLock();
        try {
            EditorScene scene = this.currentScene();
            if (scene != null && (scene.exportStartTicks >= 0 || scene.exportEndTicks >= 0)) {
                if (scene.exportStartTicks >= 0) {
                    start = scene.exportStartTicks;
                } else {
                    start = 0;
                }
                if (scene.exportEndTicks >= 0) {
                    end = scene.exportEndTicks;
                } else {
                    ReplayServer replayServer = Flashback.getReplayServer();
                    if (replayServer == null) {
                        end = start;
                    } else {
                        end = replayServer.getTotalReplayTicks();
                    }
                }
            }
        } finally {
            this.sceneLock.unlock(stamp);
        }

        return new StartAndEnd(start, end);
    }

    public StartAndEnd getFirstAndLastTicksInTracks() {
        int start = -1;
        int end = -1;

        long stamp = this.sceneLock.readLock();
        try {
            for (KeyframeTrack keyframeTrack : this.currentScene().keyframeTracks) {
                if (!keyframeTrack.enabled || keyframeTrack.keyframesByTick.isEmpty()) {
                    continue;
                }
                int min = keyframeTrack.keyframesByTick.firstKey();
                int max = keyframeTrack.keyframesByTick.lastKey();
                if (start == -1) {
                    start = min;
                } else {
                    start = Math.min(start, min);
                }
                if (end == -1) {
                    end = max;
                } else {
                    end = Math.max(end, max);
                }
            }
        } finally {
            this.sceneLock.unlock(stamp);
        }

        return new StartAndEnd(start, end);
    }

}
