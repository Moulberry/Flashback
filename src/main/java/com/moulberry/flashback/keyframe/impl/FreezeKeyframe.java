package com.moulberry.flashback.keyframe.impl;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeFreeze;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.FreezeKeyframeType;
import com.moulberry.flashback.keyframe.types.SpeedKeyframeType;
import com.moulberry.flashback.spline.CatmullRom;
import imgui.ImGui;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Consumer;

public class FreezeKeyframe extends Keyframe {

    private boolean frozen;
    private int frozenDelay;

    public FreezeKeyframe(boolean frozen, int frozenDelay) {
        this(frozen, frozenDelay, InterpolationType.HOLD);
    }

    public FreezeKeyframe(boolean frozen, int frozenDelay, InterpolationType interpolationType) {
        this.frozen = frozen;
        this.frozenDelay = frozenDelay;
        this.interpolationType(interpolationType);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return FreezeKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return new FreezeKeyframe(this.frozen, this.frozenDelay, this.interpolationType());
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        ImGui.setNextItemWidth(160);
        if (ImGui.checkbox("Frozen", this.frozen)) {
            boolean newFrozen = !this.frozen;
            update.accept(keyframe -> ((FreezeKeyframe)keyframe).frozen = newFrozen);
        }
        int[] delay = new int[]{this.frozenDelay};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Delay", delay, 0, 10)) {
            delay[0] = Math.max(0, Math.min(10, delay[0]));
            update.accept(keyframe -> ((FreezeKeyframe)keyframe).frozenDelay = delay[0]);
        }
        ImGuiHelper.tooltip("Smooth freeze delay (in ticks)\nSetting this will make the game gradually slow down over the specified delay until it comes to a freeze");
    }

    @Override
    public KeyframeChange createChange() {
        return new KeyframeChangeFreeze(this.frozen, Math.max(0, Math.min(10, this.frozenDelay)));
    }

    @Override
    public KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount) {
        return this.createChange();
    }

    @Override
    public KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float amount) {
        return this.createChange();
    }

    public static class TypeAdapter implements JsonSerializer<FreezeKeyframe>, JsonDeserializer<FreezeKeyframe> {
        @Override
        public FreezeKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            boolean freeze = jsonObject.get("freeze").getAsBoolean();
            int freezeDelay = jsonObject.has("freezeDelay") ? jsonObject.get("freezeDelay").getAsInt() : 0;
            InterpolationType interpolationType = context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            return new FreezeKeyframe(freeze, freezeDelay, interpolationType);
        }

        @Override
        public JsonElement serialize(FreezeKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("freeze", src.frozen);
            jsonObject.addProperty("freezeDelay", src.frozenDelay);
            jsonObject.addProperty("type", "freeze");
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType()));
            return jsonObject;
        }
    }
}
