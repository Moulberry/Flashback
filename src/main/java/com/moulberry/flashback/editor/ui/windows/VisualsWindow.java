package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.ReplayVisuals;
import com.moulberry.flashback.combo_options.Sizing;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import imgui.ImGui;
import net.minecraft.client.Minecraft;

public class VisualsWindow {

    private static final float[] floatBuffer = new float[]{0};
    private static final int[] intBuffer = new int[]{0};

    public static void render() {
        if (ImGui.begin("Visuals")) {
            ImGuiHelper.separatorWithText("GUI");

            if (ImGui.checkbox("Chat", ReplayVisuals.showChat)) {
                ReplayVisuals.showChat = !ReplayVisuals.showChat;
            }
            if (ImGui.checkbox("Boss Bar", ReplayVisuals.showBossBar)) {
                ReplayVisuals.showBossBar = !ReplayVisuals.showBossBar;
            }
            if (ImGui.checkbox("Title Text", ReplayVisuals.showTitleText)) {
                ReplayVisuals.showTitleText = !ReplayVisuals.showTitleText;
            }
            if (ImGui.checkbox("Scoreboard", ReplayVisuals.showScoreboard)) {
                ReplayVisuals.showScoreboard = !ReplayVisuals.showScoreboard;
            }
            if (ImGui.checkbox("Action Bar", ReplayVisuals.showActionBar)) {
                ReplayVisuals.showActionBar = !ReplayVisuals.showActionBar;
            }

            ImGuiHelper.separatorWithText("World");

            if (ImGui.checkbox("Render Blocks", ReplayVisuals.renderBlocks)) {
                ReplayVisuals.renderBlocks = !ReplayVisuals.renderBlocks;
            }

            if (ImGui.checkbox("Render Entities", ReplayVisuals.renderEntities)) {
                ReplayVisuals.renderEntities = !ReplayVisuals.renderEntities;
            }

            if (ImGui.checkbox("Render Players", ReplayVisuals.renderPlayers)) {
                ReplayVisuals.renderPlayers = !ReplayVisuals.renderPlayers;
            }

            if (ImGui.checkbox("Render Particles", ReplayVisuals.renderParticles)) {
                ReplayVisuals.renderParticles = !ReplayVisuals.renderParticles;
            }

            if (ImGui.checkbox("Render Sky", ReplayVisuals.renderSky)) {
                ReplayVisuals.renderSky = !ReplayVisuals.renderSky;
            }

            if (ImGui.checkbox("Render Nametags", ReplayVisuals.renderNametags)) {
                ReplayVisuals.renderNametags = !ReplayVisuals.renderNametags;
            }

            ImGuiHelper.separatorWithText("Overrides");

            // Fog distance
            if (ImGui.checkbox("Override Fog", ReplayVisuals.overrideFog)) {
                ReplayVisuals.overrideFog = !ReplayVisuals.overrideFog;
            }
            if (ReplayVisuals.overrideFog) {
                floatBuffer[0] = ReplayVisuals.overrideFogStart;
                if (ImGui.sliderFloat("Start", floatBuffer, 0.0f, 512.0f)) {
                    ReplayVisuals.overrideFogStart = floatBuffer[0];
                }

                floatBuffer[0] = ReplayVisuals.overrideFogEnd;
                if (ImGui.sliderFloat("End", floatBuffer, 0.0f, 512.0f)) {
                    ReplayVisuals.overrideFogEnd = floatBuffer[0];
                }
            }

            // FOV
            if (ImGui.checkbox("Override FOV", ReplayVisuals.overrideFov)) {
                ReplayVisuals.overrideFov = !ReplayVisuals.overrideFov;
            }
            if (ReplayVisuals.overrideFov) {
                floatBuffer[0] = ReplayVisuals.overrideFovAmount;
                if (ImGui.sliderFloat("FOV", floatBuffer, 0.1f, 110.0f, "%.1f")) {
                    ReplayVisuals.setFov(floatBuffer[0]);
                }
            }

            // Time
            if (ImGui.checkbox("Override Time", ReplayVisuals.overrideTimeOfDay >= 0)) {
                if (ReplayVisuals.overrideTimeOfDay >= 0) {
                    ReplayVisuals.overrideTimeOfDay = -1;
                } else {
                    ReplayVisuals.overrideTimeOfDay = (int)(Minecraft.getInstance().level.getDayTime() % 24000);
                }
            }
            if (ReplayVisuals.overrideTimeOfDay >= 0) {
                intBuffer[0] = (int) ReplayVisuals.overrideTimeOfDay;
                if (ImGui.sliderInt("Time", intBuffer, 0, 24000)) {
                    ReplayVisuals.overrideTimeOfDay = intBuffer[0];
                }
            }

            ImGuiHelper.separatorWithText("Other");

            if (ImGui.checkbox("Rule of Thirds Guide", ReplayVisuals.ruleOfThirdsGuide)) {
                ReplayVisuals.ruleOfThirdsGuide = !ReplayVisuals.ruleOfThirdsGuide;
            }

            if (ImGui.checkbox("Center Guide", ReplayVisuals.centerGuide)) {
                ReplayVisuals.centerGuide = !ReplayVisuals.centerGuide;
            }

            ReplayVisuals.sizing = ImGuiHelper.enumCombo("Sizing", ReplayVisuals.sizing);
            if (ReplayVisuals.sizing == Sizing.CHANGE_ASPECT_RATIO) {
                ReplayVisuals.changeAspectRatio = ImGuiHelper.enumCombo("Aspect", ReplayVisuals.changeAspectRatio);
            }
        }
        ImGui.end();
    }
}
