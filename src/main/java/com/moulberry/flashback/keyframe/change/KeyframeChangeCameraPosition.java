package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import org.joml.Vector3d;

public record KeyframeChangeCameraPosition(Vector3d position, double yaw, double pitch, double roll) implements KeyframeChange {
    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        keyframeHandler.applyCameraPosition(this.position, this.yaw, this.pitch, this.roll);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange to, double amount) {
        KeyframeChangeCameraPosition other = (KeyframeChangeCameraPosition) to;
        return new KeyframeChangeCameraPosition(
            this.position.lerp(other.position, amount, new Vector3d()),
            Interpolation.linearAngle(this.yaw, other.yaw, amount),
            Interpolation.linearAngle(this.pitch, other.pitch, amount),
            Interpolation.linearAngle(this.roll, other.roll, amount)
        );
    }
}
