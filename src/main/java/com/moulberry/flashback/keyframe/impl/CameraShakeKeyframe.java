package com.moulberry.flashback.keyframe.impl;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.CameraShakeKeyframeType;
import com.moulberry.flashback.spline.CatmullRom;

import java.lang.reflect.Type;

public class CameraShakeKeyframe extends Keyframe {

    private final float frequencyX;
    private final float amplitudeX;
    private final float frequencyY;
    private final float amplitudeY;

    public CameraShakeKeyframe(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY) {
        this(frequencyX, amplitudeX, frequencyY, amplitudeY, InterpolationType.DEFAULT);
    }

    public CameraShakeKeyframe(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY, InterpolationType interpolationType) {
        this.frequencyX = frequencyX;
        this.amplitudeX = amplitudeX;
        this.frequencyY = frequencyY;
        this.amplitudeY = amplitudeY;
        this.interpolationType(interpolationType);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return CameraShakeKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return new CameraShakeKeyframe(this.frequencyX, this.amplitudeX, this.frequencyY, this.amplitudeY, this.interpolationType());
    }

    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        keyframeHandler.applyCameraShake(this.frequencyX, this.amplitudeX, this.frequencyY, this.amplitudeY);
    }

    @Override
    public void applyInterpolated(KeyframeHandler keyframeHandler, Keyframe otherGeneric, float amount) {
        if (!(otherGeneric instanceof CameraShakeKeyframe other)) {
            this.apply(keyframeHandler);
            return;
        }

        float frequencyX = Interpolation.linear(this.frequencyX, other.frequencyX, amount);
        float amplitudeX = Interpolation.linear(this.amplitudeX, other.amplitudeX, amount);
        float frequencyY = Interpolation.linear(this.frequencyY, other.frequencyY, amount);
        float amplitudeY = Interpolation.linear(this.amplitudeY, other.amplitudeY, amount);

        keyframeHandler.applyCameraShake(frequencyX, amplitudeX, frequencyY, amplitudeY);
    }

    @Override
    public void applyInterpolatedSmooth(KeyframeHandler keyframeHandler, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount, float lerpAmount, boolean lerpFromRight) {
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        float frequencyX = CatmullRom.value(this.frequencyX, ((CameraShakeKeyframe)p1).frequencyX,
            ((CameraShakeKeyframe)p2).frequencyX, ((CameraShakeKeyframe)p3).frequencyX, time1, time2, time3, amount);
        float amplitudeX = CatmullRom.value(this.amplitudeX, ((CameraShakeKeyframe)p1).amplitudeX,
            ((CameraShakeKeyframe)p2).amplitudeX, ((CameraShakeKeyframe)p3).amplitudeX, time1, time2, time3, amount);
        float frequencyY = CatmullRom.value(this.frequencyY, ((CameraShakeKeyframe)p1).frequencyY,
            ((CameraShakeKeyframe)p2).frequencyY, ((CameraShakeKeyframe)p3).frequencyY, time1, time2, time3, amount);
        float amplitudeY = CatmullRom.value(this.amplitudeY, ((CameraShakeKeyframe)p1).amplitudeY,
            ((CameraShakeKeyframe)p2).amplitudeY, ((CameraShakeKeyframe)p3).amplitudeY, time1, time2, time3, amount);

        if (lerpAmount >= 0) {
            float linearFrequencyX = Interpolation.linear(((CameraShakeKeyframe)p1).frequencyX, ((CameraShakeKeyframe)p2).frequencyX, lerpAmount);
            float linearAmplitudeX = Interpolation.linear(((CameraShakeKeyframe)p1).amplitudeX, ((CameraShakeKeyframe)p2).amplitudeX, lerpAmount);
            float linearFrequencyY = Interpolation.linear(((CameraShakeKeyframe)p1).frequencyY, ((CameraShakeKeyframe)p2).frequencyY, lerpAmount);
            float linearAmplitudeY = Interpolation.linear(((CameraShakeKeyframe)p1).amplitudeY, ((CameraShakeKeyframe)p2).amplitudeY, lerpAmount);

            if (lerpFromRight) {
                frequencyX = Interpolation.linear(frequencyX, linearFrequencyX, amount);
                amplitudeX = Interpolation.linear(amplitudeX, linearAmplitudeX, amount);
                frequencyY = Interpolation.linear(frequencyY, linearFrequencyY, amount);
                amplitudeY = Interpolation.linear(amplitudeY, linearAmplitudeY, amount);
            } else {
                frequencyX = Interpolation.linear(linearFrequencyX, frequencyX, amount);
                amplitudeX = Interpolation.linear(linearAmplitudeX, amplitudeX, amount);
                frequencyY = Interpolation.linear(linearFrequencyY, frequencyY, amount);
                amplitudeY = Interpolation.linear(linearAmplitudeY, amplitudeY, amount);
            }
        }

        keyframeHandler.applyCameraShake(frequencyX, amplitudeX, frequencyY, amplitudeY);
    }

    public static class TypeAdapter implements JsonSerializer<CameraShakeKeyframe>, JsonDeserializer<CameraShakeKeyframe> {
        @Override
        public CameraShakeKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            float frequencyX = jsonObject.get("frequencyX").getAsFloat();
            float amplitudeX = jsonObject.get("amplitudeX").getAsFloat();
            float frequencyY = jsonObject.get("frequencyY").getAsFloat();
            float amplitudeY = jsonObject.get("amplitudeY").getAsFloat();
            InterpolationType interpolationType = context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            return new CameraShakeKeyframe(frequencyX, amplitudeX, frequencyY, amplitudeY, interpolationType);
        }

        @Override
        public JsonElement serialize(CameraShakeKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("frequencyX", src.frequencyX);
            jsonObject.addProperty("amplitudeX", src.amplitudeX);
            jsonObject.addProperty("frequencyY", src.frequencyY);
            jsonObject.addProperty("amplitudeY", src.amplitudeY);
            jsonObject.addProperty("type", "camera_shake");
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType()));
            return jsonObject;
        }
    }

}
