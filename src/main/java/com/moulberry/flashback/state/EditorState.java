package com.moulberry.flashback.state;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.FilePlayerSkin;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
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
import net.minecraft.client.multiplayer.PlayerInfo;
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

    public final ReplayVisuals replayVisuals = new ReplayVisuals();

    public final List<EditorScene> scenes;
    public int sceneIndex = 0;

    public double zoomMin = 0.0;
    public double zoomMax = 1.0;

    public UUID audioSourceEntity = null;
    public Set<UUID> hideDuringExport = new HashSet<>();
    public Set<UUID> hideNametags = new HashSet<>();
    public Map<UUID, GameProfile> skinOverride = new HashMap<>();
    public Map<UUID, FilePlayerSkin> skinOverrideFromFile = new HashMap<>();
    public Map<UUID, String> nameOverride = new HashMap<>();
    public Set<UUID> hideTeamPrefix = new HashSet<>();
    public Set<UUID> hideTeamSuffix = new HashSet<>();
    public Set<String> filteredEntities = new HashSet<>();
    public Set<String> filteredParticles = new HashSet<>();

    public EditorState() {
        this.scenes = new ArrayList<>();
        this.scenes.add(new EditorScene("Scene 1"));
    }

    public EditorScene currentScene() {
        if (this.scenes.isEmpty()) {
            this.scenes.add(new EditorScene("Scene 1"));
        }
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
        Set<Class<? extends KeyframeChange>> applied = new HashSet<>();

        Map<Class<? extends KeyframeChange>, KeyframeTrack> maybeApplyLastTick = new HashMap<>();

        for (KeyframeTrack keyframeTrack : this.currentScene().keyframeTracks) {
            // Ignore lines that are disabled
            if (!keyframeTrack.enabled) {
                continue;
            }

            Class<? extends KeyframeChange> keyframeChangeType = keyframeTrack.keyframeType.keyframeChangeType();

            // Already applied a keyframe of this type earlier, skip
            if (applied.contains(keyframeChangeType)) {
                continue;
            }

            if (!keyframeTrack.keyframeType.supportsHandler(keyframeHandler)) {
                continue;
            }

            // Try to apply keyframes, mark applied if successful

            KeyframeChange change = keyframeTrack.createKeyframeChange(tick);
            if (change == null) {
                if (keyframeHandler.alwaysApplyLastKeyframe() && !keyframeTrack.keyframesByTick.isEmpty()) {
                    KeyframeTrack oldTrack = maybeApplyLastTick.get(keyframeChangeType);
                    if (oldTrack == null || keyframeTrack.keyframesByTick.lastKey() > oldTrack.keyframesByTick.lastKey()) {
                        maybeApplyLastTick.put(keyframeChangeType, keyframeTrack);
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
                KeyframeChange change = keyframeTrack.createKeyframeChange(keyframeTrack.keyframesByTick.lastKey());

                if (change == null) {
                    continue;
                }

                if (change.getClass() != entry.getKey()) {
                    throw new IllegalStateException("Expected " + entry.getKey() + ", got " + change.getClass() + ". Caused by: " + keyframeTrack.keyframeType.id());
                }

                change.apply(keyframeHandler);
            }
        }
    }

}
