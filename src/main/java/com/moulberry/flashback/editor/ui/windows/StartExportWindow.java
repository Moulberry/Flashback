package com.moulberry.flashback.editor.ui.windows;

import com.mojang.blaze3d.platform.Window;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.AudioCodec;
import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.compat.IrisApiWrapper;
import com.moulberry.flashback.exporting.ExportJobQueue;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.playback.ReplayServer;
import imgui.ImGui;
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImFloat;
import imgui.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class StartExportWindow {

    private static boolean open = false;
    private static boolean close = false;

    private static final int[] lastFramebufferSize = new int[]{0, 0};

    private static final int[] resolution = new int[]{1920, 1080};
    private static final int[] startEndTick = new int[]{0, 100};
    private static final ImFloat framerate = new ImFloat(60);
    private static boolean resetRng = false;

    private static VideoContainer container = VideoContainer.MP4;
    private static VideoCodec videoCodec = VideoCodec.H264;
    private static int[] selectedVideoEncoder = new int[]{0};
    private static boolean useMaximumBitrate = false;
    private static final ImString bitrate = ImGuiHelper.createResizableImString("20m");

    private static boolean recordAudio = false;
    private static AudioCodec audioCodec = AudioCodec.AAC;

    private static Path defaultExportPath = null;
    private static final ImString jobName = ImGuiHelper.createResizableImString("");

    static {
        bitrate.inputData.allowedChars = "0123456789kmb";
    }

    public static void render() {
        if (open) {
            ImGui.openPopup("###StartExport");
            Window window = Minecraft.getInstance().getWindow();

            // Only update the resolution if the framebuffer size changes
            // This way, when users input custom resolutions it persists between opens
            if (window.framebufferWidth != lastFramebufferSize[0] || window.framebufferHeight != lastFramebufferSize[1]) {
                resolution[0] = window.framebufferWidth;
                resolution[1] = window.framebufferHeight;
                lastFramebufferSize[0] = window.framebufferWidth;
                lastFramebufferSize[1] = window.framebufferHeight;
            }

            open = false;
        }

        if (ImGuiHelper.beginPopupModalCloseable("Export to video###StartExport", ImGuiWindowFlags.AlwaysAutoResize)) {
            if (close) {
                close = false;
                ImGui.closeCurrentPopup();
                ImGuiHelper.endPopupModalCloseable();
                return;
            }

            ImGuiHelper.separatorWithText("Capture Options");

            ImGui.inputInt2("Resolution", resolution);
            if (resolution[0] < 16) resolution[0] = 16;
            if (resolution[1] < 16) resolution[1] = 16;
            if (resolution[0] % 2 != 0) resolution[0] += 1;
            if (resolution[1] % 2 != 0) resolution[1] += 1;

            if (ImGui.inputInt2("Start/end tick", startEndTick)) {
                EditorState editorState = EditorStateManager.getCurrent();
                ReplayServer replayServer = Flashback.getReplayServer();
                if (editorState != null && replayServer != null) {
                    editorState.setExportTicks(startEndTick[0], startEndTick[1], replayServer.getTotalReplayTicks());
                }
            }
            ImGui.inputFloat("Framerate", framerate);

            if (ImGui.checkbox("Reset RNG", resetRng)) {
                resetRng = !resetRng;
            }
            ImGuiHelper.tooltip("Attempts to remove randomness from the replay in order to produce more consistent outputs when recording the same scene multiple times");

            ImGuiHelper.separatorWithText("Video Options");

            VideoContainer newContainer = ImGuiHelper.enumCombo("Container", container);
            if (newContainer != container) {
                container = newContainer;

                boolean supported = false;
                for (VideoCodec supportedCodec : container.getSupportedVideoCodecs()) {
                    if (videoCodec == supportedCodec) {
                        supported = true;
                        break;
                    }
                }

                if (!supported) {
                    videoCodec = container.getSupportedVideoCodecs()[0];
                }
            }

            VideoCodec newCodec = ImGuiHelper.enumCombo("Codec", videoCodec, container.getSupportedVideoCodecs());
            if (newCodec != videoCodec) {
                videoCodec = newCodec;
                selectedVideoEncoder[0] = 0;
            }

            String[] encoders = videoCodec.getEncoders();
            if (encoders.length > 1) {
                ImGuiHelper.combo("Encoder", selectedVideoEncoder, encoders);
            }

            if (ImGui.checkbox("Use Maximum Bitrate", useMaximumBitrate)) {
                useMaximumBitrate = !useMaximumBitrate;
            }
            if (!useMaximumBitrate) {
                ImGui.inputText("Bitrate", bitrate);
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    int numBitrate = stringToBitrate(ImGuiHelper.getString(bitrate));
                    bitrate.set(bitrateToString(numBitrate));
                }
            }

            AudioCodec[] supportedAudioCodecs = container.getSupportedAudioCodecs();
            if (supportedAudioCodecs.length > 0) {
                ImGuiHelper.separatorWithText("Audio Options");

                if (ImGui.checkbox("Record Audio", recordAudio)) {
                    recordAudio = !recordAudio;
                }

                if (recordAudio) {
                    AudioCodec newAudioCodec = ImGuiHelper.enumCombo("Audio Codec", audioCodec, supportedAudioCodecs);
                    if (newAudioCodec != audioCodec) {
                        audioCodec = newAudioCodec;
                    }
                }

                EditorState editorState = EditorStateManager.getCurrent();
                if (editorState != null && editorState.audioSourceEntity != null) {
                    ImGui.text("Audio Source: \nEntity(" + editorState.audioSourceEntity + ")");
                } else {
                    ImGui.text("Audio Source: Camera");
                }
            } else {
                recordAudio = false;
            }

            ImGui.dummy(0, 10);

            float buttonSize = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2f;
            if (ImGui.button("Start Export", buttonSize, 25)) {
                createExportSettings(null).thenAccept(settings -> {
                    if (settings != null) {
                        close = true;
                        Flashback.EXPORT_JOB = new ExportJob(settings);
                    }
                });
            }
            ImGui.sameLine();
            if (ImGui.button("Add to Queue", buttonSize, 25)) {
                jobName.set("Job #" + (ExportJobQueue.count()+1));
                ImGui.openPopup("QueuedJobName");
            }

            if (ImGui.beginPopup("QueuedJobName")) {
                ImGui.setNextItemWidth(100);
                ImGui.inputText("Job Name", jobName);

                if (ImGui.button("Queue Job")) {
                    createExportSettings(ImGuiHelper.getString(jobName)).thenAccept(settings -> {
                        if (settings != null) {
                            close = true;
                            ExportJobQueue.queuedJobs.add(settings);
                        }
                    });
                }
                ImGui.sameLine();
                if (ImGui.button("Back")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }

            ImGuiHelper.endPopupModalCloseable();
        }

        close = false;
    }

    private static CompletableFuture<ExportSettings> createExportSettings(@Nullable String name) {
        int numBitrate;
        if (useMaximumBitrate) {
            numBitrate = 0;
        } else {
            numBitrate = stringToBitrate(ImGuiHelper.getString(bitrate));
        }

        String encoder = videoCodec.getEncoders()[selectedVideoEncoder[0]];

        if (defaultExportPath == null || !Files.exists(defaultExportPath)) {
            defaultExportPath = FabricLoader.getInstance().getGameDir();
        }

        String defaultName = null;
        if (name != null) {
            try {
                defaultName = FileUtil.findAvailableName(defaultExportPath, name, "." + container.extension());
            } catch (Exception ignored) {}
        }
        if (defaultName == null) {
            try {
                defaultName = FileUtil.findAvailableName(defaultExportPath, "output", "." + container.extension());
            } catch (Exception ignored) {}
        }
        if (defaultName == null) {
            defaultName = "output." + container.extension();
        }

        String defaultExportPathString = defaultExportPath.toString();
        return AsyncFileDialogs.saveFileDialog(defaultExportPathString, defaultName,
            container.extension(), container.extension()).thenApply(pathStr -> {
            if (pathStr != null) {
                int start = Math.max(0, startEndTick[0]);
                int end = Math.max(start, startEndTick[1]);

                ReplayServer replayServer = Flashback.getReplayServer();
                if (replayServer != null) {
                    int totalTicks = replayServer.getTotalReplayTicks();
                    start = Math.min(start, totalTicks);
                    end = Math.min(end, totalTicks);
                }

                EditorState editorState = EditorStateManager.getCurrent();
                if (editorState == null) {
                    return null;
                }

                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null) {
                    return null;
                }

                Path path = Path.of(pathStr);
                defaultExportPath = path.getParent();
                return new ExportSettings(name, editorState.copy(),
                    player.position(), player.getYRot(), player.getXRot(),
                    resolution[0], resolution[1], start, end,
                    Math.max(1, framerate.get()), resetRng, container, videoCodec, encoder, numBitrate, recordAudio, path);
            }

            return null;
        });
    }

    private static int stringToBitrate(String string) {
        int number = 0;
        int modifier = 1;
        int total = 0;

        for (char c : string.toCharArray()) {
            if (c >= '0' && c <= '9') {
                if (modifier > 1) {
                    total += number * modifier;
                    number = 0;
                    modifier = 1;
                }

                number *= 10;
                number += c - '0';
            } else if (c == 'k') {
                modifier *= 1000;
            } else if (c == 'm') {
                modifier *= 1000000;
            } else if (c == 'b') {
                modifier *= 1000000000;
            }
        }

        total += number * modifier;

        return total;
    }

    private static String bitrateToString(int bitrate) {
        if (bitrate >= 1000000000 && bitrate == (bitrate/1000000000)*1000000000) {
            return (bitrate/1000000000) + "b";
        } else if (bitrate >= 1000000 && bitrate == (bitrate/1000000)*1000000) {
            return (bitrate/1000000) + "m";
        } else if (bitrate >= 1000 && bitrate == (bitrate/1000)*1000) {
            return (bitrate/1000) + "k";
        } else {
            return String.valueOf(bitrate);
        }
    }

    public static void open() {
        open = true;

        EditorState editorState = EditorStateManager.getCurrent();

        if (editorState != null && editorState.exportStartTicks >= 0) {
            startEndTick[0] = editorState.exportStartTicks;
        } else {
            startEndTick[0] = 0;
        }
        if (editorState != null && editorState.exportEndTicks >= 0) {
            startEndTick[1] = editorState.exportEndTicks;
        } else {
            ReplayServer replayServer = Flashback.getReplayServer();
            if (replayServer == null) {
                startEndTick[1] = startEndTick[0] + 100;
            } else {
                startEndTick[1] = replayServer.getTotalReplayTicks();
            }
        }
    }

}
