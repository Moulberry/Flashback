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
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;

import java.lang.reflect.Type;
import java.util.function.Consumer;

public class CameraShakeKeyframe extends Keyframe {

    private float frequencyX;
    private float amplitudeX;
    private float frequencyY;
    private float amplitudeY;
    private boolean splitParams;

    public CameraShakeKeyframe(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY, boolean splitParams) {
        this(frequencyX, amplitudeX, frequencyY, amplitudeY, splitParams, InterpolationType.DEFAULT);
    }

    public CameraShakeKeyframe(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY, boolean splitParams, InterpolationType interpolationType) {
        this.frequencyX = frequencyX;
        this.amplitudeX = amplitudeX;
        this.frequencyY = frequencyY;
        this.amplitudeY = amplitudeY;
        this.splitParams = splitParams;
        this.interpolationType(interpolationType);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return CameraShakeKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return new CameraShakeKeyframe(this.frequencyX, this.amplitudeX, this.frequencyY, this.amplitudeY, this.splitParams, this.interpolationType());
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        ImGui.setNextItemWidth(160);
        if (ImGui.checkbox("Split Y/X", this.splitParams)) {
            boolean splitParams = this.splitParams;
            update.accept(keyframe -> {
                CameraShakeKeyframe cameraShakeKeyframe = (CameraShakeKeyframe) keyframe;
                cameraShakeKeyframe.splitParams = !splitParams;
                cameraShakeKeyframe.frequencyY = cameraShakeKeyframe.frequencyX;
                cameraShakeKeyframe.amplitudeY = cameraShakeKeyframe.amplitudeX;
            });
        }

        if (this.splitParams) {
            float[] input = new float[]{this.frequencyX};
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderFloat("Frequency X", input, 0.1f, 10.0f, "%.1f") && input[0] != this.frequencyX) {
                update.accept(keyframe -> {
                    ((CameraShakeKeyframe)keyframe).splitParams = true;
                    ((CameraShakeKeyframe)keyframe).frequencyX = input[0];
                });
            }

            input[0] = this.amplitudeX;
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderFloat("Amplitude X", input, 0.0f, 10.0f, "%.1f") && input[0] != this.amplitudeX) {
                update.accept(keyframe -> {
                    ((CameraShakeKeyframe)keyframe).splitParams = true;
                    ((CameraShakeKeyframe)keyframe).amplitudeX = input[0];
                });
            }

            input[0] = this.frequencyY;
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderFloat("Frequency Y", input, 0.1f, 10.0f, "%.1f") && input[0] != this.frequencyY) {
                update.accept(keyframe -> {
                    ((CameraShakeKeyframe)keyframe).splitParams = true;
                    ((CameraShakeKeyframe)keyframe).frequencyY = input[0];
                });
            }

            input[0] = this.amplitudeY;
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderFloat("Amplitude Y", input, 0.0f, 10.0f, "%.1f") && input[0] != this.amplitudeY) {
                update.accept(keyframe -> {
                    ((CameraShakeKeyframe)keyframe).splitParams = true;
                    ((CameraShakeKeyframe)keyframe).amplitudeY = input[0];
                });
            }
        } else {
            float[] input = new float[]{this.frequencyX};
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderFloat("Frequency", input, 0.1f, 10.0f, "%.1f") && input[0] != this.frequencyX) {
                update.accept(keyframe -> {
                    ((CameraShakeKeyframe)keyframe).frequencyX = input[0];
                    ((CameraShakeKeyframe)keyframe).frequencyY = input[0];
                });
            }

            input[0] = this.amplitudeX;
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderFloat("Amplitude", input, 0.0f, 10.0f, "%.1f") && input[0] != this.amplitudeX) {
                update.accept(keyframe -> {
                    ((CameraShakeKeyframe)keyframe).amplitudeX = input[0];
                    ((CameraShakeKeyframe)keyframe).amplitudeY = input[0];
                });
            }
        }
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
            boolean splitParams = jsonObject.has("splitParams") && jsonObject.get("splitParams").getAsBoolean();
            InterpolationType interpolationType = context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            return new CameraShakeKeyframe(frequencyX, amplitudeX, frequencyY, amplitudeY, splitParams, interpolationType);
        }

        @Override
        public JsonElement serialize(CameraShakeKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("frequencyX", src.frequencyX);
            jsonObject.addProperty("amplitudeX", src.amplitudeX);
            jsonObject.addProperty("frequencyY", src.frequencyY);
            jsonObject.addProperty("amplitudeY", src.amplitudeY);
            if (src.splitParams) {
                jsonObject.addProperty("splitParams", true);
            }
            jsonObject.addProperty("type", "camera_shake");
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType()));
            return jsonObject;
        }
    }

}
