package com.moulberry.flashback.keyframe.handler;

import com.moulberry.flashback.keyframe.KeyframeType;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.Set;

public interface KeyframeHandler {

    Set<KeyframeType<?>> supportedKeyframes();

    default boolean alwaysApplyLastKeyframe() {
        return false;
    }

    default void applyCameraPosition(Vector3d position, double yaw, double pitch, double roll) {
        throw new UnsupportedOperationException();
    }

    default void applyFov(float fov) {
        throw new UnsupportedOperationException();
    }

    default void applyTickrate(float tickrate) {
        throw new UnsupportedOperationException();
    }

    default void applyFreeze(boolean frozen, int frozenDelay) {
        throw new UnsupportedOperationException();
    }

    default void applyTimeOfDay(int timeOfDay) {
        throw new UnsupportedOperationException();
    }

    default void applyCameraShake(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY) {
        throw new UnsupportedOperationException();
    }


}
