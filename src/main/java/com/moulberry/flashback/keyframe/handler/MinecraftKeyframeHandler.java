package com.moulberry.flashback.keyframe.handler;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.keyframe.KeyframeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.EnumSet;

public class MinecraftKeyframeHandler implements KeyframeHandler {

    private final Minecraft minecraft;

    public MinecraftKeyframeHandler(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public EnumSet<KeyframeType> supportedKeyframes() {
        return EnumSet.of(KeyframeType.CAMERA, KeyframeType.CAMERA_ORBIT, KeyframeType.FOV, KeyframeType.TIME_OF_DAY, KeyframeType.CAMERA_SHAKE);
    }

    @Override
    public void applyCameraPosition(Vector3f position, float yaw, float pitch, float roll) {
        LocalPlayer player = this.minecraft.player;
        if (player != null) {
            player.moveTo(position.x, position.y, position.z, yaw, pitch);

            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null) {
                if (roll > -0.1 && roll < 0.1) {
                    editorState.replayVisuals.overrideRoll = false;
                    editorState.replayVisuals.overrideRollAmount = 0.0f;
                } else {
                    editorState.replayVisuals.overrideRoll = true;
                    editorState.replayVisuals.overrideRollAmount = roll;
                }
            }

            player.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public void applyFov(float fov) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            editorState.replayVisuals.setFov(fov);
        }
    }

    @Override
    public void applyTickrate(float tickrate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyTimeOfDay(int timeOfDay) {
        timeOfDay = timeOfDay % 24000;
        if (timeOfDay < 0) {
            timeOfDay += 24000;
        }

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            editorState.replayVisuals.overrideTimeOfDay = timeOfDay;
        }
    }

    @Override
    public void applyCameraShake(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            editorState.replayVisuals.setCameraShake(frequencyX, amplitudeX, frequencyY, amplitudeY);
        }
    }
}
