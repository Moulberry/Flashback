package com.moulberry.flashback.keyframe.impl;

import com.google.gson.*;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangePlayAudio;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.AudioKeyframeType;
import com.moulberry.flashback.sound.FlashbackAudioBuffer;
import com.moulberry.flashback.sound.FlashbackAudioManager;
import imgui.ImDrawList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public class AudioKeyframe extends Keyframe {

    public Path path;
    private FlashbackAudioBuffer audioBuffer = null;

    public AudioKeyframe(Path path) {
        this.path = path;
        this.interpolationType(InterpolationType.LINEAR);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return AudioKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return new AudioKeyframe(this.path);
    }

    @Override
    public InterpolationType interpolationType() {
        return InterpolationType.LINEAR;
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
    }

    @Override
    public KeyframeChange createChange() {
        throw new UnsupportedOperationException();
    }

    @Override
    public KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount) {
        throw new UnsupportedOperationException();
    }

    @Override
    public KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float amount) {
        throw new UnsupportedOperationException();
    }

    private void ensureAudioBufferLoaded() {
        if (this.audioBuffer == null) {
            this.audioBuffer = FlashbackAudioManager.getBuffer(this.path);
        }
    }

    public @Nullable KeyframeChangePlayAudio createAudioChange(int startTick, float seconds) {
        this.ensureAudioBufferLoaded();
        if (this.audioBuffer == FlashbackAudioBuffer.EMPTY) {
            return null;
        }
        if (seconds > this.audioBuffer.durationInSeconds()) {
            return null;
        }
        return new KeyframeChangePlayAudio(this.audioBuffer, startTick, seconds);
    }

    @Override
    public float getCustomWidthInTicks() {
        this.ensureAudioBufferLoaded();
        if (this.audioBuffer == FlashbackAudioBuffer.EMPTY) {
            return -1;
        }
        return this.audioBuffer.durationInSeconds() * 20.0f;
    }

    @Override
    public void drawOnTimeline(ImDrawList drawList, int keyframeSize, float x, float y, int colour,
                               float timelineScale, float minTimelineX, float maxTimelineX, int tick, TreeMap<Integer, Keyframe> keyframeTimes) {
        this.ensureAudioBufferLoaded();

        int alpha = colour & 0xFF000000;

        if (this.audioBuffer == FlashbackAudioBuffer.EMPTY) {
            drawList.addRectFilled(x - keyframeSize, y - keyframeSize, x + keyframeSize, y + keyframeSize, alpha | 0x155FFF);
            drawList.addText(x + keyframeSize + 4, y - keyframeSize, 0xFF155FFF, "Error loading audio");
            return;
        }

        float durationInTicks = this.audioBuffer.durationInSeconds() * 20.0f;
        int waveformLength = (int)(durationInTicks / timelineScale);
        int drawLength = waveformLength;

        var next = keyframeTimes.ceilingEntry(tick + 1);
        if (next != null) {
            int nextTick = next.getKey();
            drawLength = Math.min(drawLength, (int)((nextTick - tick) / timelineScale));
        }

        int minSample = Math.max(0, (int)(minTimelineX - x));
        int maxSample = Math.min(drawLength, (int)(maxTimelineX - x));

        byte[] waveform = this.audioBuffer.getAveragedWaveform(waveformLength);
        drawList.addRectFilled(x, y - keyframeSize, x + drawLength, y + keyframeSize, alpha);
        drawList.addRect(x, y - keyframeSize - 1, x + drawLength, y + keyframeSize + 1, colour);
        for (int i = minSample; i < maxSample; i++) {
            float max = y + keyframeSize;
            float min = max - 2 * keyframeSize * ((int)waveform[i] - (int) Byte.MIN_VALUE)/255f;
            drawList.addRectFilled(x+i, min, x+i+1, max, alpha | 0xE37D77);
        }
    }

    public static class TypeAdapter implements JsonSerializer<AudioKeyframe>, JsonDeserializer<AudioKeyframe> {
        @Override
        public AudioKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            String path = jsonObject.get("path").getAsString();
            return new AudioKeyframe(Path.of(path));
        }

        @Override
        public JsonElement serialize(AudioKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("path", src.path.toString());
            jsonObject.addProperty("type", "audio");
            return jsonObject;
        }
    }
}
