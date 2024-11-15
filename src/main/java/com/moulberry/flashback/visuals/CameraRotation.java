package com.moulberry.flashback.visuals;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import org.joml.Quaternionf;

public class CameraRotation {

    private static final FastNoiseLite fastNoiseLite = new FastNoiseLite();

    private static float shakeTimeY = 0.0f;
    private static float shakeTimeX = 0.0f;
    private static float lastShakeReplayTick = 0.0f;

    public static Quaternionf modifyViewQuaternion(Quaternionf quaternionf) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            return quaternionf;
        }

        if (ReplayUI.isMovingCamera()) {
            return quaternionf;
        }

        ReplayVisuals visuals = editorState.replayVisuals;
        quaternionf = new Quaternionf(quaternionf);

        if (visuals.overrideRoll) {
            quaternionf = quaternionf.rotateZ((float) Math.toRadians(visuals.overrideRollAmount));
        }

        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null && visuals.overrideCameraShake && (Flashback.isExporting() || !replayServer.replayPaused)) {
            float tick = Flashback.isExporting() ? (float) Flashback.EXPORT_JOB.getCurrentTickDouble() : replayServer.getPartialReplayTick();
            float speedFactor = 20.0f / replayServer.getDesiredTickRate(false);

            if (tick < lastShakeReplayTick) {
                lastShakeReplayTick = tick;
                shakeTimeY = 0.0f;
                shakeTimeX = 0.0f;
            } else if (tick > lastShakeReplayTick) {
                float yFrequency = visuals.cameraShakeYFrequency;
                float xFrequency = visuals.cameraShakeXFrequency;

                shakeTimeY += (tick - lastShakeReplayTick) * speedFactor * yFrequency * 2.0f;
                shakeTimeX += (tick - lastShakeReplayTick) * speedFactor * xFrequency * 2.0f;
                lastShakeReplayTick = tick;
            }

            float yAmplitude = visuals.cameraShakeYAmplitude;
            float xAmplitude = visuals.cameraShakeXAmplitude;

            float yRot = fastNoiseLite.GetNoise(shakeTimeX, -10000) * 2*(float)Math.PI;
            float xRot = fastNoiseLite.GetNoise(-10000, shakeTimeY) * 2*(float)Math.PI;
            quaternionf = quaternionf.rotateYXZ(yRot * xAmplitude/360, xRot * yAmplitude/360, 0);
        }
        return quaternionf;
    }

}
