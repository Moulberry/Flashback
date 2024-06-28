package com.moulberry.flashback.keyframe.impl;

import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.spline.CatmullRom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CameraKeyframe extends Keyframe<Minecraft> {

    private final Vec3 position;
    private final Quaternionf quaternion;

    public CameraKeyframe(Entity entity) {
        this(entity.position(), new Quaternionf().rotationYXZ((float) Math.toRadians(entity.getYRot()), (float) Math.toRadians(entity.getXRot()), 0.0f));
    }

    public CameraKeyframe(Vec3 position, Quaternionf quaternion) {
        super(Minecraft.class);
        this.position = position;
        this.quaternion = quaternion;
    }

    @Override
    public void apply(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player != null) {
            Vector3f euler = this.quaternion.getEulerAnglesYXZ(new Vector3f());
            player.moveTo(this.position.x, this.position.y, this.position.z, (float) Mth.wrapDegrees(Math.toDegrees(euler.y)), (float) Mth.wrapDegrees(Math.toDegrees(euler.x)));
            player.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public void applyInterpolated(Minecraft minecraft, Keyframe genericOther, float amount) {
        if (!(genericOther instanceof CameraKeyframe other)) {
            this.apply(minecraft);
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player != null) {
            double x = Interpolation.linear(this.position.x, other.position.x, amount);
            double y = Interpolation.linear(this.position.y, other.position.y, amount);
            double z = Interpolation.linear(this.position.z, other.position.z, amount);
            Quaternionf quaternion = Interpolation.linear(this.quaternion, other.quaternion, amount);
            Vector3f euler = quaternion.getEulerAnglesYXZ(new Vector3f());
            player.moveTo(x, y, z, (float) Mth.wrapDegrees(Math.toDegrees(euler.y)), (float) Mth.wrapDegrees(Math.toDegrees(euler.x)));
            player.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public void applyInterpolatedSmooth(Minecraft minecraft, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount, float lerpAmount, boolean lerpFromRight) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        // Calculate position
        Vector3f position = CatmullRom.position(this.position.toVector3f(),
            ((CameraKeyframe)p1).position.toVector3f(), ((CameraKeyframe)p2).position.toVector3f(),
            ((CameraKeyframe)p3).position.toVector3f(), time1, time2, time3, amount);

        float x = position.x;
        float y = position.y;
        float z = position.z;

        // Calculate rotation
        Quaternionf rotation = CatmullRom.rotation(this.quaternion,
            ((CameraKeyframe)p1).quaternion, ((CameraKeyframe)p2).quaternion,
            ((CameraKeyframe)p3).quaternion, time1, time2, time3, amount);

        if (lerpAmount >= 0) {
            float linearX = (float) Interpolation.linear(((CameraKeyframe)p1).position.x, ((CameraKeyframe)p2).position.x, lerpAmount);
            float linearY = (float) Interpolation.linear(((CameraKeyframe)p1).position.y, ((CameraKeyframe)p2).position.y, lerpAmount);
            float linearZ = (float) Interpolation.linear(((CameraKeyframe)p1).position.z, ((CameraKeyframe)p2).position.z, lerpAmount);

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

        Vector3f euler = rotation.getEulerAnglesYXZ(new Vector3f());
        float yaw = (float) Mth.wrapDegrees(Math.toDegrees(euler.y));
        float pitch = (float) Mth.wrapDegrees(Math.toDegrees(euler.x));

        player.moveTo(x, y, z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
    }
}
