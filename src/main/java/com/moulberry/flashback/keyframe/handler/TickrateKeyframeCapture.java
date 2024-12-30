package com.moulberry.flashback.keyframe.handler;

import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeFreeze;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTickrate;
import com.moulberry.flashback.keyframe.types.FreezeKeyframeType;
import com.moulberry.flashback.keyframe.types.SpeedKeyframeType;
import com.moulberry.flashback.keyframe.types.TimelapseKeyframeType;

import java.util.Set;

public class TickrateKeyframeCapture implements KeyframeHandler {
    private static final Set<Class<? extends KeyframeChange>> supportedChanges = Set.of(
            KeyframeChangeTickrate.class, KeyframeChangeFreeze.class
    );

    public float tickrate = 20.0f;
    public boolean frozen = false;
    public int frozenDelay = 0;

    @Override
    public boolean supportsKeyframeChange(Class<? extends KeyframeChange> clazz) {
        return supportedChanges.contains(clazz);
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
