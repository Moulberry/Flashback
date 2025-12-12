package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImVec2;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.resources.language.I18n;

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

            FlashbackConfigV1 config = Flashback.getConfig();

            // Exporting
            ImGuiHelper.separatorWithText("Exporting");

            ImString imString = ImGuiHelper.createResizableImString(config.exporting.defaultExportFilename);
            ImGui.setNextItemWidth(ReplayUI.scaleUi(200));
            if (ImGui.inputText(I18n.get("flashback.export_filename"), imString)) {
                config.exporting.defaultExportFilename = ImGuiHelper.getString(imString);
                config.delayedSaveToDefaultFolder();
            }
            ImGuiHelper.tooltip(I18n.get("flashback.export_filename_tooltip"));

            // Keyframes
            ImGuiHelper.separatorWithText(I18n.get("flashback.keyframes"));

            ImGui.setNextItemWidth(ReplayUI.scaleUi(200));
            config.keyframes.defaultInterpolationType = ImGuiHelper.enumCombo(I18n.get("flashback.default_interpolation"), config.keyframes.defaultInterpolationType);
            ImGuiHelper.tooltip(I18n.get("flashback.default_interpolation_description"));

            if (ImGui.checkbox(I18n.get("flashback.use_realtime_interpolation"), config.keyframes.useRealtimeInterpolation)) {
                config.keyframes.useRealtimeInterpolation = !config.keyframes.useRealtimeInterpolation;
            }
            ImGuiHelper.tooltip(I18n.get("flashback.use_realtime_interpolation_description"));

            if (ImGui.collapsingHeader(I18n.get("flashback.advanced"))) {
                ImGui.textWrapped(I18n.get("flashback.advanced_description"));

                if (ImGui.checkbox(I18n.get("flashback.disable_first_person_updates"), config.advanced.disableIncreasedFirstPersonUpdates)) {
                    config.advanced.disableIncreasedFirstPersonUpdates = !config.advanced.disableIncreasedFirstPersonUpdates;
                    config.delayedSaveToDefaultFolder();
                }

                if (ImGui.checkbox(I18n.get("flashback.disable_third_person_cancel"), config.advanced.disableThirdPersonCancel)) {
                    config.advanced.disableThirdPersonCancel = !config.advanced.disableThirdPersonCancel;
                    config.delayedSaveToDefaultFolder();
                }

                ImGui.setNextItemWidth(ReplayUI.scaleUi(200));
                int[] value = new int[]{config.exporting.exportRenderDummyFrames};
                if (ImGui.sliderInt(I18n.get("flashback.dummy_render_frames"), value, 0, 100)) {
                    config.exporting.exportRenderDummyFrames = value[0];
                    config.delayedSaveToDefaultFolder();
                }
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
