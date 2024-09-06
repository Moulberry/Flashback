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
        if (ImGui.menuItem("Export Video")) {
            StartExportWindow.open();
        }

        if (!ExportJobQueue.queuedJobs.isEmpty()) {
            String name = "Export Queue (" + ExportJobQueue.count() + ")";
            if (ImGui.menuItem(name + "###QueuedJobs")) {
                ExportQueueWindow.open();
            }
        }

        ImGui.separator();

        if (ImGui.menuItem("Player List")) {
            toggleWindow("player_list");
        }
        if (ImGui.menuItem("Movement")) {
            toggleWindow("movement");
        }

        ImGui.separator();

        if (ImGui.menuItem("Hide (F1)")) {
            Minecraft.getInstance().options.hideGui = true;
        }
    }

    private static void toggleWindow(String windowName) {
        var openedWindows = Flashback.getConfig().openedWindows;
        boolean playerListIsOpen = openedWindows.contains(windowName);
        if (playerListIsOpen) {
            openedWindows.remove(windowName);
        } else {
            openedWindows.add(windowName);
        }
        Flashback.getConfig().saveToDefaultFolder();
    }

}
