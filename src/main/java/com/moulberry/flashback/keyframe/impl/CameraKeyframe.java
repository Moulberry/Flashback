package com.moulberry.flashback.keyframe.impl;

import com.google.gson.*;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Type;

public class CameraKeyframe extends Keyframe {

    public final Vector3f position;
    public final Quaternionf quaternion;
    public final boolean hasRoll;

    private static Quaternionf getQuaternion(Entity entity) {
        Quaternionf quaternionf = new Quaternionf();
        float angleZ = 0.0f;
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideRoll) {
            angleZ = (float) Math.toRadians(editorState.replayVisuals.overrideRollAmount);
        }
        quaternionf = quaternionf.rotationYXZ((float) -Math.toRadians(entity.getYRot()), (float) Math.toRadians(entity.getXRot()), -angleZ);
        return quaternionf;
    }

    public CameraKeyframe(Entity entity) {
        this(entity.position().toVector3f(), getQuaternion(entity), shouldHaveRoll());
    }

    public CameraKeyframe(Vector3f position, Quaternionf quaternion, boolean hasRoll) {
        this(position, quaternion, hasRoll, InterpolationType.DEFAULT);
    }

    public CameraKeyframe(Vector3f position, Quaternionf quaternion, boolean hasRoll, InterpolationType interpolationType) {
        this.position = position;
        this.quaternion = quaternion;
        this.hasRoll = hasRoll;
        this.interpolationType(interpolationType);
    }

    @Override
    public Keyframe copy() {
        return new CameraKeyframe(new Vector3f(this.position), new Quaternionf(this.quaternion), this.hasRoll, this.interpolationType());
    }

    private static boolean shouldHaveRoll() {
        EditorState editorState = EditorStateManager.getCurrent();
        return editorState != null && editorState.replayVisuals.overrideRoll && editorState.replayVisuals.overrideRollAmount != 0.0f;
    }

    private void apply(KeyframeHandler keyframeHandler, Vector3f position, Quaternionf quaternion, float rollScale) {
        Vector3f euler = quaternion.getEulerAnglesYXZ(new Vector3f());
        float yaw = (float) Mth.wrapDegrees(Math.toDegrees(euler.y));
        float pitch = (float) Mth.wrapDegrees(Math.toDegrees(euler.x));
        float roll = (float) Mth.wrapDegrees(Math.toDegrees(euler.z)) * rollScale;

        // Handle singularity
        double test = (double)quaternion.y * (double)quaternion.z - (double)quaternion.w * (double)quaternion.x;
        final double MIN_TEST = 0.49999;
        final double MAX_TEST = 0.499999;
        if (test > MIN_TEST) {
            float fixedYaw = (float) Mth.wrapDegrees(Math.toDegrees(2 * Math.atan2(quaternion.z, quaternion.w)));
            if (test >= MAX_TEST) {
                yaw = fixedYaw;
            } else {
                yaw = yaw + (float)(Mth.wrapDegrees(fixedYaw - yaw) * (test - MIN_TEST) / (MAX_TEST - MIN_TEST));
            }
        } else if (test < -MIN_TEST) {
            float fixedYaw = (float) Mth.wrapDegrees(Math.toDegrees(-2 * Math.atan2(quaternion.z, quaternion.w)));
            if (test <= -MAX_TEST) {
                yaw = fixedYaw;
            } else {
                yaw = yaw + (float)(Mth.wrapDegrees(fixedYaw - yaw) * (test - -MIN_TEST) / (-MAX_TEST - -MIN_TEST));
            }
        }

        // Apply position
        keyframeHandler.applyCameraPosition(position, -yaw, pitch, -roll);
    }

    private static float calculateRollScale(CameraKeyframe left, CameraKeyframe right, float alpha) {
        if (left.hasRoll && right.hasRoll) {
            return 1.0f;
        } else if (left.hasRoll) {
            return 1.0f - alpha;
        } else if (right.hasRoll) {
            return alpha;
        } else {
            return 0.0f;
        }
    }

    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        apply(keyframeHandler, this.position, this.quaternion, this.hasRoll ? 1.0f : 0.0f);
    }

    @Override
    public void applyInterpolated(KeyframeHandler keyframeHandler, Keyframe genericOther, float amount) {
        if (!(genericOther instanceof CameraKeyframe other)) {
            this.apply(keyframeHandler);
            return;
        }

        double x = Interpolation.linear(this.position.x, other.position.x, amount);
        double y = Interpolation.linear(this.position.y, other.position.y, amount);
        double z = Interpolation.linear(this.position.z, other.position.z, amount);
        Quaternionf quaternion = Interpolation.linear(this.quaternion, other.quaternion, amount);

        Vector3f position = new Vector3f((float) x, (float) y, (float) z);
        apply(keyframeHandler, position, quaternion, calculateRollScale(this, other, amount));
    }

    @Override
    public void applyInterpolatedSmooth(KeyframeHandler keyframeHandler, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount, float lerpAmount, boolean lerpFromRight) {
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        // Calculate position
        Vector3f position = CatmullRom.position(this.position,
            ((CameraKeyframe)p1).position, ((CameraKeyframe)p2).position,
            ((CameraKeyframe)p3).position, time1, time2, time3, amount);

        float x = position.x;
        float y = position.y;
        float z = position.z;

        // Calculate rotation
        Quaternionf rotation = CatmullRom.rotation(this.quaternion,
            ((CameraKeyframe)p1).quaternion, ((CameraKeyframe)p2).quaternion,
            ((CameraKeyframe)p3).quaternion, time1, time2, time3, amount);

        if (lerpAmount >= 0) {
            float linearX = Interpolation.linear(((CameraKeyframe)p1).position.x, ((CameraKeyframe)p2).position.x, lerpAmount);
            float linearY = Interpolation.linear(((CameraKeyframe)p1).position.y, ((CameraKeyframe)p2).position.y, lerpAmount);
            float linearZ = Interpolation.linear(((CameraKeyframe)p1).position.z, ((CameraKeyframe)p2).position.z, lerpAmount);

            Quaternionf linearQuaternion = Interpolation.linear(((CameraKeyframe)p1).quaternion, ((CameraKeyframe)p2).quaternion, lerpAmount);

            if (lerpFromRight) {
                x = Interpolation.linear(x, linearX, amount);
                y = Interpolation.linear(y, linearY, amount);
                z = Interpolation.linear(z, linearZ, amount);
                rotation = Interpolation.linear(rotation, linearQuaternion, amount);
            } else {
                x = Interpolation.linear(linearX, x, amount);
                y = Interpolation.linear(linearY, y, amount);
                z = Interpolation.linear(linearZ, z, amount);
                rotation = Interpolation.linear(linearQuaternion, rotation, amount);
            }
        }

        apply(keyframeHandler, new Vector3f(x, y, z), rotation, calculateRollScale((CameraKeyframe) p1, (CameraKeyframe) p2, amount));
    }

    public static class TypeAdapter implements JsonSerializer<CameraKeyframe>, JsonDeserializer<CameraKeyframe> {
        @Override
        public CameraKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            Vector3f position = context.deserialize(jsonObject.get("position"), Vector3f.class);
            Quaternionf rotation = context.deserialize(jsonObject.get("rotation"), Quaternionf.class);
            boolean hasRoll = jsonObject.has("has_roll") && jsonObject.get("has_roll").getAsBoolean();
            InterpolationType interpolationType = context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            return new CameraKeyframe(position, rotation, hasRoll, interpolationType);
        }

        @Override
        public JsonElement serialize(CameraKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("position", context.serialize(src.position));
            jsonObject.add("rotation", context.serialize(src.quaternion));
            jsonObject.add("has_roll", context.serialize(src.hasRoll));
            jsonObject.addProperty("type", "camera");
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType()));
            return jsonObject;
        }
    }

}
