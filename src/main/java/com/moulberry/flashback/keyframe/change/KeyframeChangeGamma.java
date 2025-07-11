package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;

public record KeyframeChangeGamma(float gamma) implements KeyframeChange {
    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        keyframeHandler.applyGamma(this.gamma);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange to, double amount) {
        KeyframeChangeGamma other = (KeyframeChangeGamma) to;
        float gamma = (float) Interpolation.linear(this.gamma, other.gamma, amount);
        return new KeyframeChangeGamma(gamma);
    }

}
