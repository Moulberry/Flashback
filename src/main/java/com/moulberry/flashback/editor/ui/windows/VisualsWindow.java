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
import imgui.flashback.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

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

        if (ImGui.begin(I18n.get("flashback.visuals") + "###Visuals")) {
            ImGuiHelper.separatorWithText(I18n.get("flashback.visuals.gui"));

            if (ImGui.checkbox(I18n.get("flashback.visuals.gui.chat"), visuals.showChat)) {
                visuals.showChat = !visuals.showChat;
                editorState.markDirty();
            }

            if (ImGui.checkbox(I18n.get("flashback.visuals.gui.boss_bar"), visuals.showBossBar)) {
                visuals.showBossBar = !visuals.showBossBar;
                editorState.markDirty();
            }
            if (ImGui.checkbox(I18n.get("flashback.visuals.gui.title_text"), visuals.showTitleText)) {
                visuals.showTitleText = !visuals.showTitleText;
                editorState.markDirty();
            }
            if (ImGui.checkbox(I18n.get("flashback.visuals.gui.scoreboard"), visuals.showScoreboard)) {
                visuals.showScoreboard = !visuals.showScoreboard;
                editorState.markDirty();
            }
            if (ImGui.checkbox(I18n.get("flashback.visuals.gui.action_bar"), visuals.showActionBar)) {
                visuals.showActionBar = !visuals.showActionBar;
                editorState.markDirty();
            }

            if (Minecraft.getInstance().getCameraEntity() != null && Minecraft.getInstance().getCameraEntity() != Minecraft.getInstance().player) {
                if (ImGui.checkbox(I18n.get("flashback.visuals.gui.hotbar"), visuals.showHotbar)) {
                    visuals.showHotbar = !visuals.showHotbar;
                    editorState.markDirty();
                }
            }

            ImGuiHelper.separatorWithText(I18n.get("flashback.visuals.world"));

            if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_blocks"), visuals.renderBlocks)) {
                visuals.renderBlocks = !visuals.renderBlocks;
                editorState.markDirty();
            }

            if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_entities"), visuals.renderEntities)) {
                visuals.renderEntities = !visuals.renderEntities;
                editorState.markDirty();
            }

            if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_players"), visuals.renderPlayers)) {
                visuals.renderPlayers = !visuals.renderPlayers;
                editorState.markDirty();
            }

            if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_particles"), visuals.renderParticles)) {
                visuals.renderParticles = !visuals.renderParticles;
                editorState.markDirty();
            }

            if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_sky"), visuals.renderSky)) {
                visuals.renderSky = !visuals.renderSky;
                editorState.markDirty();
            }

            if (!visuals.renderSky) {
                if (ImGui.colorButton(I18n.get("flashback.visuals.world.sky_colour"), visuals.skyColour)) {
                    ImGui.openPopup("##EditSkyColour");
                }
                ImGui.sameLine();
                ImGui.textUnformatted(I18n.get("flashback.visuals.world.sky_colour"));

                if (ImGui.beginPopup("##EditSkyColour")) {
                    ImGui.colorPicker3(I18n.get("flashback.visuals.world.sky_colour"), visuals.skyColour);
                    ImGui.endPopup();
                }
            }

            if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_nametags"), visuals.renderNametags)) {
                visuals.renderNametags = !visuals.renderNametags;
                editorState.markDirty();
            }

            ImGuiHelper.separatorWithText(I18n.get("flashback.visuals.overrides"));

            // Fog distance
            if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.override_fog"), visuals.overrideFog)) {
                visuals.overrideFog = !visuals.overrideFog;
                editorState.markDirty();
            }
            if (visuals.overrideFog) {
                floatBuffer[0] = visuals.overrideFogStart;
                if (ImGui.sliderFloat(I18n.get("flashback.fog_start"), floatBuffer, 0.0f, 512.0f)) {
                    visuals.overrideFogStart = floatBuffer[0];
                    editorState.markDirty();
                }

                floatBuffer[0] = visuals.overrideFogEnd;
                if (ImGui.sliderFloat(I18n.get("flashback.fog_end"), floatBuffer, 0.0f, 512.0f)) {
                    visuals.overrideFogEnd = floatBuffer[0];
                    editorState.markDirty();
                }
            }

            if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.override_fog_colour"), visuals.overrideFogColour)) {
                visuals.overrideFogColour = !visuals.overrideFogColour;
                editorState.markDirty();
            }
            if (visuals.overrideFogColour) {
                if (ImGui.colorButton(I18n.get("flashback.visuals.overrides.fog_colour"), visuals.fogColour)) {
                    ImGui.openPopup("##EditFogColour");
                }
                ImGui.sameLine();
                ImGui.textUnformatted(I18n.get("flashback.visuals.overrides.fog_colour"));

                if (ImGui.beginPopup("##EditFogColour")) {
                    ImGui.colorPicker3(I18n.get("flashback.visuals.overrides.fog_colour"), visuals.fogColour);
                    ImGui.endPopup();
                }
            }

            // FOV
            if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.override_fov"), visuals.overrideFov)) {
                visuals.overrideFov = !visuals.overrideFov;
                Minecraft.getInstance().levelRenderer.needsUpdate();
                editorState.markDirty();
            }
            if (visuals.overrideFov) {
                if (visuals.overrideFovAmount < 0) {
                    visuals.overrideFovAmount = Flashback.getConfig().internal.defaultOverrideFov;
                }

                ImGui.sameLine();
                if (ImGui.smallButton("+")) {
                    addKeyframe(editorState, replayServer, new FOVKeyframe(visuals.overrideFovAmount));
                }
                ImGuiHelper.tooltip(I18n.get("flashback.add_fov_keyframe"));

                floatBuffer[0] = visuals.overrideFovAmount;
                if (ImGui.sliderFloat(I18n.get("flashback.fov"), floatBuffer, 1.0f, 110.0f, "%.1f")) {
                    visuals.setFov(floatBuffer[0]);
                    editorState.markDirty();
                }
            }

            // Time
            if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.override_time"), visuals.overrideTimeOfDay >= 0)) {
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
                ImGuiHelper.tooltip(I18n.get("flashback.add_time_keyframe"));

                intBuffer[0] = (int) visuals.overrideTimeOfDay;
                if (ImGui.sliderInt(I18n.get("flashback.time"), intBuffer, 0, 24000)) {
                    visuals.overrideTimeOfDay = intBuffer[0];
                    editorState.markDirty();
                }
            }

            // Night vision
            if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.night_vision"), visuals.overrideNightVision)) {
                visuals.overrideNightVision = !visuals.overrideNightVision;
            }

            // Camera shake
            if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.camera_shake"), visuals.overrideCameraShake)) {
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
                ImGuiHelper.tooltip(I18n.get("flashback.add_camera_shake_keyframe"));

                if (ImGui.checkbox(I18n.get("flashback.split_yx"), visuals.cameraShakeSplitParams)) {
                    visuals.cameraShakeSplitParams = !visuals.cameraShakeSplitParams;
                    editorState.markDirty();
                }

                if (visuals.cameraShakeSplitParams) {
                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = visuals.cameraShakeXFrequency;
                    if (ImGui.sliderFloat(I18n.get("flashback.frequency_x"), floatBuffer, 0.1f, 10.0f, "%.1f")) {
                        visuals.cameraShakeXFrequency = floatBuffer[0];
                        editorState.markDirty();
                    }

                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = visuals.cameraShakeXAmplitude;
                    if (ImGui.sliderFloat(I18n.get("flashback.amplitude_x"), floatBuffer, 0.0f, 10.0f, "%.1f")) {
                        visuals.cameraShakeXAmplitude = floatBuffer[0];
                        editorState.markDirty();
                    }

                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = visuals.cameraShakeYFrequency;
                    if (ImGui.sliderFloat(I18n.get("flashback.frequency_y"), floatBuffer, 0.1f, 10.0f, "%.1f")) {
                        visuals.cameraShakeYFrequency = floatBuffer[0];
                        editorState.markDirty();
                    }

                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = visuals.cameraShakeYAmplitude;
                    if (ImGui.sliderFloat(I18n.get("flashback.amplitude_y"), floatBuffer, 0.0f, 10.0f, "%.1f")) {
                        visuals.cameraShakeYAmplitude = floatBuffer[0];
                        editorState.markDirty();
                    }
                } else {
                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = (visuals.cameraShakeXFrequency + visuals.cameraShakeYFrequency)/2.0f;
                    if (ImGui.sliderFloat(I18n.get("flashback.frequency"), floatBuffer, 0.1f, 10.0f, "%.1f")) {
                        visuals.cameraShakeXFrequency = floatBuffer[0];
                        visuals.cameraShakeYFrequency = floatBuffer[0];
                        editorState.markDirty();
                    }

                    ImGui.setNextItemWidth(100);
                    floatBuffer[0] = (visuals.cameraShakeXAmplitude + visuals.cameraShakeYAmplitude)/2.0f;
                    if (ImGui.sliderFloat(I18n.get("flashback.amplitude"), floatBuffer, 0.0f, 10.0f, "%.1f")) {
                        visuals.cameraShakeXAmplitude = floatBuffer[0];
                        visuals.cameraShakeYAmplitude = floatBuffer[0];
                        editorState.markDirty();
                    }
                }
            }

            // Camera Roll
            if (ImGui.checkbox(I18n.get("flashback.camera_roll"), visuals.overrideRoll)) {
                visuals.overrideRoll = !visuals.overrideRoll;
                Minecraft.getInstance().levelRenderer.needsUpdate();
                editorState.markDirty();
            }
            if (visuals.overrideRoll) {
                floatBuffer[0] = visuals.overrideRollAmount;
                if (ImGui.sliderFloat(I18n.get("flashback.roll"), floatBuffer, -180.0f, 180.0f, "%.1f")) {
                    visuals.overrideRollAmount = floatBuffer[0];
                    Minecraft.getInstance().levelRenderer.needsUpdate();
                    editorState.markDirty();
                }
            }

            visuals.overrideWeatherMode = ImGuiHelper.enumCombo(I18n.get("flashback.weather"), visuals.overrideWeatherMode);

            ImGuiHelper.separatorWithText(I18n.get("flashback.other"));

            if (ImGui.checkbox(I18n.get("flashback.rule_of_thirds_guide"), visuals.ruleOfThirdsGuide)) {
                visuals.ruleOfThirdsGuide = !visuals.ruleOfThirdsGuide;
                editorState.markDirty();
            }

            if (ImGui.checkbox(I18n.get("flashback.center_guide"), visuals.centerGuide)) {
                visuals.centerGuide = !visuals.centerGuide;
                editorState.markDirty();
            }

            if (ImGui.checkbox(I18n.get("flashback.camera_path"), visuals.cameraPath)) {
                visuals.cameraPath = !visuals.cameraPath;
                editorState.markDirty();
            }

            visuals.sizing = ImGuiHelper.enumCombo(I18n.get("flashback.sizing"), visuals.sizing);
            if (visuals.sizing == Sizing.CHANGE_ASPECT_RATIO) {
                visuals.changeAspectRatio = ImGuiHelper.enumCombo(I18n.get("flashback.aspect"), visuals.changeAspectRatio);
                editorState.markDirty();
            }

            if (!editorState.hideDuringExport.isEmpty()) {
                if (ImGui.button(I18n.get("flashback.unhide_all_entities"))) {
                    editorState.hideDuringExport.clear();
                    editorState.markDirty();
                }
            }

            if (replayServer.hasServerResourcePack) {
                if (ImGui.checkbox(I18n.get("flashback.disable_server_resource_packs"), visuals.disableServerResourcePack)) {
                    visuals.disableServerResourcePack = !visuals.disableServerResourcePack;
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

            String description = I18n.get("flashback.added_named_keyframe", keyframeType.name());
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
