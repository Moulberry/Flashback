package com.moulberry.flashback.editor.ui.windows;

import com.mojang.blaze3d.platform.Window;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.combo_options.AspectRatio;
import com.moulberry.flashback.combo_options.AudioCodec;
import com.moulberry.flashback.combo_options.Sizing;
import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.exporting.ExportJobQueue;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.playback.ReplayServer;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class StartExportWindow {

    private static boolean open = false;
    private static boolean close = false;

    private static final int[] lastFramebufferSize = new int[]{0, 0};
    private static AspectRatio lastCustomAspectRatio = null;

    private static final int[] resolution = new int[]{1920, 1080};
    private static final int[] startEndTick = new int[]{0, 100};
    private static final float[] framerate = new float[]{60};
    private static boolean resetRng = false;
    public static boolean ssaa = false;
    public static boolean noGui = false;

    private static VideoContainer[] supportedContainers = null;
    private static VideoContainer[] supportedContainersWithTransparency = null;

    private static VideoContainer container = null;
    private static VideoCodec videoCodec = null;
    private static int[] selectedVideoEncoder = new int[]{0};
    private static boolean useMaximumBitrate = false;
    private static final ImString bitrate = ImGuiHelper.createResizableImString("20m");

    private static boolean recordAudio = false;
    public static boolean transparentBackground = false;
    private static AudioCodec audioCodec = AudioCodec.AAC;
    private static boolean stereoAudio = false;

    public static Path defaultExportPath = null;
    private static final ImString jobName = ImGuiHelper.createResizableImString("");

    private static String installedIncompatibleModsString = null;
    private static final List<String> potentialIncompatibleMods = List.of(
        "g4mespeed", // causes rendering issues due to overriding partial tick time
        "feather" // causes miscellaneous crashes and issues that are impossible to debug
    );

    static {
        bitrate.inputData.allowedChars = "0123456789kmb";
    }

    public static void render() {
        EditorState editorState = EditorStateManager.getCurrent();

        if (open) {
            installedIncompatibleModsString = null;
            for (String potentialIncompatibleMod : potentialIncompatibleMods) {
                if (FabricLoader.getInstance().isModLoaded(potentialIncompatibleMod)) {
                    if (installedIncompatibleModsString == null) {
                        installedIncompatibleModsString = potentialIncompatibleMod;
                    } else {
                        installedIncompatibleModsString += ", " + potentialIncompatibleMod;
                    }
                }
            }

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

            if (editorState != null && editorState.replayVisuals.sizing == Sizing.CHANGE_ASPECT_RATIO) {
                AspectRatio aspectRatio = editorState.replayVisuals.changeAspectRatio;
                if (aspectRatio != null && aspectRatio != lastCustomAspectRatio) {
                    switch (aspectRatio) {
                        case ASPECT_16_9 -> {
                            resolution[0] = 1920;
                            resolution[1] = 1080;
                        }
                        case ASPECT_9_16 -> {
                            resolution[0] = 1080;
                            resolution[1] = 1920;
                        }
                        case ASPECT_240_1 -> {
                            resolution[0] = 1920;
                            resolution[1] = 800;
                        }
                        case ASPECT_1_1 -> {
                            resolution[0] = 1920;
                            resolution[1] = 1920;
                        }
                        case ASPECT_4_3 -> {
                            resolution[0] = 1600;
                            resolution[1] = 1200;
                        }
                        case ASPECT_3_2 -> {
                            resolution[0] = 2160;
                            resolution[1] = 1440;
                        }
                    }
                }
                lastCustomAspectRatio = aspectRatio;
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

            ImGuiHelper.inputInt("Resolution", resolution);

            if (resolution[0] < 16) resolution[0] = 16;
            if (resolution[1] < 16) resolution[1] = 16;
            if (resolution[0] % 2 != 0) resolution[0] += 1;
            if (resolution[1] % 2 != 0) resolution[1] += 1;

            if (ImGuiHelper.inputInt("Start/end tick", startEndTick)) {
                ReplayServer replayServer = Flashback.getReplayServer();
                if (editorState != null && replayServer != null) {
                    editorState.currentScene().setExportTicks(startEndTick[0], startEndTick[1], replayServer.getTotalReplayTicks());
                    editorState.markDirty();
                }
            }
            ImGuiHelper.inputFloat("Framerate", framerate);

            if (ImGui.checkbox("Reset RNG", resetRng)) {
                resetRng = !resetRng;
            }
            ImGuiHelper.tooltip("Attempts to remove randomness from the replay in order to produce more consistent outputs when recording the same scene multiple times");

            ImGui.sameLine();

            if (ImGui.checkbox("SSAA", ssaa)) {
                ssaa = !ssaa;
            }
            ImGuiHelper.tooltip("Supersampling Anti-Aliasing: Remove jagged edges by rendering the game at double resolution and downscaling");

            ImGui.sameLine();

            if (ImGui.checkbox("No GUI", noGui)) {
                noGui = !noGui;
            }
            ImGuiHelper.tooltip("Removes all UI from the screen, rendering only the world");

            ImGuiHelper.separatorWithText("Video Options");

            renderVideoOptions(editorState);

            AudioCodec[] supportedAudioCodecs = container.getSupportedAudioCodecs();
            if (supportedAudioCodecs.length > 0) {
                ImGuiHelper.separatorWithText("Audio Options");

                if (ImGui.checkbox("Record Audio", recordAudio)) {
                    recordAudio = !recordAudio;
                }

                if (recordAudio) {
                    if (ImGui.checkbox("Stereo (2 channel)", stereoAudio)) {
                        stereoAudio = !stereoAudio;
                    }

                    AudioCodec newAudioCodec = ImGuiHelper.enumCombo("Audio Codec", audioCodec, supportedAudioCodecs);
                    if (newAudioCodec != audioCodec) {
                        audioCodec = newAudioCodec;
                    }

                    if (editorState != null && editorState.audioSourceEntity != null) {
                        ImGui.text("Audio Source: \nEntity(" + editorState.audioSourceEntity + ")");
                    } else {
                        ImGui.text("Audio Source: Camera");
                    }
                }
            } else {
                recordAudio = false;
            }

            if (installedIncompatibleModsString != null) {
                ImGuiHelper.separatorWithText("Incompatible Mods");
                ImGui.textWrapped("You have some mods installed which are known to cause crashes/rendering issues.\n" +
                    "If you encounter problems exporting, please try removing the following mods:");
                ImGui.pushTextWrapPos();
                ImGui.textColored(0xFF0000FF, installedIncompatibleModsString);
                ImGui.popTextWrapPos();
            }

            ImGui.dummy(0, 10);

            float buttonSize = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2f;
            if (ImGui.button("Start Export", buttonSize, 25)) {
                createExportSettings(null).thenAccept(settings -> {
                    if (settings != null) {
                        close = true;
                        Utils.exportSequenceCount += 1;
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
                            Utils.exportSequenceCount += 1;
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

    private static void renderVideoOptions(EditorState editorState) {
        if (editorState != null && !editorState.replayVisuals.renderSky) {
            if (ImGui.checkbox("Transparent Sky", transparentBackground)) {
                transparentBackground = !transparentBackground;
            }
        } else {
            transparentBackground = false;
        }

        VideoContainer[] containers;
        if (transparentBackground) {
            if (supportedContainersWithTransparency == null) {
                supportedContainersWithTransparency = VideoContainer.findSupportedContainers(true);
            }
            containers = supportedContainersWithTransparency;
        } else {
            if (supportedContainers == null) {
                supportedContainers = VideoContainer.findSupportedContainers(false);
            }
            containers = supportedContainers;
        }

        if (containers.length == 0) {
            ImGui.text("No supported containers found");
            return;
        }

        if (container == null || !Arrays.asList(containers).contains(container)) {
            container = containers[0];
        }

        container = ImGuiHelper.enumCombo("Container", container, containers);

        if (container == VideoContainer.PNG_SEQUENCE) {
            return;
        }

        VideoCodec[] codecs = container.getSupportedVideoCodecs(transparentBackground);

        if (codecs.length == 0) {
            ImGui.text("No supported codecs found");
            return;
        }

        if (videoCodec == null || !Arrays.asList(codecs).contains(videoCodec)) {
            videoCodec = codecs[0];
        }

        if (codecs.length > 1) {
            VideoCodec newCodec = ImGuiHelper.enumCombo("Codec", videoCodec, codecs);
            if (newCodec != videoCodec) {
                videoCodec = newCodec;
                selectedVideoEncoder[0] = 0;
            }
        }

        String[] encoders = videoCodec.getEncoders();
        if (encoders.length > 1) {
            ImGuiHelper.combo("Encoder", selectedVideoEncoder, encoders);
        }

        if (videoCodec != VideoCodec.GIF) {
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
        } else {
            ImGui.pushTextWrapPos();
            ImGui.textColored(0xFFFFFFFF, "Warning: GIF output can be extremely large. Please ensure you know the limitations of the GIF format before exporting. You might be better off using WebP which is a similar but better format");
            ImGui.popTextWrapPos();
        }
    }

    private static CompletableFuture<ExportSettings> createExportSettings(@Nullable String name) {
        int numBitrate;
        if (useMaximumBitrate) {
            numBitrate = 0;
        } else {
            numBitrate = stringToBitrate(ImGuiHelper.getString(bitrate));
        }

        String defaultName = getDefaultFilename(name, container.extension());

        Function<String, ExportSettings> callback = pathStr -> {
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

                boolean transparent = transparentBackground && !editorState.replayVisuals.renderSky;
                String encoder = videoCodec.getEncoders()[selectedVideoEncoder[0]];

                VideoCodec useVideoCodec = videoCodec;
                AudioCodec useAudioCodec = audioCodec;
                boolean shouldRecordAudio = recordAudio;

                if (container == VideoContainer.PNG_SEQUENCE) {
                    useVideoCodec = null;
                    encoder = null;
                    shouldRecordAudio = false;
                }

                if (!shouldRecordAudio) {
                    useAudioCodec = null;
                }

                Path path = Path.of(pathStr);
                defaultExportPath = path.getParent();
                return new ExportSettings(name, editorState.copy(),
                    player.position(), player.getYRot(), player.getXRot(),
                    resolution[0], resolution[1], start, end,
                    Math.max(1, framerate[0]), resetRng, container, useVideoCodec, encoder, numBitrate, transparent, ssaa, noGui,
                    shouldRecordAudio, stereoAudio, useAudioCodec,
                    path);
            }

            return null;
        };

        String defaultExportPathString = defaultExportPath.toString();
        if (container == VideoContainer.PNG_SEQUENCE) {
            return AsyncFileDialogs.openFolderDialog(defaultExportPathString).thenApply(callback);
        } else {
            return AsyncFileDialogs.saveFileDialog(defaultExportPathString, defaultName,
                container.extension(), container.extension()).thenApply(callback);
        }

    }

    public static @NotNull String getDefaultFilename(@Nullable String name, String extension) {
        if (defaultExportPath == null || !Files.exists(defaultExportPath)) {
            defaultExportPath = FabricLoader.getInstance().getGameDir();
        }

        String defaultName = null;
        if (name != null) {
            try {
                defaultName = FileUtil.findAvailableName(defaultExportPath, name, "." + extension);
            } catch (Exception ignored) {}
        }
        if (defaultName == null) {
            String desiredName = Utils.resolveFilenameTemplate(Flashback.getConfig().defaultExportFilename);
            try {
                defaultName = FileUtil.findAvailableName(defaultExportPath, desiredName, "." + extension);
            } catch (Exception ignored) {}
        }
        if (defaultName == null) {
            String desiredName = Utils.resolveFilenameTemplate(Flashback.getConfig().defaultExportFilename);
            defaultName = desiredName + "." + extension;
        }
        return defaultName;
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
        EditorScene editorScene = editorState == null ? null : editorState.currentScene();

        if (editorScene != null && editorScene.exportStartTicks >= 0) {
            startEndTick[0] = editorScene.exportStartTicks;
        } else {
            startEndTick[0] = 0;
        }
        if (editorScene != null && editorScene.exportEndTicks >= 0) {
            startEndTick[1] = editorScene.exportEndTicks;
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
