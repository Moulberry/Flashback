package com.moulberry.flashback.keyframe;

import com.google.gson.*;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.impl.TickrateKeyframe;
import com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;

import java.lang.reflect.Type;

public abstract class Keyframe<T> {

    public final Class<T> operandClass;
    private InterpolationType interpolationType = InterpolationType.SMOOTH;

    public Keyframe(Class<T> operandClass) {
        this.operandClass = operandClass;
    }

    public InterpolationType interpolationType() {
        return interpolationType;
    }

    public void interpolationType(InterpolationType interpolationType) {
        this.interpolationType = interpolationType;
    }

    public abstract void apply(T t);
    public abstract void applyInterpolated(T t, Keyframe other, float amount);
    public abstract void applyInterpolatedSmooth(T t, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount,
        float lerpAmount, boolean lerpFromRight);

    public static class TypeAdapter implements JsonSerializer<Keyframe<?>>, JsonDeserializer<Keyframe<?>> {
        @Override
        public Keyframe<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            String type = jsonObject.get("type").getAsString();
            Keyframe<?> keyframe = switch (type) {
                case "camera" -> context.deserialize(json, CameraKeyframe.class);
                case "fov" -> context.deserialize(json, FOVKeyframe.class);
                case "tickrate" -> context.deserialize(json, TickrateKeyframe.class);
                case "time" -> context.deserialize(json, TimeOfDayKeyframe.class);
                default -> throw new IllegalStateException("Unknown keyframe type: " + type);
            };
            keyframe.interpolationType = context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            return keyframe;
        }

        @Override
        public JsonElement serialize(Keyframe<?> src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject;
            if (src instanceof CameraKeyframe cameraKeyframe) {
                jsonObject = (JsonObject) context.serialize(cameraKeyframe);
                jsonObject.addProperty("type", "camera");
            } else if (src instanceof FOVKeyframe fovKeyframe) {
                jsonObject = (JsonObject) context.serialize(fovKeyframe);
                jsonObject.addProperty("type", "fov");
            } else if (src instanceof TickrateKeyframe tickrateKeyframe) {
                jsonObject = (JsonObject) context.serialize(tickrateKeyframe);
                jsonObject.addProperty("type", "tickrate");
            } else if (src instanceof TimeOfDayKeyframe timeOfDayKeyframe) {
                jsonObject = (JsonObject) context.serialize(timeOfDayKeyframe);
                jsonObject.addProperty("type", "time");
            } else {
                throw new IllegalStateException("Unknown keyframe type: " + src.getClass());
            }
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType));
            return jsonObject;
        }
    }

}
