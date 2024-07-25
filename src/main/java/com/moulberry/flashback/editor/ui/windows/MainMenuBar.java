package com.moulberry.flashback.editor.ui.windows;

import imgui.ImGui;

public class MainMenuBar {

    public static void render() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.menuItem("Export")) {
                StartExportWindow.open();
            }

            ImGui.endMainMenuBar();
        }
    }

}
