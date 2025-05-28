package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
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

        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(ReplayUI.scaleUi(400), 0);
        if (ImGuiHelper.beginPopupModalCloseable("Preferences###Preferences", ImGuiWindowFlags.NoResize)) {
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
            ImGui.setNextItemWidth(ReplayUI.scaleUi(200));
            if (ImGui.inputText("Export Filename", imString)) {
                config.defaultExportFilename = ImGuiHelper.getString(imString);
                config.delayedSaveToDefaultFolder();
            }
            ImGuiHelper.tooltip("The default filename when exporting\nVariables:\n\t%date%\tyear-month-day\n\t%time%\thh_mm_ss\n\t%replay%\tReplay name\n\t%seq%\tExport count for this session");

            // Keyframes
            ImGuiHelper.separatorWithText("Keyframes");

            ImGui.setNextItemWidth(ReplayUI.scaleUi(200));
            config.defaultInterpolationType = ImGuiHelper.enumCombo("Default Interpolation", config.defaultInterpolationType);

            if (ImGui.checkbox("Use Realtime Interpolation", config.useRealtimeInterpolation)) {
                config.useRealtimeInterpolation = !config.useRealtimeInterpolation;
            }

            if (ImGui.collapsingHeader("Advanced")) {
                ImGui.textWrapped("Don't change any of these unless you know what you're doing!! If you change one of these and then ask for support you will be made fun of!!");
                if (ImGui.checkbox("Disable increased first-person updates", config.disableIncreasedFirstPersonUpdates)) {
                    config.disableIncreasedFirstPersonUpdates = !config.disableIncreasedFirstPersonUpdates;
                    config.delayedSaveToDefaultFolder();
                }
                if (ImGui.checkbox("Disable third-person cancel", config.disableThirdPersonCancel)) {
                    config.disableThirdPersonCancel = !config.disableThirdPersonCancel;
                    config.delayedSaveToDefaultFolder();
                }
                ImGui.setNextItemWidth(ReplayUI.scaleUi(200));
                ImGui.sliderInt("Dummy Render Frames", config.exportRenderDummyFrames, 0, 100);
                ImGuiHelper.tooltip("This will make the exporter render extra dummy frames before saving a frame.\nThis will DRASTICALLY increase the time it takes to export, but may be necessary when using shaders that rely on temporal accumulation or mods which lack support for FREX Flawless Frames");
            }

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
