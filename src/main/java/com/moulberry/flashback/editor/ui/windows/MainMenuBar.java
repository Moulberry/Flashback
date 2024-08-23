package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.exporting.ExportJobQueue;
import imgui.ImGui;
import net.minecraft.client.Minecraft;

public class MainMenuBar {

    public static void render() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.menuItem("Export")) {
                StartExportWindow.open();
            }

            if (!ExportJobQueue.queuedJobs.isEmpty()) {
                String name = "Export Queue (" + ExportJobQueue.count() + ")";
                if (ImGui.menuItem(name + "###QueuedJobs")) {
                    ExportQueueWindow.open();
                }
            }

            if (ImGui.menuItem("Hide (F1)")) {
                Minecraft.getInstance().options.hideGui = true;
            }

            ImGui.endMainMenuBar();
        }
    }

}
