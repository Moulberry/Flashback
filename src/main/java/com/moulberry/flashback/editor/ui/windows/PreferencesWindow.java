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
import net.minecraft.client.resources.language.I18n;

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
        String title = I18n.get("flashback.preferences");
        if (ImGuiHelper.beginPopupModalCloseable(title + "###Preferences", ImGuiWindowFlags.NoResize)) {
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
            if (ImGui.inputText(I18n.get("flashback.export_filename"), imString)) {
                config.defaultExportFilename = ImGuiHelper.getString(imString);
                config.delayedSaveToDefaultFolder();
            }
            ImGuiHelper.tooltip(I18n.get("flashback.export_filename_tooltip"));

            // Keyframes
            ImGuiHelper.separatorWithText(I18n.get("flashback.keyframes"));

            ImGui.setNextItemWidth(ReplayUI.scaleUi(200));
            config.defaultInterpolationType = ImGuiHelper.enumCombo(I18n.get("flashback.default_interpolation"), config.defaultInterpolationType);

            if (ImGui.checkbox(I18n.get("flashback.use_realtime_interpolation"), config.useRealtimeInterpolation)) {
                config.useRealtimeInterpolation = !config.useRealtimeInterpolation;
            }

            if (ImGui.collapsingHeader(I18n.get("flashback.advanced"))) {
                ImGui.textWrapped(I18n.get("flashback.advanced_description"));
                if (ImGui.checkbox(I18n.get("flashback.disable_first_person_updates"), config.disableIncreasedFirstPersonUpdates)) {
                    config.disableIncreasedFirstPersonUpdates = !config.disableIncreasedFirstPersonUpdates;
                    config.delayedSaveToDefaultFolder();
                }
                if (ImGui.checkbox(I18n.get("flashback.disable_third_person_cancel"), config.disableThirdPersonCancel)) {
                    config.disableThirdPersonCancel = !config.disableThirdPersonCancel;
                    config.delayedSaveToDefaultFolder();
                }
                ImGui.setNextItemWidth(ReplayUI.scaleUi(200));
                ImGui.sliderInt(I18n.get("flashback.dummy_render_frames"), config.exportRenderDummyFrames, 0, 100);
                ImGuiHelper.tooltip(I18n.get("flashback.dummy_render_frames_description"));
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
