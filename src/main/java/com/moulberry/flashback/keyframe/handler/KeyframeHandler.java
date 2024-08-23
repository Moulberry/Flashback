package com.moulberry.flashback.keyframe.handler;

import com.moulberry.flashback.keyframe.KeyframeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumSet;

public interface KeyframeHandler {

    EnumSet<KeyframeType> supportedKeyframes();

    default boolean alwaysApplyLastKeyframe() {
        return false;
    }

    default void applyCameraPosition(Vector3f position, float yaw, float pitch, float roll) {
        throw new UnsupportedOperationException();
    }

    default void applyFov(float fov) {
        throw new UnsupportedOperationException();
    }

    default void applyTickrate(float tickrate) {
        throw new UnsupportedOperationException();
    }

    default void applyTimeOfDay(int timeOfDay) {
        throw new UnsupportedOperationException();
    }

    default void applyCameraShake(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY) {
        throw new UnsupportedOperationException();
    }


}
