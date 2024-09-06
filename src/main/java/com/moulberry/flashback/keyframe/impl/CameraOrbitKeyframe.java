package com.moulberry.flashback.keyframe.impl;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.CameraOrbitKeyframeType;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;
import imgui.type.ImFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.lang.reflect.Type;
import java.util.function.Consumer;

public class CameraOrbitKeyframe extends Keyframe {

    public final Vector3d center;
    public float distance;
    public float yaw;
    public float pitch;

    public CameraOrbitKeyframe(Vector3d center, float distance, float yaw, float pitch) {
        this(center, distance, yaw, pitch, InterpolationType.DEFAULT);
    }

    public CameraOrbitKeyframe(Vector3d center, float distance, float yaw, float pitch, InterpolationType interpolationType) {
        this.center = center;
        this.distance = distance;
        this.yaw = yaw;
        this.pitch = pitch;
        this.interpolationType(interpolationType);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return CameraOrbitKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return new CameraOrbitKeyframe(new Vector3d(this.center), this.distance, this.yaw, this.pitch, this.interpolationType());
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        float[] center = new float[]{(float) this.center.x, (float) this.center.y, (float) this.center.z};
        if (ImGuiHelper.inputFloat("Position", center)) {
            if (center[0] != this.center.x) {
                update.accept(keyframe -> ((CameraOrbitKeyframe)keyframe).center.x = center[0]);
            }
            if (center[1] != this.center.y) {
                update.accept(keyframe -> ((CameraOrbitKeyframe)keyframe).center.y = center[1]);
            }
            if (center[2] != this.center.z) {
                update.accept(keyframe -> ((CameraOrbitKeyframe)keyframe).center.z = center[2]);
            }
        }
        float[] input = new float[]{this.distance};
        if (ImGuiHelper.inputFloat("Distance", input)) {
            if (input[0] != this.distance) {
                update.accept(keyframe -> ((CameraOrbitKeyframe)keyframe).distance = input[0]);
            }
        }
        input[0] = this.yaw;
        if (ImGuiHelper.inputFloat("Yaw", input)) {
            if (input[0] != this.yaw) {
                update.accept(keyframe -> ((CameraOrbitKeyframe)keyframe).yaw = input[0]);
            }
        }
        input[0] = this.pitch;
        if (ImGuiHelper.inputFloat("Pitch", input)) {
            if (input[0] != this.pitch) {
                update.accept(keyframe -> ((CameraOrbitKeyframe)keyframe).pitch = input[0]);
            }
        }
    }

