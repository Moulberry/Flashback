package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;

public record KeyframeChangeFreeze(boolean frozen, int frozenDelay) implements KeyframeChange {
    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        keyframeHandler.applyFreeze(this.frozen, this.frozenDelay);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange to, double amount) {
        return this;
    }

}
