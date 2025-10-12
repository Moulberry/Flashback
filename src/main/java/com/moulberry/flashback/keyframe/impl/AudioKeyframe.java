package com.moulberry.flashback.keyframe.impl;

import com.google.gson.*;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.TimelapseKeyframeType;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.client.resources.language.I18n;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Consumer;

public class AudioKeyframe extends Keyframe {

    public int ticks;

    private final ImString timelapseKeyframeInput;

    public AudioKeyframe(int ticks) {
        this.ticks = ticks;
        this.timelapseKeyframeInput = ImGuiHelper.createResizableImString(Utils.timeInTicksToString(this.ticks));
        this.timelapseKeyframeInput.inputData.allowedChars = "0123456789tsmh.";
        this.interpolationType(InterpolationType.LINEAR);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return TimelapseKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return new AudioKeyframe(this.ticks);
    }

    @Override
    public InterpolationType interpolationType() {
        return InterpolationType.LINEAR;
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        ImGui.setNextItemWidth(160);
        ImGui.inputText(I18n.get("flashback.time"), timelapseKeyframeInput);
        if (ImGui.isItemDeactivatedAfterEdit()) {
            int ticks = Utils.stringToTime(timelapseKeyframeInput.get());
            if (this.ticks != ticks) {
                update.accept(keyframe -> ((AudioKeyframe)keyframe).ticks = ticks);
            }
        }
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

    public static class TypeAdapter implements JsonSerializer<AudioKeyframe>, JsonDeserializer<AudioKeyframe> {
        @Override
        public AudioKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            int ticks = jsonObject.get("ticks").getAsInt();
            return new AudioKeyframe(ticks);
        }

        @Override
        public JsonElement serialize(AudioKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("ticks", src.ticks);
            jsonObject.addProperty("type", "timelapse");
            return jsonObject;
        }
    }
}
