package com.moulberry.flashback.keyframe;

import com.google.gson.*;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.impl.*;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import imgui.ImDrawList;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

public abstract class Keyframe {

    private InterpolationType interpolationType = InterpolationType.getDefault();

    public InterpolationType interpolationType() {
        return interpolationType;
    }

    public void interpolationType(InterpolationType interpolationType) {
        this.interpolationType = Objects.requireNonNullElse(interpolationType, InterpolationType.getDefault());
    }

    public abstract KeyframeType<?> keyframeType();
    public abstract Keyframe copy();
    public abstract KeyframeChange createChange();
    public abstract KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount);
    public abstract KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float tick);

    public float getCustomWidthInTicks() {
        return -1;
    }

    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {}

    public void drawOnTimeline(ImDrawList drawList, int keyframeSize, float x, float y, int colour, float timelineScale, float minTimelineX, float maxTimelineX,
                               int tick, TreeMap<Integer, Keyframe> keyframeTimes) {
        int easeSize = keyframeSize / 5;
        switch (interpolationType) {
            case SMOOTH -> {
                drawList.addCircleFilled(x, y, keyframeSize, colour);
            }
            case LINEAR -> {
                drawList.addTriangleFilled(x - keyframeSize, y, x, y - keyframeSize, x, y + keyframeSize, colour);
                drawList.addTriangleFilled(x + keyframeSize, y, x, y + keyframeSize, x, y - keyframeSize, colour);
            }
            case EASE_IN -> {
                // Left inverted triangle
                drawList.addTriangleFilled(x - keyframeSize, y - keyframeSize, x - easeSize, y - keyframeSize, x - easeSize, y, colour);
                drawList.addTriangleFilled(x - keyframeSize, y + keyframeSize, x - easeSize, y, x - easeSize, y + keyframeSize, colour);
                // Right triangle
                drawList.addTriangleFilled(x + keyframeSize, y, x + easeSize, y + keyframeSize, x + easeSize, y - keyframeSize, colour);
                // Center
                drawList.addRectFilled(x - easeSize, y - keyframeSize, x + easeSize, y + keyframeSize, colour);
            }
            case EASE_OUT -> {
                // Left triangle
                drawList.addTriangleFilled(x - keyframeSize, y, x - easeSize, y - keyframeSize, x - easeSize, y + keyframeSize, colour);
                // Right inverted triangle
                drawList.addTriangleFilled(x + keyframeSize, y - keyframeSize, x + easeSize, y, x + easeSize, y - keyframeSize, colour);
                drawList.addTriangleFilled(x + keyframeSize, y + keyframeSize, x + easeSize, y + keyframeSize, x + easeSize, y, colour);
                // Center
                drawList.addRectFilled(x - easeSize, y - keyframeSize, x + easeSize, y + keyframeSize, colour);
            }
            case EASE_IN_OUT -> {
                // Left inverted triangle
                drawList.addTriangleFilled(x - keyframeSize, y - keyframeSize, x - easeSize, y - keyframeSize, x - easeSize, y, colour);
                drawList.addTriangleFilled(x - keyframeSize, y + keyframeSize, x - easeSize, y, x - easeSize, y + keyframeSize, colour);
                // Right inverted triangle
                drawList.addTriangleFilled(x + keyframeSize, y - keyframeSize, x + easeSize, y, x + easeSize, y - keyframeSize, colour);
                drawList.addTriangleFilled(x + keyframeSize, y + keyframeSize, x + easeSize, y + keyframeSize, x + easeSize, y, colour);
                // Center
                drawList.addRectFilled(x - easeSize, y - keyframeSize, x + easeSize, y + keyframeSize, colour);
            }
            case HOLD -> {
                drawList.addRectFilled(x - keyframeSize, y - keyframeSize, x + keyframeSize, y + keyframeSize, colour);
            }
            case HERMITE -> {
                drawList.addTriangleFilled(x, y - keyframeSize, x + keyframeSize, y + keyframeSize,
                        x - keyframeSize, y + keyframeSize, colour);
            }
        }
    }

    public static class TypeAdapter implements JsonSerializer<Keyframe>, JsonDeserializer<Keyframe> {
        @Override
        public Keyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            String type;
            if (jsonObject.has("type")) {
                type = jsonObject.get("type").getAsString();
            } else {
                throw new RuntimeException("Unable to determine type of keyframe for: " + jsonObject);
            }
            Keyframe keyframe = switch (type) {
                case "camera" -> context.deserialize(json, CameraKeyframe.class);
                case "camera_orbit" -> context.deserialize(json, CameraOrbitKeyframe.class);
                case "track_entity" -> context.deserialize(json, TrackEntityKeyframe.class);
                case "fov" -> context.deserialize(json, FOVKeyframe.class);
                case "tickrate" -> context.deserialize(json, TickrateKeyframe.class);
                case "freeze" -> context.deserialize(json, FreezeKeyframe.class);
                case "timelapse" -> context.deserialize(json, TimelapseKeyframe.class);
                case "time" -> context.deserialize(json, TimeOfDayKeyframe.class);
                case "camera_shake" -> context.deserialize(json, CameraShakeKeyframe.class);
                case "block_override" -> context.deserialize(json, BlockOverrideKeyframe.class);
                case "audio" -> context.deserialize(json, AudioKeyframe.class);
                default -> throw new IllegalStateException("Unknown keyframe type: " + type);
            };
            keyframe.interpolationType(context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class));
            return keyframe;
        }

        @Override
        public JsonElement serialize(Keyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject;
            switch (src) {
                case CameraKeyframe cameraKeyframe -> {
                    jsonObject = (JsonObject) context.serialize(cameraKeyframe);
                    jsonObject.addProperty("type", "camera");
                }
                case CameraOrbitKeyframe cameraOrbitKeyframe -> {
                    jsonObject = (JsonObject) context.serialize(cameraOrbitKeyframe);
                    jsonObject.addProperty("type", "camera_orbit");
                }
                case FOVKeyframe fovKeyframe -> {
                    jsonObject = (JsonObject) context.serialize(fovKeyframe);
                    jsonObject.addProperty("type", "fov");
                }
                case TickrateKeyframe tickrateKeyframe -> {
                    jsonObject = (JsonObject) context.serialize(tickrateKeyframe);
                    jsonObject.addProperty("type", "tickrate");
                }
                case FreezeKeyframe freezeKeyframe -> {
                    jsonObject = (JsonObject) context.serialize(freezeKeyframe);
                    jsonObject.addProperty("type", "freeze");
                }
                case TimeOfDayKeyframe timeOfDayKeyframe -> {
                    jsonObject = (JsonObject) context.serialize(timeOfDayKeyframe);
                    jsonObject.addProperty("type", "time");
                }
                case CameraShakeKeyframe cameraShakeKeyframe -> {
                    jsonObject = (JsonObject) context.serialize(cameraShakeKeyframe);
                    jsonObject.addProperty("type", "camera_shake");
                }
                case BlockOverrideKeyframe blockOverrideKeyframe -> {
                    jsonObject = (JsonObject) context.serialize(blockOverrideKeyframe);
                    jsonObject.addProperty("type", "block_override");
                }
                case AudioKeyframe audioKeyframe -> {
                    jsonObject = (JsonObject) context.serialize(audioKeyframe);
                    jsonObject.addProperty("type", "audio");
                }
                default -> throw new IllegalStateException("Unknown keyframe type: " + src.getClass());
            }
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType));
            return jsonObject;
        }
    }

}
