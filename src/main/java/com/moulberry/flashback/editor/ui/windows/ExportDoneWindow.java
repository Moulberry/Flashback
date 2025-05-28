package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import net.minecraft.Util;

import java.nio.file.Files;
import java.nio.file.Path;

public class ExportDoneWindow {

    public static boolean exportDoneWindowOpen = false;
    private static Path exportPath = null;

    public static void open() {
        exportDoneWindowOpen = true;
        exportPath = null;

        FlashbackConfig config = Flashback.getConfig();
        if (config.defaultExportPath != null) {
            Path path = Path.of(config.defaultExportPath);
            if (Files.exists(path)) {
                while (path != null && !Files.isDirectory(path)) {
                    path = path.getParent();
                }
                exportPath = path;
            }
        }
    }

    public static void render() {
        if (!exportDoneWindowOpen) {
            return;
        }

        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing, 0.5f, 0.5f);

        ImBoolean open = new ImBoolean(true);

        ImGui.setNextWindowSizeConstraints(250, 50, 5000, 5000);
        int flags = ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoSavedSettings;

        ImGui.openPopup("###ExportDone");
        if (ImGui.beginPopupModal("Export Finished###ExportDone", open, flags)) {
            ImGui.text("Export has successfully finished");
            if (exportPath != null && ImGui.button("Open Folder")) {
                Util.getPlatform().openFile(exportPath.toFile());
            }
            ImGui.endPopup();
        }

        if (!open.get()) {
            exportDoneWindowOpen = false;
        }
    }

}
