package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangePlayAudio;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.keyframe.impl.AudioKeyframe;
import imgui.flashback.ImGui;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public class AudioKeyframeType implements KeyframeType<AudioKeyframe> {

    public static AudioKeyframeType INSTANCE = new AudioKeyframeType();

    private AudioKeyframeType() {
    }

    @Override
    public boolean supportsHandler(KeyframeHandler handler) {
        return MinecraftKeyframeHandler.class.isAssignableFrom(handler.getClass());
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangePlayAudio.class;
    }

    @Override
    public @Nullable String icon() {
        return "\ue3a1";
    }

    @Override
    public String name() {
        return I18n.get("flashback.keyframe.audio_track");
    }

    @Override
    public String id() {
        return "AUDIO";
    }

    @Override
    public boolean allowChangingInterpolationType() {
        return false;
    }

    @Override
    public boolean allowApplyingDuplicateKeyframeChanges() {
        return true;
    }

    @Override
    public boolean cullKeyframesInTimelineToTheLeft() {
        return false;
    }

    @Override
    public boolean hasCustomKeyframeChangeCalculation() {
        return true;
    }

    @Override
    public KeyframeChange customKeyframeChange(TreeMap<Integer, Keyframe> keyframes, float tick) {
        Map.Entry<Integer, Keyframe> entry = keyframes.floorEntry((int) tick);
        if (entry == null) {
            return null;
        }

        float delta = tick - entry.getKey();

        AudioKeyframe audioKeyframe = (AudioKeyframe) entry.getValue();
        return audioKeyframe.createAudioChange(entry.getKey(), delta / 20.0f);
    }

    @Override
    public @Nullable AudioKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<AudioKeyframe> createPopup() {
        CompletableFuture<String> pathFuture = AsyncFileDialogs.openFileDialog(FabricLoader.getInstance().getGameDir().toString(),
                "Audio Files", "mp3", "ogg", "wav", "aiff", "au", "flac", "opus");

        return () -> {
            if (!pathFuture.isDone()) {
                return null;
            }
            String pathStr = pathFuture.join();
            if (pathStr == null) {
                ImGui.closeCurrentPopup();
                return null;
            } else {
                Path path = Path.of(pathStr);
                return new AudioKeyframe(path);
            }
        };
    }
}
