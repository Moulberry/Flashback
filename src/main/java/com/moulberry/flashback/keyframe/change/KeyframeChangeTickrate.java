package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;

public record KeyframeChangeTickrate(float tickrate) implements KeyframeChange {
    public static final float MIN_TICKRATE = 0.001f;

    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        keyframeHandler.applyTickrate(this.tickrate);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange to, double amount) {
        KeyframeChangeTickrate other = (KeyframeChangeTickrate) to;
        return new KeyframeChangeTickrate(
            Math.max(MIN_TICKRATE, (float) Interpolation.linear(this.tickrate, other.tickrate, amount))
        );
    }

}
