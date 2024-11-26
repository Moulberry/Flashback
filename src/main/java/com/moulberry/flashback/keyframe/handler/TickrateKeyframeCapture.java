package com.moulberry.flashback.keyframe.handler;

import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.types.FreezeKeyframeType;
import com.moulberry.flashback.keyframe.types.SpeedKeyframeType;
import com.moulberry.flashback.keyframe.types.TimelapseKeyframeType;

import java.util.Set;

public class TickrateKeyframeCapture implements KeyframeHandler {
    public float tickrate = 20.0f;
    public boolean frozen = false;
    public int frozenDelay = 0;

    @Override
    public Set<KeyframeType<?>> supportedKeyframes() {
        return Set.of(SpeedKeyframeType.INSTANCE, TimelapseKeyframeType.INSTANCE, FreezeKeyframeType.INSTANCE);
    }

    @Override
    public boolean alwaysApplyLastKeyframe() {
        return true;
    }

    @Override
    public void applyTickrate(float tickrate) {
        this.tickrate = tickrate;
    }

    @Override
    public void applyFreeze(boolean frozen, int frozenDelay) {
        this.frozen = frozen;
        this.frozenDelay = frozenDelay;
    }
}
