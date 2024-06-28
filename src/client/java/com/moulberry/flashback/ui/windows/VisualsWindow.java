package com.moulberry.flashback.ui.windows;

import com.moulberry.flashback.ReplayVisuals;
import imgui.ImGui;

public class VisualsWindow {

    public static void render() {
        if (ImGui.begin("Visuals")) {
            if (ImGui.checkbox("Show Boss Bar", ReplayVisuals.showBossBar)) {
                ReplayVisuals.showBossBar = !ReplayVisuals.showBossBar;
            }
            if (ImGui.checkbox("Show Title Text", ReplayVisuals.showTitleText)) {
                ReplayVisuals.showTitleText = !ReplayVisuals.showTitleText;
            }
            if (ImGui.checkbox("Show Scoreboard", ReplayVisuals.showScoreboard)) {
                ReplayVisuals.showScoreboard = !ReplayVisuals.showScoreboard;
            }
            if (ImGui.checkbox("Show Action Bar", ReplayVisuals.showActionBar)) {
                ReplayVisuals.showActionBar = !ReplayVisuals.showActionBar;
            }
            if (ImGui.checkbox("Show Nametags", ReplayVisuals.showNametags)) {
                ReplayVisuals.showNametags = !ReplayVisuals.showNametags;
            }

            // Fog distance
            boolean overridingFog = ReplayVisuals.overrideFogDistance > 0.0;
            if (ImGui.checkbox("Override Fog", overridingFog)) {
                if (overridingFog) {
                    ReplayVisuals.overrideFogDistance = 0.0f;
                } else {
                    ReplayVisuals.overrideFogDistance = 256.0f;
                }
            }
            if (overridingFog) {
                float[] fogDistance = new float[]{ReplayVisuals.overrideFogDistance};
                if (ImGui.sliderFloat("Override Fog Distance", fogDistance, 0.0f, 512.0f)) {
                    ReplayVisuals.overrideFogDistance = fogDistance[0];
                }
            }

        }
        ImGui.end();
    }
}
