package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;

import java.nio.file.Path;

public class ExportScreenshotWindow {

    private static boolean open = false;
    private static boolean close = false;

    public static void render() {
        if (open) {
            open = false;
            ImGui.openPopup("###ExportScreenshot");
        }

        String title = I18n.get("flashback.export_screenshot");
        if (ImGuiHelper.beginPopupModalCloseable(title + "###ExportScreenshot", ImGuiWindowFlags.AlwaysAutoResize)) {
            if (close) {
                close = false;
                ImGui.closeCurrentPopup();
                ImGuiHelper.endPopupModalCloseable();
                return;
            }

            FlashbackConfigV1 config = Flashback.getConfig();
            config.forceDefaultExportSettings.apply(config.internalExport);

            if (config.internalExport.resolution == null || config.internalExport.resolution.length != 2) {
                config.internalExport.resolution = new int[]{1920, 1080};
            }

            ImGuiHelper.inputInt(I18n.get("flashback.resolution"), config.internalExport.resolution);
            config.internalExport.resolution[0] = Math.max(1, config.internalExport.resolution[0]);
            config.internalExport.resolution[1] = Math.max(1, config.internalExport.resolution[1]);

            EditorState editorState = EditorStateManager.getCurrent();

            if (ImGui.checkbox(I18n.get("flashback.ssaa"), config.internalExport.ssaa)) {
                config.internalExport.ssaa = !config.internalExport.ssaa;
            }
            ImGuiHelper.tooltip(I18n.get("flashback.ssaa_tooltip"));

            ImGui.sameLine();

            if (ImGui.checkbox(I18n.get("flashback.no_gui"), config.internalExport.noGui)) {
                config.internalExport.noGui = !config.internalExport.noGui;
            }
            ImGuiHelper.tooltip(I18n.get("flashback.no_gui_tooltip"));

            if (editorState != null && !editorState.replayVisuals.renderSky) {
                if (ImGui.checkbox(I18n.get("flashback.transparent_sky"), config.internalExport.transparentBackground)) {
                    config.internalExport.transparentBackground = !config.internalExport.transparentBackground;
                }
            }

            if (editorState != null && ImGui.button(I18n.get("flashback.take_screenshot"))) {
                String defaultName = StartExportWindow.getDefaultFilename(null, "png", config);
                String defaultExportPathString = config.internalExport.defaultExportPath;

                AsyncFileDialogs.saveFileDialog(defaultExportPathString, defaultName, "PNG", "png").thenAccept(pathStr -> {
                    if (pathStr != null) {
                        Path path = Path.of(pathStr);
                        config.internalExport.defaultExportPath = path.getParent().toString();

                        LocalPlayer player = Minecraft.getInstance().player;
                        int tick = Flashback.getReplayServer().getReplayTick();

                        boolean transparent = config.internalExport.transparentBackground && !editorState.replayVisuals.renderSky;
                        boolean ssaa = config.internalExport.ssaa;
                        boolean noGui = config.internalExport.noGui;

                        EditorState copiedEditorState = editorState.copyWithoutKeyframes();

                        ExportSettings settings = new ExportSettings(null, copiedEditorState,
                            player.position(), player.getYRot(), player.getXRot(),
                            config.internalExport.resolution[0], config.internalExport.resolution[1], tick, tick,
                            1, false, VideoContainer.PNG_SEQUENCE, null, null, 0, transparent, ssaa, noGui,
                            false, false, null,
                            path, null);

                        close = true;
                        Utils.exportSequenceCount += 1;
                        Flashback.EXPORT_JOB = new ExportJob(settings);

                        config.delayedSaveToDefaultFolder();
                    }
                });
            }

            ImGuiHelper.endPopupModalCloseable();
        }

        close = false;
    }

    public static void open() {
        open = true;
    }

}