    private void apply(KeyframeHandler keyframeHandler, Vector3d center, float distance, float yaw, float pitch) {
        float pitchRadians = (float) Math.toRadians(pitch);
        float yawRadians = (float) Math.toRadians(-yaw);
        float cosYaw = Mth.cos(yawRadians);
        float sinYaw = Mth.sin(yawRadians);
        float cosPitch = Mth.cos(pitchRadians);
        float sinPitch = Mth.sin(pitchRadians);

        Vector3d look = new Vector3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
        Vector3d cameraPosition = new Vector3d(center).sub(look.mul(distance));
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            cameraPosition.y -= player.getEyeHeight();
        }
        keyframeHandler.applyCameraPosition(cameraPosition, yaw, pitch, 0.0f);
    }

    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        apply(keyframeHandler, this.center, this.distance, this.yaw, this.pitch);
    }

    @Override
    public void applyInterpolated(KeyframeHandler keyframeHandler, Keyframe genericOther, float amount) {
        if (!(genericOther instanceof CameraOrbitKeyframe other)) {
            this.apply(keyframeHandler);
            return;
        }

        double x = Interpolation.linear(this.center.x, other.center.x, amount);
        double y = Interpolation.linear(this.center.y, other.center.y, amount);
        double z = Interpolation.linear(this.center.z, other.center.z, amount);
        float distance = Interpolation.linear(this.distance, other.distance, amount);
        float yaw = Interpolation.linear(this.yaw, other.yaw, amount);
        float pitch = Interpolation.linear(this.pitch, other.pitch, amount);

        Vector3d center = new Vector3d((float) x, (float) y, (float) z);
        apply(keyframeHandler, center, distance, yaw, pitch);
    }

    @Override
    public void applyInterpolatedSmooth(KeyframeHandler keyframeHandler, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount, float lerpAmount, boolean lerpFromRight) {
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        // Calculate position
        Vector3d position = CatmullRom.position(this.center,
            ((CameraOrbitKeyframe)p1).center, ((CameraOrbitKeyframe)p2).center,
            ((CameraOrbitKeyframe)p3).center, time1, time2, time3, amount);

        double x = position.x;
        double y = position.y;
        double z = position.z;
        float distance = CatmullRom.value(this.distance, ((CameraOrbitKeyframe)p1).distance, ((CameraOrbitKeyframe)p2).distance,
            ((CameraOrbitKeyframe)p3).distance, time1, time2, time3, amount);
        float yaw = CatmullRom.value(this.yaw, ((CameraOrbitKeyframe)p1).yaw, ((CameraOrbitKeyframe)p2).yaw,
            ((CameraOrbitKeyframe)p3).yaw, time1, time2, time3, amount);
        float pitch = CatmullRom.value(this.pitch, ((CameraOrbitKeyframe)p1).pitch, ((CameraOrbitKeyframe)p2).pitch,
            ((CameraOrbitKeyframe)p3).pitch, time1, time2, time3, amount);

        if (lerpAmount >= 0) {
            double linearX = Interpolation.linear(((CameraOrbitKeyframe)p1).center.x, ((CameraOrbitKeyframe)p2).center.x, lerpAmount);
            double linearY = Interpolation.linear(((CameraOrbitKeyframe)p1).center.y, ((CameraOrbitKeyframe)p2).center.y, lerpAmount);
            double linearZ = Interpolation.linear(((CameraOrbitKeyframe)p1).center.z, ((CameraOrbitKeyframe)p2).center.z, lerpAmount);
            float linearDistance = Interpolation.linear(((CameraOrbitKeyframe)p1).distance, ((CameraOrbitKeyframe)p2).distance, amount);
            float linearYaw = Interpolation.linear(((CameraOrbitKeyframe)p1).yaw, ((CameraOrbitKeyframe)p2).yaw, amount);
            float linearPitch = Interpolation.linear(((CameraOrbitKeyframe)p1).pitch, ((CameraOrbitKeyframe)p2).pitch, amount);

            if (lerpFromRight) {
                x = Interpolation.linear(x, linearX, amount);
                y = Interpolation.linear(y, linearY, amount);
                z = Interpolation.linear(z, linearZ, amount);
                distance = Interpolation.linear(distance, linearDistance, amount);
                yaw = Interpolation.linear(yaw, linearYaw, amount);
                pitch = Interpolation.linear(pitch, linearPitch, amount);
            } else {
                x = Interpolation.linear(linearX, x, amount);
                y = Interpolation.linear(linearY, y, amount);
                z = Interpolation.linear(linearZ, z, amount);
                distance = Interpolation.linear(linearDistance, distance, amount);
                yaw = Interpolation.linear(linearYaw, yaw, amount);
                pitch = Interpolation.linear(linearPitch, pitch, amount);
            }
        }

        apply(keyframeHandler, new Vector3d(x, y, z), distance, yaw, pitch);
    }

    public static class TypeAdapter implements JsonSerializer<CameraOrbitKeyframe>, JsonDeserializer<CameraOrbitKeyframe> {
        @Override
        public CameraOrbitKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            Vector3d center = context.deserialize(jsonObject.get("center"), Vector3d.class);
            float distance = jsonObject.get("distance").getAsFloat();
            float yaw = jsonObject.get("yaw").getAsFloat();
            float pitch = jsonObject.get("pitch").getAsFloat();
            InterpolationType interpolationType = context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            return new CameraOrbitKeyframe(center, distance, yaw, pitch, interpolationType);
        }

        @Override
        public JsonElement serialize(CameraOrbitKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("center", context.serialize(src.center));
            jsonObject.addProperty("distance", src.distance);
            jsonObject.addProperty("yaw", src.yaw);
            jsonObject.addProperty("pitch", src.pitch);
            jsonObject.addProperty("type", "camera_orbit");
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType()));
            return jsonObject;
        }
    }

}
