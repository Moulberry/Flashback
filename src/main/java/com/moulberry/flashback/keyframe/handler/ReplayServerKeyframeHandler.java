package com.moulberry.flashback.keyframe.handler;

import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.types.SpeedKeyframeType;
import com.moulberry.flashback.keyframe.types.TimelapseKeyframeType;
import com.moulberry.flashback.playback.ReplayServer;

import java.util.EnumSet;
import java.util.Set;

public class ReplayServerKeyframeHandler implements KeyframeHandler {

    private final ReplayServer replayServer;

    public ReplayServerKeyframeHandler(ReplayServer replayServer) {
        this.replayServer = replayServer;
    }

    @Override
    public boolean alwaysApplyLastKeyframe() {
        return true;
    }

    @Override
    public Set<KeyframeType<?>> supportedKeyframes() {
        return Set.of(SpeedKeyframeType.INSTANCE, TimelapseKeyframeType.INSTANCE);
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
    public void applyTimeOfDay(int timeOfDay) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyCameraShake(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY) {
        throw new UnsupportedOperationException();
    }
}
