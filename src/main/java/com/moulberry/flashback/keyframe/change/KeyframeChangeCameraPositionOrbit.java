package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Vector3d;

public record KeyframeChangeCameraPositionOrbit(Vector3d center, double distance, double yaw, double pitch) implements KeyframeChange {
    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        float pitchRadians = (float) Math.toRadians(this.pitch);
        float yawRadians = (float) Math.toRadians(-this.yaw);
        float cosYaw = Mth.cos(yawRadians);
        float sinYaw = Mth.sin(yawRadians);
        float cosPitch = Mth.cos(pitchRadians);
        float sinPitch = Mth.sin(pitchRadians);

        Vector3d look = new Vector3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
        Vector3d cameraPosition = new Vector3d(this.center).sub(look.mul(this.distance));
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            cameraPosition.y -= player.getEyeHeight();
        }
        keyframeHandler.applyCameraPosition(cameraPosition, this.yaw, this.pitch, 0.0f);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange to, double amount) {
        KeyframeChangeCameraPositionOrbit other = (KeyframeChangeCameraPositionOrbit) to;
        return new KeyframeChangeCameraPositionOrbit(
            this.center.lerp(other.center, amount, new Vector3d()),
            Interpolation.linear(this.distance, other.distance, amount),
            Interpolation.linear(this.yaw, other.yaw, amount),
            Interpolation.linear(this.pitch, other.pitch, amount)
        );
    }
}
