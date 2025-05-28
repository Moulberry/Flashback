package com.moulberry.flashback.editor.ui;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Vector3d;

public class KeyframeRelativeOffsets {

    public Vector3d oldOrigin = new Vector3d();
    public Vector3d newOrigin = new Vector3d();
    public float oldYaw = 0.0f;
    public float newYaw = 0.0f;
    public float oldPitch = 0.0f;
    public float newPitch = 0.0f;

    public void apply(Keyframe keyframe) {
        if (keyframe instanceof CameraKeyframe cameraKeyframe) {
            Vector3d position = cameraKeyframe.position;
            position.sub(this.oldOrigin);
            position.rotateY(Math.toRadians(this.oldYaw));
            position.rotateX(Math.toRadians(this.newPitch-this.oldPitch));
            position.rotateY(Math.toRadians(-this.newYaw));
            position.add(this.newOrigin);

            cameraKeyframe.yaw += Mth.wrapDegrees(this.newYaw - this.oldYaw);
            cameraKeyframe.pitch += Mth.wrapDegrees(this.newPitch - this.oldPitch);
        } else if (keyframe instanceof CameraOrbitKeyframe cameraOrbitKeyframe) {
            Vector3d center = cameraOrbitKeyframe.center;
            center.sub(this.oldOrigin);
            center.rotateY(Math.toRadians(this.oldYaw));
            center.rotateX(Math.toRadians(this.newPitch-this.oldPitch));
            center.rotateY(Math.toRadians(-this.newYaw));
            center.add(this.newOrigin);

            if (this.newPitch != this.oldPitch) {
                float oldPitchRadians = (float) Math.toRadians(cameraOrbitKeyframe.pitch);
                float oldYawRadians = (float) Math.toRadians(-cameraOrbitKeyframe.yaw);
                float oldCosYaw = Mth.cos(oldYawRadians);
                float oldSinYaw = Mth.sin(oldYawRadians);
                float oldCosPitch = Mth.cos(oldPitchRadians);
                float oldSinPitch = Mth.sin(oldPitchRadians);
                Vector3d oldLook = new Vector3d(oldSinYaw * oldCosPitch, -oldSinPitch, oldCosYaw * oldCosPitch);

                Vector3d camera = new Vector3d(cameraOrbitKeyframe.center).sub(oldLook.mul(cameraOrbitKeyframe.distance));
                camera.sub(this.oldOrigin);
                camera.rotateY(Math.toRadians(this.oldYaw));
                camera.rotateX(Math.toRadians(this.newPitch-this.oldPitch));
                camera.rotateY(Math.toRadians(-this.newYaw));
                camera.add(this.newOrigin);

                double dx = center.x - camera.x;
                double dy = center.y - camera.y;
                double dz = center.z - camera.z;
                double dhorz = Math.sqrt(dx * dx + dz * dz);
                cameraOrbitKeyframe.pitch = Mth.wrapDegrees((float)(-(Mth.atan2(dy, dhorz) * 180.0F / (float)Math.PI)));
                cameraOrbitKeyframe.yaw = Mth.wrapDegrees((float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F);
            } else if (this.newYaw != this.oldYaw) {
                cameraOrbitKeyframe.yaw += Mth.wrapDegrees(this.newYaw - this.oldYaw);
            }
        }
    }

}
