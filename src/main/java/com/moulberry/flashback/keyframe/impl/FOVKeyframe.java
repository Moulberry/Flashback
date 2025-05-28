package com.moulberry.flashback.keyframe.impl;

import com.google.common.collect.Maps;
import com.google.gson.*;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeFov;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.FOVKeyframeType;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.spline.Hermite;
import imgui.ImGui;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Consumer;

public class FOVKeyframe extends Keyframe {

    public float fov;

    public FOVKeyframe(float fov) {
        this(fov, InterpolationType.getDefault());
    }

    public FOVKeyframe(float fov, InterpolationType interpolationType) {
        this.fov = fov;
        this.interpolationType(interpolationType);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return FOVKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return new FOVKeyframe(this.fov, this.interpolationType());
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        ImGui.setNextItemWidth(160);
        float[] input = new float[]{this.fov};
        if (ImGui.sliderFloat("FOV", input, 1.0f, 110.0f, "%.1f")) {
            if (this.fov != input[0]) {
                update.accept(keyframe -> ((FOVKeyframe)keyframe).fov = input[0]);
            }
        }
    }

    @Override
    public KeyframeChange createChange() {
        return new KeyframeChangeFov(this.fov);
    }

    @Override
    public KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount) {
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        float f0 = Utils.fovToFocalLength(this.fov);
        float f1 = Utils.fovToFocalLength(((FOVKeyframe)p1).fov);
        float f2 = Utils.fovToFocalLength(((FOVKeyframe)p2).fov);
        float f3 = Utils.fovToFocalLength(((FOVKeyframe)p3).fov);

        float focalLength = CatmullRom.value(f0, f1, f2, f3, time1, time2, time3, amount);

        return new KeyframeChangeFov(Utils.focalLengthToFov(focalLength));
    }

    @Override
    public KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float amount) {
        float focalLength = (float) Hermite.value(Maps.transformValues(keyframes, k -> (double) Utils.fovToFocalLength(((FOVKeyframe)k).fov)), amount);
        return new KeyframeChangeFov(Utils.focalLengthToFov(focalLength));
    }

    public static class TypeAdapter implements JsonSerializer<FOVKeyframe>, JsonDeserializer<FOVKeyframe> {
        @Override
        public FOVKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            float fov = jsonObject.get("fov").getAsFloat();
            InterpolationType interpolationType = context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            return new FOVKeyframe(fov, interpolationType);
        }

        @Override
        public JsonElement serialize(FOVKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("fov", src.fov);
            jsonObject.addProperty("type", "fov");
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType()));
            return jsonObject;
        }
    }

}
