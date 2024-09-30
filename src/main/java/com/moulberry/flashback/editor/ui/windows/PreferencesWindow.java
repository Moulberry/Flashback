package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImShort;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.nio.file.Path;

public class PreferencesWindow {

    private static boolean open = false;
    private static boolean close = false;

    public static void render() {
        if (open) {
            open = false;
            ImGui.openPopup("###Preferences");
        }

        boolean wasOpen = ImGui.isPopupOpen("###Preferences");

        if (ImGuiHelper.beginPopupModalCloseable("Preferences###Preferences", ImGuiWindowFlags.AlwaysAutoResize)) {
            if (close) {
                close = false;
                ImGui.closeCurrentPopup();
                ImGuiHelper.endPopupModalCloseable();
                return;
            }

            FlashbackConfig config = Flashback.getConfig();

            // Exporting
            ImGuiHelper.separatorWithText("Exporting");

            ImString imString = ImGuiHelper.createResizableImString(config.defaultExportFilename);
            if (ImGui.inputText("Export Filename", imString)) {
                config.defaultExportFilename = ImGuiHelper.getString(imString);
            }
            ImGuiHelper.tooltip("The default filename when exporting\nVariables:\n\t%date%\tyear-month-day\n\t%time%\thh_mm_ss\n\t%replay%\tReplay name\n\t%seq%\tExport count for this session");

            // Keyframes
            ImGuiHelper.separatorWithText("Keyframes");

            config.defaultInterpolationType = ImGuiHelper.enumCombo("Default Interpolation", config.defaultInterpolationType);

            ImGuiHelper.endPopupModalCloseable();
        }

        if (wasOpen && !ImGui.isPopupOpen("###Preferences")) {
            Flashback.getConfig().saveToDefaultFolder();
        }

        close = false;
    }

    public static void open() {
        open = true;
    }

}
