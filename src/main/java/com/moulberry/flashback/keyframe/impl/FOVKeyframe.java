package com.moulberry.flashback.keyframe.impl;

import com.google.gson.*;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.spline.CatmullRom;
import imgui.ImGui;

import java.lang.reflect.Type;
import java.util.function.Consumer;

public class FOVKeyframe extends Keyframe {

    public float fov;

    public FOVKeyframe(float fov) {
        this(fov, InterpolationType.DEFAULT);
    }

    public FOVKeyframe(float fov, InterpolationType interpolationType) {
        this.fov = fov;
        this.interpolationType(interpolationType);
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
    public void apply(KeyframeHandler keyframeHandler) {
        keyframeHandler.applyFov(this.fov);
    }


    @Override
    public void applyInterpolated(KeyframeHandler keyframeHandler, Keyframe otherGeneric, float amount) {
        if (!(otherGeneric instanceof FOVKeyframe other)) {
            this.apply(keyframeHandler);
            return;
        }

        float thisFocalLength = Utils.fovToFocalLength(this.fov);
        float otherFocalLength = Utils.fovToFocalLength(other.fov);
        float focalLength = Interpolation.linear(thisFocalLength, otherFocalLength, amount);
        keyframeHandler.applyFov(Utils.focalLengthToFov(focalLength));
    }

    @Override
    public void applyInterpolatedSmooth(KeyframeHandler keyframeHandler, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount, float lerpAmount, boolean lerpFromRight) {
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        float f0 = Utils.fovToFocalLength(this.fov);
        float f1 = Utils.fovToFocalLength(((FOVKeyframe)p1).fov);
        float f2 = Utils.fovToFocalLength(((FOVKeyframe)p2).fov);
        float f3 = Utils.fovToFocalLength(((FOVKeyframe)p3).fov);

        float focalLength = CatmullRom.value(f0, f1, f2, f3, time1, time2, time3, amount);

        if (lerpAmount >= 0) {
            float linearFocalLength = Interpolation.linear(f1, f2, lerpAmount);

            if (lerpFromRight) {
                focalLength = Interpolation.linear(focalLength, linearFocalLength, amount);
            } else {
                focalLength = Interpolation.linear(linearFocalLength, focalLength, amount);
            }
        }

        keyframeHandler.applyFov(Utils.focalLengthToFov(focalLength));
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
