package com.moulberry.flashback.keyframe.impl;

import com.google.common.collect.Maps;
import com.google.gson.*;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTickrate;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.SpeedKeyframeType;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.spline.Hermite;
import imgui.ImGui;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Consumer;

public class TickrateKeyframe extends Keyframe {

    private float tickrate;

    public TickrateKeyframe(float tickrate) {
        this(tickrate, InterpolationType.getDefault());
    }

    public TickrateKeyframe(float tickrate, InterpolationType interpolationType) {
        this.tickrate = tickrate;
        this.interpolationType(interpolationType);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return SpeedKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return new TickrateKeyframe(this.tickrate, this.interpolationType());
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        ImGui.setNextItemWidth(160);
        float[] input = new float[]{this.tickrate/20f};
        if (ImGui.sliderFloat("Speed", input, 0.1f, 10.0f)) {
            float tickrate = Math.max(0.01f, input[0]*20f);
            if (this.tickrate != tickrate) {
                update.accept(keyframe -> ((TickrateKeyframe)keyframe).tickrate = tickrate);
            }
        }
    }

    @Override
    public KeyframeChange createChange() {
        return new KeyframeChangeTickrate(this.tickrate);
    }

    @Override
    public KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount) {
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        float tickrate = CatmullRom.value(this.tickrate,
                ((TickrateKeyframe)p1).tickrate, ((TickrateKeyframe)p2).tickrate,
                ((TickrateKeyframe)p3).tickrate, time1, time2, time3, amount);

        return new KeyframeChangeTickrate(tickrate);
    }

    @Override
    public KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float amount) {
        float tickrate = (float) Hermite.value(Maps.transformValues(keyframes, k -> (double) ((TickrateKeyframe)k).tickrate), amount);
        return new KeyframeChangeTickrate(tickrate);
    }

    public static class TypeAdapter implements JsonSerializer<TickrateKeyframe>, JsonDeserializer<TickrateKeyframe> {
        @Override
        public TickrateKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            float tickrate = jsonObject.get("tickrate").getAsFloat();
            InterpolationType interpolationType = context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            return new TickrateKeyframe(tickrate, interpolationType);
        }

        @Override
        public JsonElement serialize(TickrateKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("tickrate", src.tickrate);
            jsonObject.addProperty("type", "tickrate");
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType()));
            return jsonObject;
        }
    }
}
