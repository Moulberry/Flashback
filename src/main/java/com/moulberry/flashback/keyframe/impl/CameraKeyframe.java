package com.moulberry.flashback.keyframe.impl;

import com.google.gson.*;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.CameraKeyframeType;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;

import java.lang.reflect.Type;
import java.util.function.Consumer;

public class CameraKeyframe extends Keyframe {

    private final Vector3d position;
    private float yaw;
    private float pitch;
    private float roll;

    private static float getDefaultRoll() {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideRoll) {
            return editorState.replayVisuals.overrideRollAmount;
        } else {
            return 0.0f;
        }
    }

    public CameraKeyframe(Entity entity) {
        this(new Vector3d(entity.getX(), entity.getY(), entity.getZ()), entity.getYRot(), entity.getXRot(), getDefaultRoll());
    }

    public CameraKeyframe(Vector3d position, float yaw, float pitch, float roll) {
        this(position, yaw, pitch, roll, InterpolationType.getDefault());
    }

    public CameraKeyframe(Vector3d position, float yaw, float pitch, float roll, InterpolationType interpolationType) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.interpolationType(interpolationType);
    }

    public Vector3d getNormal() {
        double yawRad = Math.toRadians(-this.yaw);
        double cosYaw = Math.cos((float)yawRad);
        double sinYaw = Math.sin((float)yawRad);

        double pitchRad = Math.toRadians(this.pitch);
        double cosPitch = Math.cos((float)pitchRad);
        double sinPitch = Math.sin((float)pitchRad);

        return new Vector3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return CameraKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return new CameraKeyframe(new Vector3d(this.position), this.yaw, this.pitch, this.roll, this.interpolationType());
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        float[] center = new float[]{(float) this.position.x, (float) this.position.y, (float) this.position.z};
        if (ImGuiHelper.inputFloat("Position", center)) {
            if (center[0] != this.position.x) {
                update.accept(keyframe -> ((CameraKeyframe)keyframe).position.x = center[0]);
            }
            if (center[1] != this.position.y) {
                update.accept(keyframe -> ((CameraKeyframe)keyframe).position.y = center[1]);
            }
            if (center[2] != this.position.z) {
                update.accept(keyframe -> ((CameraKeyframe)keyframe).position.z = center[2]);
            }
        }
        float[] input = new float[]{this.yaw};
        if (ImGuiHelper.inputFloat("Yaw", input)) {
            if (input[0] != this.yaw) {
                update.accept(keyframe -> ((CameraKeyframe)keyframe).yaw = input[0]);
            }
        }
        input[0] = this.pitch;
        if (ImGuiHelper.inputFloat("Pitch", input)) {
            if (input[0] != this.pitch) {
                update.accept(keyframe -> ((CameraKeyframe)keyframe).pitch = input[0]);
            }
        }
        input[0] = this.roll;
        if (ImGuiHelper.inputFloat("Roll", input)) {
            if (input[0] != this.roll) {
                update.accept(keyframe -> ((CameraKeyframe)keyframe).roll = input[0]);
            }
        }
    }



    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        keyframeHandler.applyCameraPosition(this.position, this.yaw, this.pitch, this.roll);
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
        Vector3d position = new Vector3d(x, y, z);

        float yaw = Interpolation.linearAngle(this.yaw, other.yaw, amount);
        float pitch = Interpolation.linearAngle(this.pitch, other.pitch, amount);
        float roll = Interpolation.linearAngle(this.roll, other.roll, amount);

        keyframeHandler.applyCameraPosition(position, yaw, pitch, roll);
    }

    @Override
    public void applyInterpolatedSmooth(KeyframeHandler keyframeHandler, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount, float lerpAmount, boolean lerpFromRight) {
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        // Calculate position
        Vector3d position = CatmullRom.position(this.position,
            ((CameraKeyframe)p1).position, ((CameraKeyframe)p2).position,
            ((CameraKeyframe)p3).position, time1, time2, time3, amount);

        double x = position.x;
        double y = position.y;
        double z = position.z;

        // Calculate rotation
        float yaw = CatmullRom.degrees(this.yaw,
            ((CameraKeyframe)p1).yaw, ((CameraKeyframe)p2).yaw,
            ((CameraKeyframe)p3).yaw, time1, time2, time3, amount);
        float pitch = CatmullRom.degrees(this.pitch,
            ((CameraKeyframe)p1).pitch, ((CameraKeyframe)p2).pitch,
            ((CameraKeyframe)p3).pitch, time1, time2, time3, amount);
        float roll = CatmullRom.degrees(this.roll,
            ((CameraKeyframe)p1).roll, ((CameraKeyframe)p2).roll,
            ((CameraKeyframe)p3).roll, time1, time2, time3, amount);

        if (lerpAmount >= 0) {
            double linearX = Interpolation.linear(((CameraKeyframe)p1).position.x, ((CameraKeyframe)p2).position.x, lerpAmount);
            double linearY = Interpolation.linear(((CameraKeyframe)p1).position.y, ((CameraKeyframe)p2).position.y, lerpAmount);
            double linearZ = Interpolation.linear(((CameraKeyframe)p1).position.z, ((CameraKeyframe)p2).position.z, lerpAmount);
            float linearYaw = Interpolation.linearAngle(((CameraKeyframe)p1).yaw, ((CameraKeyframe)p2).yaw, lerpAmount);
            float linearPitch = Interpolation.linearAngle(((CameraKeyframe)p1).pitch, ((CameraKeyframe)p2).pitch, lerpAmount);
            float linearRoll = Interpolation.linearAngle(((CameraKeyframe)p1).roll, ((CameraKeyframe)p2).roll, lerpAmount);

            if (lerpFromRight) {
                x = Interpolation.linear(x, linearX, amount);
                y = Interpolation.linear(y, linearY, amount);
                z = Interpolation.linear(z, linearZ, amount);
                yaw = Interpolation.linearAngle(yaw, linearYaw, amount);
                pitch = Interpolation.linearAngle(pitch, linearPitch, amount);
                roll = Interpolation.linearAngle(roll, linearRoll, amount);
            } else {
                x = Interpolation.linear(linearX, x, amount);
                y = Interpolation.linear(linearY, y, amount);
                z = Interpolation.linear(linearZ, z, amount);
                yaw = Interpolation.linearAngle(linearYaw, yaw, amount);
                pitch = Interpolation.linearAngle(linearPitch, pitch, amount);
                roll = Interpolation.linearAngle(linearRoll, roll, amount);
            }
        }

        position.x = x;
        position.y = y;
        position.z = z;
        keyframeHandler.applyCameraPosition(position, yaw, pitch, roll);
    }

    public static class TypeAdapter implements JsonSerializer<CameraKeyframe>, JsonDeserializer<CameraKeyframe> {
        @Override
        public CameraKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            Vector3d position = context.deserialize(jsonObject.get("position"), Vector3d.class);
            float yaw = jsonObject.has("yaw") ? jsonObject.get("yaw").getAsFloat() : 0.0f;
            float pitch = jsonObject.has("pitch") ? jsonObject.get("pitch").getAsFloat() : 0.0f;
            float roll = jsonObject.has("roll") ? jsonObject.get("roll").getAsFloat() : 0.0f;
            InterpolationType interpolationType = context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            return new CameraKeyframe(position, yaw, pitch, roll, interpolationType);
        }

        @Override
        public JsonElement serialize(CameraKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("position", context.serialize(src.position));
            jsonObject.add("yaw", context.serialize(src.yaw));
            jsonObject.add("pitch", context.serialize(src.pitch));
            jsonObject.add("roll", context.serialize(src.roll));
            jsonObject.addProperty("type", "camera");
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType()));
            return jsonObject;
        }
    }

}
