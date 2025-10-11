package com.moulberry.flashback.editor.ui.windows;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.MedalTvUploading;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.exporting.ExportSettings;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.*;

public class ExportDoneWindow {

    public static final class FinishedExportEntry {
        private final ExportSettings settings;
        private final @Nullable NativeImage thumbnail;
        private final double duration;
        private final long fileSize;
        private final boolean outputIsFolder;
        private DynamicTexture uploaded = null;
        private MedalTvUploading.UploadStatus completedUpload = null;

        private boolean checkedCanUpload = false;
        private @Nullable MedalTvUploading.CannotUploadReason cannotUploadReason = null;

        public FinishedExportEntry(ExportSettings settings, @Nullable NativeImage thumbnail, double duration, long fileSize) {
            this.settings = settings;
            this.thumbnail = thumbnail;
            this.duration = duration;
            this.fileSize = fileSize;
            this.outputIsFolder = Files.isDirectory(settings.output());
        }

        public int getThumbnailTextureId() {
            Objects.requireNonNull(this.thumbnail);

            if (this.uploaded == null) {
                this.uploaded = new DynamicTexture(() -> "flashback export thumbnail", this.thumbnail);;
            }

            return ((GlTexture)this.uploaded.getTexture()).glId();
        }
    }

    private static final List<FinishedExportEntry> entries = new ArrayList<>();
    private static @Nullable MedalTvUploading.UploadStatus inProgressUpload = null;

    public static void addFinishedExportEntry(FinishedExportEntry entry) {
        entries.add(entry);
    }

    public static boolean isDone() {
        return !entries.isEmpty();
    }

    public static void render() {
        if (!entries.isEmpty()) {
            renderFinishedExports();
        } else {
            renderInProgressUpload();
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
                Path output = entry.settings.output();

                String name = entry.settings.name();
                if (name == null) {
                    name = output.getFileName().toString();
                }
                ImGuiHelper.separatorWithText(name);

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

                    if (ImGui.imageButton(id, width, height)) {
                        Util.getPlatform().openPath(output);
                    }
                } else if (ImGui.button(I18n.get("flashback.export_done.missing_thumbnail"), DESIRED_W+padding.x*2, DESIRED_H+padding.y*2)) {
                    Util.getPlatform().openPath(output);
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
                        Util.getPlatform().openPath(output);
                    }
                } else {
                    if (ImGui.button(I18n.get("flashback.open_file"))) {
                        Util.getPlatform().openPath(output);
                    }
                    if (ImGui.button(I18n.get("flashback.open_folder"))) {
                        Util.getPlatform().openPath(output.getParent());
                    }
                }

                if (!entry.checkedCanUpload) {
                    entry.checkedCanUpload = true;
                    entry.cannotUploadReason = MedalTvUploading.checkCanUpload(entry.settings);
                }

                if (entry.cannotUploadReason != MedalTvUploading.CannotUploadReason.UNSUPPORTED_FORMAT) {
                    String uploadAndCopyLink = I18n.get("flashback.export_done.upload_and_copy_link");
                    if (entry.cannotUploadReason != null) {
                        ImGui.beginDisabled();
                        ImGui.button(uploadAndCopyLink);
                        ImGui.endDisabled();

                        switch (entry.cannotUploadReason) {
                            case UNSUPPORTED_FORMAT -> throw new UnsupportedOperationException();
                            case FILE_MISSING -> {
                                ImGuiHelper.tooltip("Cannot upload: file is missing", ImGuiHoveredFlags.AllowWhenDisabled);
                            }
                            case ERROR_CHECKING_FILE -> {
                                ImGuiHelper.tooltip("Cannot upload: error while checking file", ImGuiHoveredFlags.AllowWhenDisabled);
                            }
                            case OVER_2GB -> {
                                ImGuiHelper.tooltip("Cannot upload: file is over 2gb limit", ImGuiHoveredFlags.AllowWhenDisabled);
                            }
                        }
                    } else {
                        if (ImGui.button(uploadAndCopyLink)) {
                            entry.cannotUploadReason = MedalTvUploading.checkCanUpload(entry.settings);
                            if (entry.cannotUploadReason == null) {
                                if (entry.completedUpload == null || entry.completedUpload.shouldCancel) {
                                    entry.completedUpload = MedalTvUploading.upload(entry.settings);
                                }
                                inProgressUpload = entry.completedUpload;
                            }
                        }
                        ImGuiHelper.tooltip("Upload a clip to medal.tv and generate a link to view the clip");
                    }
                }

                ImGui.endGroup();

                ImGui.endGroup();

                ImGui.popID();
            }

            renderInProgressUpload();

            ImGui.endPopup();
        }

        if (!open.get() && inProgressUpload == null) {
            entries.clear();
        }
    }

    public static void renderInProgressUpload() {
        if (inProgressUpload == null) {
            return;
        }

        int flags = ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoSavedSettings;

        ImGui.openPopup("###UploadingToMedal");
        if (ImGui.beginPopupModal("Uploading clip###UploadingToMedal", flags)) {
            boolean hasError = false;

            boolean finished = inProgressUpload.finished;
            int progressPercentage = inProgressUpload.progressPercentage;

            ImVec2 cursor = ImGui.getCursorScreenPos();
            ImGui.dummy(500, 20);
            ImGui.getForegroundDrawList().addRectFilled(cursor.x, cursor.y, cursor.x+500, cursor.y+20, 0xFF000000);
            ImGui.getForegroundDrawList().addRectFilled(cursor.x, cursor.y, cursor.x+progressPercentage*5, cursor.y+20, 0xFFF4A903);

            String errorMessage = inProgressUpload.errorMessage;
            if (errorMessage != null) {
                ImGui.dummy(0, 10);
                ImGui.textColored(0xFF0000FF, errorMessage);
                hasError = true;
            }

            Throwable throwable = inProgressUpload.throwable;
            if (throwable != null) {
                ImGui.dummy(0, 10);
                ImGui.textColored(0xFF0000FF, throwable.toString());
                hasError = true;
            }

            if (!hasError && progressPercentage >= 100) {
                ImGui.dummy(0, 10);

                URI shareUrl = inProgressUpload.shareUrl;
                String shareUrlStr = shareUrl.toString();

                ImGui.text("Share Link:");
                ImGui.sameLine();
                ImGui.textColored(0xFFF4A903, shareUrlStr);
                if (ImGui.isItemClicked()) {
                    try {
                        Util.getPlatform().openUri(shareUrl);
                    } catch (Exception ignored) {}
                }
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clipboard.equals(shareUrlStr)) {
                    ImGui.button("Copied!");
                } else if (ImGui.button("Copy to Clipboard")) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(shareUrlStr);
                }
            }

            ImGui.dummy(0, 10);

            if (!hasError) {
                if (!finished) ImGui.beginDisabled();
                if (ImGui.button("Done") && finished) {
                    inProgressUpload = null;
                    ImGui.closeCurrentPopup();
                }
                if (!finished) ImGui.endDisabled();

            }

            if (hasError || (!finished && Math.abs(inProgressUpload.startTime - System.currentTimeMillis()) > 5000)) {
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    inProgressUpload.shouldCancel = true;
                    inProgressUpload = null;
                    ImGui.closeCurrentPopup();
                }
            }

            ImGui.endPopup();
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
