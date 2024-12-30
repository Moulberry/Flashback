package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;

public record KeyframeChangeCameraShake(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY) implements KeyframeChange {
    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        keyframeHandler.applyCameraShake(this.frequencyX, this.amplitudeX, this.frequencyY, this.amplitudeY);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange to, double amount) {
        KeyframeChangeCameraShake other = (KeyframeChangeCameraShake) to;
        return new KeyframeChangeCameraShake(
            (float) Interpolation.linear(this.frequencyX, other.frequencyX, amount),
            (float) Interpolation.linear(this.amplitudeX, other.amplitudeX, amount),
            (float) Interpolation.linear(this.frequencyY, other.frequencyY, amount),
            (float) Interpolation.linear(this.amplitudeY, other.amplitudeY, amount)
        );
    }
}
