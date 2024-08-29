package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.exporting.ExportJobQueue;
import imgui.ImGui;
import net.minecraft.client.Minecraft;

public class MainMenuBar {

    public static void render() {
        if (ImGui.beginMainMenuBar()) {
            renderInner();
            ImGui.endMainMenuBar();
        }
    }

    public static void renderInner() {
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

        if (ImGui.menuItem("Player List")) {
            var openedWindows = Flashback.getConfig().openedWindows;
            boolean playerListIsOpen = openedWindows.contains("player_list");
            if (playerListIsOpen) {
                openedWindows.remove("player_list");
            } else {
                openedWindows.add("player_list");
            }
            Flashback.getConfig().saveToDefaultFolder();
        }
    }

}
