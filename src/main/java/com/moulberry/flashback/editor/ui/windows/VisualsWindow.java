package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.impl.CameraShakeKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.visuals.ReplayVisuals;
import com.moulberry.flashback.combo_options.Sizing;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import imgui.ImGui;
import net.minecraft.client.Minecraft;

import java.util.List;

public class VisualsWindow {

    private static final float[] floatBuffer = new float[]{0};
    private static final int[] intBuffer = new int[]{0};

    public static void render() {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            return;
        }

        FlashbackMeta metadata = replayServer.getMetadata();
        EditorState editorState = EditorStateManager.get(metadata.replayIdentifier);
        ReplayVisuals visuals = editorState.replayVisuals;

        if (ImGui.begin("Visuals")) {
            ImGuiHelper.separatorWithText("GUI");

            if (ImGui.checkbox("Chat", visuals.showChat)) {
                visuals.showChat = !visuals.showChat;
                editorState.markDirty();
            }
            if (ImGui.checkbox("Boss Bar", visuals.showBossBar)) {
                visuals.showBossBar = !visuals.showBossBar;
                editorState.markDirty();
            }
            if (ImGui.checkbox("Title Text", visuals.showTitleText)) {
                visuals.showTitleText = !visuals.showTitleText;
                editorState.markDirty();
            }
            if (ImGui.checkbox("Scoreboard", visuals.showScoreboard)) {
                visuals.showScoreboard = !visuals.showScoreboard;
                editorState.markDirty();
            }
            if (ImGui.checkbox("Action Bar", visuals.showActionBar)) {
                visuals.showActionBar = !visuals.showActionBar;
                editorState.markDirty();
            }

            if (Minecraft.getInstance().cameraEntity != null && Minecraft.getInstance().cameraEntity != Minecraft.getInstance().player) {
                if (ImGui.checkbox("Hotbar", visuals.showHotbar)) {
                    visuals.showHotbar = !visuals.showHotbar;
                    editorState.markDirty();
                }
            }

            ImGuiHelper.separatorWithText("World");

            if (ImGui.checkbox("Render Blocks", visuals.renderBlocks)) {
                visuals.renderBlocks = !visuals.renderBlocks;
                editorState.markDirty();
            }

            if (ImGui.checkbox("Render Entities", visuals.renderEntities)) {
                visuals.renderEntities = !visuals.renderEntities;
                editorState.markDirty();
            }

            if (ImGui.checkbox("Render Players", visuals.renderPlayers)) {
                visuals.renderPlayers = !visuals.renderPlayers;
                editorState.markDirty();
            }

            if (ImGui.checkbox("Render Particles", visuals.renderParticles)) {
                visuals.renderParticles = !visuals.renderParticles;
                editorState.markDirty();
            }

            if (ImGui.checkbox("Render Sky", visuals.renderSky)) {
                visuals.renderSky = !visuals.renderSky;
                editorState.markDirty();
            }

            if (!visuals.renderSky) {
                if (ImGui.colorButton("Sky Colour", visuals.skyColour)) {
                    ImGui.openPopup("##EditSkyColour");
                }
                ImGui.sameLine();
                ImGui.text("Sky Colour");

                if (ImGui.beginPopup("##EditSkyColour")) {
                    ImGui.colorPicker3("Sky Colour", visuals.skyColour);
                    ImGui.endPopup();
                }
            }

            if (ImGui.checkbox("Render Nametags", visuals.renderNametags)) {
                visuals.renderNametags = !visuals.renderNametags;
                editorState.markDirty();
            }

            ImGuiHelper.separatorWithText("Overrides");

            // Fog distance
            if (ImGui.checkbox("Override Fog", visuals.overrideFog)) {
                visuals.overrideFog = !visuals.overrideFog;
                editorState.markDirty();
            }
            if (visuals.overrideFog) {
                floatBuffer[0] = visuals.overrideFogStart;
                if (ImGui.sliderFloat("Start", floatBuffer, 0.0f, 512.0f)) {
                    visuals.overrideFogStart = floatBuffer[0];
                    editorState.markDirty();
                }

                floatBuffer[0] = visuals.overrideFogEnd;
                if (ImGui.sliderFloat("End", floatBuffer, 0.0f, 512.0f)) {
                    visuals.overrideFogEnd = floatBuffer[0];
                    editorState.markDirty();
                }
            }

            if (ImGui.checkbox("Override Fog Colour", visuals.overrideFogColour)) {
                visuals.overrideFogColour = !visuals.overrideFogColour;
                editorState.markDirty();
            }
            if (visuals.overrideFogColour) {
                if (ImGui.colorButton("Fog Colour", visuals.fogColour)) {
                    ImGui.openPopup("##EditFogColour");
                }
                ImGui.sameLine();
                ImGui.text("Fog Colour");

                if (ImGui.beginPopup("##EditFogColour")) {
                    ImGui.colorPicker3("Fog Colour", visuals.fogColour);
                    ImGui.endPopup();
                }
            }

            // FOV
            if (ImGui.checkbox("Override FOV", visuals.overrideFov)) {
                visuals.overrideFov = !visuals.overrideFov;
                Minecraft.getInstance().levelRenderer.needsUpdate();
                editorState.markDirty();
            }
            if (visuals.overrideFov) {
                if (visuals.overrideFovAmount < 0) {
                    visuals.overrideFovAmount = Flashback.getConfig().defaultOverrideFov;
                }

                ImGui.sameLine();
                if (ImGui.smallButton("+")) {
                    addKeyframe(editorState, replayServer, new FOVKeyframe(visuals.overrideFovAmount));
                }
                ImGuiHelper.tooltip("Add FOV keyframe");

                floatBuffer[0] = visuals.overrideFovAmount;
                if (ImGui.sliderFloat("FOV", floatBuffer, 1.0f, 110.0f, "%.1f")) {
                    visuals.setFov(floatBuffer[0]);
                    editorState.markDirty();
                }
            }

            // Time
            if (ImGui.checkbox("Override Time", visuals.overrideTimeOfDay >= 0)) {
                if (visuals.overrideTimeOfDay >= 0) {
                    visuals.overrideTimeOfDay = -1;
                } else {
                    visuals.overrideTimeOfDay = (int)(Minecraft.getInstance().level.getDayTime() % 24000);
                }
                editorState.markDirty();
            }
            if (visuals.overrideTimeOfDay >= 0) {
                ImGui.sameLine();
                if (ImGui.smallButton("+")) {
                    addKeyframe(editorState, replayServer, new TimeOfDayKeyframe((int) visuals.overrideTimeOfDay));
                }
                ImGuiHelper.tooltip("Add Time keyframe");

                intBuffer[0] = (int) visuals.overrideTimeOfDay;
                if (ImGui.sliderInt("Time", intBuffer, 0, 24000)) {
                    visuals.overrideTimeOfDay = intBuffer[0];
                    editorState.markDirty();
                }
            }

            // Night vision
            if (ImGui.checkbox("Night Vision", visuals.overrideNightVision)) {
                visuals.overrideNightVision = !visuals.overrideNightVision;
            }

            // Camera shake
            if (ImGui.checkbox("Camera Shake", visuals.overrideCameraShake)) {
                visuals.overrideCameraShake = !visuals.overrideCameraShake;
                editorState.markDirty();
            }
            if (visuals.overrideCameraShake) {
                ImGui.sameLine();
                if (ImGui.smallButton("+")) {
                    if (visuals.cameraShakeSplitParams) {
                        addKeyframe(editorState, replayServer, new CameraShakeKeyframe(visuals.cameraShakeXFrequency, visuals.cameraShakeXAmplitude,
                            visuals.cameraShakeYFrequency, visuals.cameraShakeYAmplitude, true));
                    } else {
                        float frequency = (visuals.cameraShakeXFrequency + visuals.cameraShakeYFrequency)/2.0f;
                        float amplitude = (visuals.cameraShakeXAmplitude + visuals.cameraShakeYAmplitude)/2.0f;
                        addKeyframe(editorState, replayServer, new CameraShakeKeyframe(frequency, amplitude, frequency, amplitude, false));
                    }
                }
                ImGuiHelper.tooltip("Add Camera Shake keyframe");

                if (ImGui.checkbox("Split Y/X", visuals.cameraShakeSplitParams)) {
                    visuals.cameraShakeSplitParams = !visuals.cameraShakeSplitParams;
                    editorState.markDirty();
                }

                if (visuals.cameraShakeSplitParams) {
                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = visuals.cameraShakeXFrequency;
                    if (ImGui.sliderFloat("Frequency X", floatBuffer, 0.1f, 10.0f, "%.1f")) {
                        visuals.cameraShakeXFrequency = floatBuffer[0];
                        editorState.markDirty();
                    }

                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = visuals.cameraShakeXAmplitude;
                    if (ImGui.sliderFloat("Amplitude X", floatBuffer, 0.0f, 10.0f, "%.1f")) {
                        visuals.cameraShakeXAmplitude = floatBuffer[0];
                        editorState.markDirty();
                    }

                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = visuals.cameraShakeYFrequency;
                    if (ImGui.sliderFloat("Frequency Y", floatBuffer, 0.1f, 10.0f, "%.1f")) {
                        visuals.cameraShakeYFrequency = floatBuffer[0];
                        editorState.markDirty();
                    }

                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = visuals.cameraShakeYAmplitude;
                    if (ImGui.sliderFloat("Amplitude Y", floatBuffer, 0.0f, 10.0f, "%.1f")) {
                        visuals.cameraShakeYAmplitude = floatBuffer[0];
                        editorState.markDirty();
                    }
                } else {
                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = (visuals.cameraShakeXFrequency + visuals.cameraShakeYFrequency)/2.0f;
                    if (ImGui.sliderFloat("Frequency", floatBuffer, 0.1f, 10.0f, "%.1f")) {
                        visuals.cameraShakeXFrequency = floatBuffer[0];
                        visuals.cameraShakeYFrequency = floatBuffer[0];
                        editorState.markDirty();
                    }

                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = (visuals.cameraShakeXAmplitude + visuals.cameraShakeYAmplitude)/2.0f;
                    if (ImGui.sliderFloat("Amplitude", floatBuffer, 0.0f, 10.0f, "%.1f")) {
                        visuals.cameraShakeXAmplitude = floatBuffer[0];
                        visuals.cameraShakeYAmplitude = floatBuffer[0];
                        editorState.markDirty();
                    }
                }
            }

            // Camera Roll
            if (ImGui.checkbox("Camera Roll", visuals.overrideRoll)) {
                visuals.overrideRoll = !visuals.overrideRoll;
                Minecraft.getInstance().levelRenderer.needsUpdate();
                editorState.markDirty();
            }
            if (visuals.overrideRoll) {
                floatBuffer[0] = visuals.overrideRollAmount;
                if (ImGui.sliderFloat("Roll", floatBuffer, -180.0f, 180.0f, "%.1f")) {
                    visuals.overrideRollAmount = floatBuffer[0];
                    Minecraft.getInstance().levelRenderer.needsUpdate();
                    editorState.markDirty();
                }
            }

            visuals.overrideWeatherMode = ImGuiHelper.enumCombo("Weather", visuals.overrideWeatherMode);

            ImGuiHelper.separatorWithText("Other");

            if (ImGui.checkbox("Rule of Thirds Guide", visuals.ruleOfThirdsGuide)) {
                visuals.ruleOfThirdsGuide = !visuals.ruleOfThirdsGuide;
                editorState.markDirty();
            }

            if (ImGui.checkbox("Center Guide", visuals.centerGuide)) {
                visuals.centerGuide = !visuals.centerGuide;
                editorState.markDirty();
            }

            if (ImGui.checkbox("Camera Path", visuals.cameraPath)) {
                visuals.cameraPath = !visuals.cameraPath;
                editorState.markDirty();
            }

            visuals.sizing = ImGuiHelper.enumCombo("Sizing", visuals.sizing);
            if (visuals.sizing == Sizing.CHANGE_ASPECT_RATIO) {
                visuals.changeAspectRatio = ImGuiHelper.enumCombo("Aspect", visuals.changeAspectRatio);
                editorState.markDirty();
            }

            if (!editorState.hideDuringExport.isEmpty()) {
                if (ImGui.button("Unhide All Entities")) {
                    editorState.hideDuringExport.clear();
                    editorState.markDirty();
                }
            }
        }
        ImGui.end();
    }

    private static void addKeyframe(EditorState editorState, ReplayServer replayServer, Keyframe keyframe) {
        KeyframeType<?> keyframeType = keyframe.keyframeType();
        long stamp = editorState.acquireWrite();
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);

            // Try add to existing enabled keyframe track
            for (int i = 0; i < scene.keyframeTracks.size(); i++) {
                KeyframeTrack keyframeTrack = scene.keyframeTracks.get(i);
                if (keyframeTrack.enabled && keyframeTrack.keyframeType == keyframeType) {
                    scene.setKeyframe(i, replayServer.getReplayTick(), keyframe);
                    return;
                }
            }

            // Try add to any keyframe track
            for (int i = 0; i < scene.keyframeTracks.size(); i++) {
                KeyframeTrack keyframeTrack = scene.keyframeTracks.get(i);
                if (keyframeTrack.keyframeType == keyframeType) {
                    scene.setKeyframe(i, replayServer.getReplayTick(), keyframe);
                    return;
                }
            }

            String description = "Added " + keyframeType.name() + " keyframe";
            int newKeyframeTrackIndex = scene.keyframeTracks.size();
            scene.push(new EditorSceneHistoryEntry(
                List.of(new EditorSceneHistoryAction.RemoveTrack(keyframeType, newKeyframeTrackIndex)),
                List.of(
                    new EditorSceneHistoryAction.AddTrack(keyframeType, newKeyframeTrackIndex),
                    new EditorSceneHistoryAction.SetKeyframe(keyframeType, newKeyframeTrackIndex, replayServer.getReplayTick(), keyframe)
                ),
                description
            ));
            editorState.markDirty();
        } finally {
            editorState.release(stamp);
        }
    }
}
