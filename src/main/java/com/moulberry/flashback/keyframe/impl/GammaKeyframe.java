package com.moulberry.flashback.keyframe.impl;

import com.google.common.collect.Maps;
import com.google.gson.*;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeGamma;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.GammaKeyframeType;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.spline.Hermite;
import imgui.ImGui;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Consumer;

public class GammaKeyframe extends Keyframe {

    public float gamma;

    public GammaKeyframe(float gamma) {
        this(gamma, InterpolationType.getDefault());
    }

    public GammaKeyframe(float gamma, InterpolationType interpolationType) {
        this.gamma = gamma;
        this.interpolationType(interpolationType);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return GammaKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return new GammaKeyframe(this.gamma, this.interpolationType());
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        ImGui.setNextItemWidth(160);
        float[] input = new float[]{this.gamma};
        if (ImGui.sliderFloat("Gamma", input, 0.0f, 1.0f)) {
            if (this.gamma != input[0]) {
                update.accept(keyframe -> ((GammaKeyframe) keyframe).gamma = input[0]);
            }
        }
    }

    @Override
    public KeyframeChange createChange() {
        return new KeyframeChangeGamma(this.gamma);
    }

    @Override
    public KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3,
                                                         float t0, float t1, float t2, float t3,
                                                         float amount) {
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        float g0 = this.gamma;
        float g1 = ((GammaKeyframe) p1).gamma;
        float g2 = ((GammaKeyframe) p2).gamma;
        float g3 = ((GammaKeyframe) p3).gamma;

        float gamma = CatmullRom.value(g0, g1, g2, g3, time1, time2, time3, amount);

        return new KeyframeChangeGamma(gamma);
    }

    @Override
    public KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes,
                                                          float amount) {
        float gamma = (float) Hermite.value(
                Maps.transformValues(keyframes, k -> (double) ((GammaKeyframe) k).gamma),
                amount);
        return new KeyframeChangeGamma(gamma);
    }

    public static class TypeAdapter implements JsonSerializer<GammaKeyframe>,
                                               JsonDeserializer<GammaKeyframe> {
        @Override
        public GammaKeyframe deserialize(JsonElement json,
                                         Type typeOfT,
                                         JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            float gamma = jsonObject.get("gamma").getAsFloat();
            InterpolationType interpolationType =
                context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            return new GammaKeyframe(gamma, interpolationType);
        }

        @Override
        public JsonElement serialize(GammaKeyframe src, Type typeOfSrc,
                                     JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("gamma", src.gamma);
            jsonObject.addProperty("type", "gamma");
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType()));
            return jsonObject;
        }
    }

}
