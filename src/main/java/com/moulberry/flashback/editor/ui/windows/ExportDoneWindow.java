package com.moulberry.flashback.editor.ui.windows;

import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.exporting.ExportSettings;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImGuiViewport;
import imgui.moulberry90.ImVec2;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiHoveredFlags;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.type.ImBoolean;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.*;

public class ExportDoneWindow {

    public static final class FinishedExportEntry {
        private final ExportSettings settings;
        private final Path outputLocation;
        private final boolean errorMovingToOutput;
        private final @Nullable NativeImage thumbnail;
        private final double duration;
        private final long fileSize;
        private final boolean outputIsFolder;
        private DynamicTexture uploaded = null;

        public FinishedExportEntry(ExportSettings settings, Path outputLocation, boolean errorMovingToOutput, @Nullable NativeImage thumbnail, double duration, long fileSize) {
            this.settings = settings;
            this.outputLocation = outputLocation;
            this.errorMovingToOutput = errorMovingToOutput;
            this.thumbnail = thumbnail;
            this.duration = duration;
            this.fileSize = fileSize;
            this.outputIsFolder = Files.isDirectory(settings.output());
        }

        public int getThumbnailTextureId() {
            Objects.requireNonNull(this.thumbnail);

            if (this.uploaded == null) {
                this.uploaded = new DynamicTexture(this.thumbnail);;
            }

            return this.uploaded.getId();
        }
    }

    private static final List<FinishedExportEntry> entries = new ArrayList<>();

    public static void addFinishedExportEntry(FinishedExportEntry entry) {
        entries.add(entry);
    }

    public static boolean isDone() {
        return !entries.isEmpty();
    }

    public static void render() {
        if (!entries.isEmpty()) {
            renderFinishedExports();
        }
    }

    public static void renderFinishedExports() {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing, 0.5f, 0.5f);

        ImBoolean open = new ImBoolean(true);

        ImGui.setNextWindowSizeConstraints(250, 50, 5000, 500);
        int flags = ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoSavedSettings;

        ImGui.openPopup("###ExportDone");
        if (ImGui.beginPopupModal(I18n.get("flashback.export_done") + "###ExportDone", open, flags)) {
            ImVec2 padding = ImGui.getStyle().getFramePadding();

            int index = 0;
            for (FinishedExportEntry entry : entries) {
                ImGui.pushID(index++);

                String name = entry.settings.name();
                if (name == null) {
                    name = entry.outputLocation.getFileName().toString();
                }
                ImGuiHelper.separatorWithText(name);

                if (entry.errorMovingToOutput) {
                    ImGui.textWrapped(I18n.get("flashback.error_moving_to_output"));
                    ImGui.separator();
                }

                final int DESIRED_W = 240;
                final int DESIRED_H = 135;

                ImGui.beginGroup();

                NativeImage thumbnail = entry.thumbnail;
                if (thumbnail != null) {
                    int id = entry.getThumbnailTextureId();

                    int originalW = thumbnail.getWidth();
                    int originalH = thumbnail.getHeight();

                    int width;
                    int height;

                    if (originalW*DESIRED_H > DESIRED_W*originalH) { // equivalent to originalW/originalH > DESIRED_W/DESIRED_H
                        width = DESIRED_W;
                        height = DESIRED_W*originalH/originalW;
                    } else {
                        width = DESIRED_H*originalW/originalH;
                        height = DESIRED_H;
                    }

                    if (ImGui.imageButton("ExportThumbnail", id, new ImVec2(width, height))) {
                        Util.getPlatform().openPath(entry.outputLocation);
                    }
                } else if (ImGui.button(I18n.get("flashback.export_done.missing_thumbnail"), DESIRED_W+padding.x*2, DESIRED_H+padding.y*2)) {
                    Util.getPlatform().openPath(entry.outputLocation);
                }

                ImGui.sameLine();

                ImGui.beginGroup();

                if (entry.fileSize > 0) {
                    String bytesString;
                    if (entry.fileSize < 1000) {
                        bytesString = I18n.get("flashback.bytes", NumberFormat.getInstance().format(entry.fileSize));
                    } else if (entry.fileSize < 1000*1000) {
                        bytesString = I18n.get("flashback.kilobytes", NumberFormat.getInstance().format(entry.fileSize/1000));
                    } else {
                        bytesString = I18n.get("flashback.megabytes", NumberFormat.getInstance().format(entry.fileSize/1000/1000));
                    }

                    ImGui.text(I18n.get("flashback.export_done.size", bytesString));
                }

                String duration = Utils.timeInSecondsToString((int) entry.duration);
                ImGui.text(I18n.get("flashback.export_done.duration", duration));

                if (entry.outputIsFolder) {
                    if (ImGui.button(I18n.get("flashback.open_folder"))) {
                        Util.getPlatform().openPath(entry.outputLocation);
                    }
                } else {
                    if (ImGui.button(I18n.get("flashback.open_file"))) {
                        Util.getPlatform().openPath(entry.outputLocation);
                    }
                    if (ImGui.button(I18n.get("flashback.open_folder"))) {
                        Util.getPlatform().openPath(entry.outputLocation.getParent());
                    }
                }

                ImGui.endGroup();

                ImGui.endGroup();

                ImGui.popID();
            }

            ImGui.endPopup();
        }

        if (!open.get()) {
            entries.clear();
        }
    }

    private static void clear() {
        for (FinishedExportEntry entry : entries) {
            if (entry.thumbnail != null) entry.thumbnail.close();
            if (entry.uploaded != null) entry.uploaded.close();
        }
        entries.clear();
    }

}
