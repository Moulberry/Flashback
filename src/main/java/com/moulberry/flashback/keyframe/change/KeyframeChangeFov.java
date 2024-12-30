package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;

public record KeyframeChangeFov(float fov) implements KeyframeChange {
    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        keyframeHandler.applyFov(this.fov);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange to, double amount) {
        KeyframeChangeFov other = (KeyframeChangeFov) to;
        float thisFocalLength = Utils.fovToFocalLength(this.fov);
        float otherFocalLength = Utils.fovToFocalLength(other.fov);
        float focalLength = (float) Interpolation.linear(thisFocalLength, otherFocalLength, amount);

        return new KeyframeChangeFov(Utils.focalLengthToFov(focalLength));
    }

}
