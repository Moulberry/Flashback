package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPosition;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraShake;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.impl.CameraShakeKeyframe;
import com.moulberry.flashback.keyframe.impl.TickrateKeyframe;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;
import org.jetbrains.annotations.Nullable;

public class CameraShakeKeyframeType implements KeyframeType<CameraShakeKeyframe> {

    public static CameraShakeKeyframeType INSTANCE = new CameraShakeKeyframeType();

    private CameraShakeKeyframeType() {
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangeCameraShake.class;
    }

    @Override
    public @Nullable String icon() {
        return "\uefeb";
    }

    @Override
    public String name() {
        return "Camera Shake";
    }

    @Override
    public String id() {
        return "CAMERA_SHAKE";
    }

    @Override
    public @Nullable CameraShakeKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<CameraShakeKeyframe> createPopup() {
        boolean[] cameraShakeSplitXYKeyframeInput = new boolean[]{false};
        float[] cameraShakeFrequencyXKeyframeInput = new float[]{1.0f};
        float[] cameraShakeAmplitudeXKeyframeInput = new float[]{0.0f};
        float[] cameraShakeFrequencyYKeyframeInput = new float[]{1.0f};
        float[] cameraShakeAmplitudeYKeyframeInput = new float[]{0.0f};

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideCameraShake) {
            cameraShakeSplitXYKeyframeInput[0] = editorState.replayVisuals.cameraShakeSplitParams;
            cameraShakeFrequencyXKeyframeInput[0] = editorState.replayVisuals.cameraShakeXFrequency;
            cameraShakeAmplitudeXKeyframeInput[0] = editorState.replayVisuals.cameraShakeXAmplitude;
            cameraShakeFrequencyYKeyframeInput[0] = editorState.replayVisuals.cameraShakeYFrequency;
            cameraShakeAmplitudeYKeyframeInput[0] = editorState.replayVisuals.cameraShakeYAmplitude;
        }

        return () -> {
            if (ImGui.checkbox("Split Y/X", cameraShakeSplitXYKeyframeInput[0])) {
                cameraShakeSplitXYKeyframeInput[0] = !cameraShakeSplitXYKeyframeInput[0];
                if (!cameraShakeSplitXYKeyframeInput[0]) {
                    cameraShakeFrequencyYKeyframeInput[0] = cameraShakeFrequencyXKeyframeInput[0];
                    cameraShakeAmplitudeYKeyframeInput[0] = cameraShakeAmplitudeXKeyframeInput[0];
                }
            }

            if (cameraShakeSplitXYKeyframeInput[0]) {
                ImGui.sliderFloat("Frequency X", cameraShakeFrequencyXKeyframeInput, 0.1f, 10.0f, "%.1f");
                ImGui.sliderFloat("Amplitude X", cameraShakeAmplitudeXKeyframeInput, 0.0f, 10.0f, "%.1f");
                ImGui.sliderFloat("Frequency Y", cameraShakeFrequencyYKeyframeInput, 0.1f, 10.0f, "%.1f");
                ImGui.sliderFloat("Amplitude Y", cameraShakeAmplitudeYKeyframeInput, 0.0f, 10.0f, "%.1f");
            } else {
                ImGui.sliderFloat("Frequency", cameraShakeFrequencyXKeyframeInput, 0.1f, 10.0f, "%.1f");
                ImGui.sliderFloat("Amplitude", cameraShakeAmplitudeXKeyframeInput, 0.0f, 10.0f, "%.1f");
            }

            if (ImGui.button("Add")) {
                if (cameraShakeSplitXYKeyframeInput[0]) {
                    return new CameraShakeKeyframe(cameraShakeFrequencyXKeyframeInput[0], cameraShakeAmplitudeXKeyframeInput[0],
                        cameraShakeFrequencyYKeyframeInput[0], cameraShakeAmplitudeYKeyframeInput[0], true);
                } else {
                    return new CameraShakeKeyframe(cameraShakeFrequencyXKeyframeInput[0], cameraShakeAmplitudeXKeyframeInput[0],
                        cameraShakeFrequencyXKeyframeInput[0], cameraShakeAmplitudeXKeyframeInput[0], false);
                }
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
