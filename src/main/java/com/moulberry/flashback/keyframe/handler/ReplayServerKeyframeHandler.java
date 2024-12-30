package com.moulberry.flashback.keyframe.handler;

import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeFreeze;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTickrate;
import com.moulberry.flashback.keyframe.types.FreezeKeyframeType;
import com.moulberry.flashback.keyframe.types.SpeedKeyframeType;
import com.moulberry.flashback.keyframe.types.TimelapseKeyframeType;
import com.moulberry.flashback.playback.ReplayServer;

import java.util.EnumSet;
import java.util.Set;

public record ReplayServerKeyframeHandler(ReplayServer replayServer) implements KeyframeHandler {

    private static final Set<Class<? extends KeyframeChange>> supportedChanges = Set.of(
        KeyframeChangeTickrate.class, KeyframeChangeFreeze.class
    );

    @Override
    public boolean supportsKeyframeChange(Class<? extends KeyframeChange> clazz) {
        return supportedChanges.contains(clazz);
    }

    @Override
    public boolean alwaysApplyLastKeyframe() {
        return true;
    }

    @Override
    public void applyFov(float fov) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyTickrate(float tickrate) {
        this.replayServer.setDesiredTickRate(tickrate, false);
    }

    @Override
    public void applyFreeze(boolean frozen, int frozenDelay) {
        this.replayServer.setFrozen(frozen, frozenDelay);
    }

    @Override
    public void applyTimeOfDay(int timeOfDay) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyCameraShake(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY) {
        throw new UnsupportedOperationException();
    }
}
