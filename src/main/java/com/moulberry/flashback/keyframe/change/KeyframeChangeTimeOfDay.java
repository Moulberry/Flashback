package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;

public record KeyframeChangeTimeOfDay(int timeOfDay) implements KeyframeChange {
    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        keyframeHandler.applyTimeOfDay(this.timeOfDay);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange to, double amount) {
        KeyframeChangeTimeOfDay other = (KeyframeChangeTimeOfDay) to;
        return new KeyframeChangeTimeOfDay(
            (int) Interpolation.linear(this.timeOfDay, other.timeOfDay, amount)
        );
    }

}
