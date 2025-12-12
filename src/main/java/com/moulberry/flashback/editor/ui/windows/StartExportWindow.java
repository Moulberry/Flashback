package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.combo_options.AspectRatio;
import com.moulberry.flashback.combo_options.AudioCodec;
import com.moulberry.flashback.combo_options.Sizing;
import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.exporting.ExportJobQueue;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.playback.ReplayServer;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
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

    private static final int[] startEndTick = new int[]{-1, -1};

    private static VideoContainer[] supportedContainers = null;
    private static VideoContainer[] supportedContainersWithTransparency = null;

    private static final ImString bitrate = ImGuiHelper.createResizableImString("20m");
    private static final ImString jobName = ImGuiHelper.createResizableImString("");
    private static final ImString pngSequenceFormat = ImGuiHelper.createResizableImString("%04d");

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

            FlashbackConfigV1 config = Flashback.getConfig();
            config.forceDefaultExportSettings.apply(config.internalExport);

            if (config.internalExport.resolution == null || config.internalExport.resolution.length != 2) {
                config.internalExport.resolution = new int[]{1920, 1080};
            }
            if (config.internalExport.framerate == null || config.internalExport.framerate.length != 1) {
                config.internalExport.framerate = new float[]{60};
            }
            if (config.internalExport.selectedVideoEncoder == null || config.internalExport.selectedVideoEncoder.length != 1) {
                config.internalExport.selectedVideoEncoder = new int[]{0};
            }
            if (config.internalExport.audioCodec == null) {
                config.internalExport.audioCodec = AudioCodec.AAC;
            }

            ImGui.openPopup("###StartExport");

            if (editorState != null && editorState.replayVisuals.sizing == Sizing.CHANGE_ASPECT_RATIO) {
                AspectRatio aspectRatio = editorState.replayVisuals.changeAspectRatio;
                if (aspectRatio != null && aspectRatio != lastCustomAspectRatio) {
                    switch (aspectRatio) {
                        case ASPECT_16_9 -> {
                            config.internalExport.resolution[0] = 1920;
                            config.internalExport.resolution[1] = 1080;
                        }
                        case ASPECT_9_16 -> {
                            config.internalExport.resolution[0] = 1080;
                            config.internalExport.resolution[1] = 1920;
                        }
                        case ASPECT_240_1 -> {
                            config.internalExport.resolution[0] = 1920;
                            config.internalExport.resolution[1] = 800;
                        }
                        case ASPECT_1_1 -> {
                            config.internalExport.resolution[0] = 1920;
                            config.internalExport.resolution[1] = 1920;
                        }
                        case ASPECT_4_3 -> {
                            config.internalExport.resolution[0] = 1600;
                            config.internalExport.resolution[1] = 1200;
                        }
                        case ASPECT_3_2 -> {
                            config.internalExport.resolution[0] = 2160;
                            config.internalExport.resolution[1] = 1440;
                        }
                    }
                }
                lastCustomAspectRatio = aspectRatio;
            }

            open = false;
            close = false;
        }

        if (ImGuiHelper.beginPopupModalCloseable(I18n.get("flashback.export_to_video") + "###StartExport", ImGuiWindowFlags.AlwaysAutoResize)) {
            if (close) {
                close = false;
                ImGui.closeCurrentPopup();
                ImGuiHelper.endPopupModalCloseable();
                return;
            }

            FlashbackConfigV1 config = Flashback.getConfig();

            ImGuiHelper.separatorWithText(I18n.get("flashback.capture_options"));

            ImGuiHelper.inputInt(I18n.get("flashback.resolution"), config.internalExport.resolution);

            if (config.internalExport.resolution[0] < 16) config.internalExport.resolution[0] = 16;
            if (config.internalExport.resolution[1] < 16) config.internalExport.resolution[1] = 16;
            if (config.internalExport.resolution[0] % 2 != 0) config.internalExport.resolution[0] += 1;
            if (config.internalExport.resolution[1] % 2 != 0) config.internalExport.resolution[1] += 1;

            if (startEndTick[0] >= 0 && startEndTick[1] >= 0) {
                if (ImGuiHelper.inputInt(I18n.get("flashback.start_end_tick"), startEndTick)) {
                    ReplayServer replayServer = Flashback.getReplayServer();
                    if (editorState != null && replayServer != null) {
                        editorState.setExportTicks(startEndTick[0], startEndTick[1], replayServer.getTotalReplayTicks());
                    }
                }
            }
            ImGuiHelper.inputFloat(I18n.get("flashback.framerate"), config.internalExport.framerate);

            if (ImGui.checkbox(I18n.get("flashback.reset_rng"), config.internalExport.resetRng)) {
                config.internalExport.resetRng = !config.internalExport.resetRng;
            }
            ImGuiHelper.tooltip(I18n.get("flashback.reset_rng_tooltip"));

            ImGui.sameLine();

            if (ImGui.checkbox(I18n.get("flashback.ssaa"), config.internalExport.ssaa)) {
                config.internalExport.ssaa = !config.internalExport.ssaa;
            }
            ImGuiHelper.tooltip(I18n.get("flashback.ssaa_tooltip"));

            ImGui.sameLine();

            if (ImGui.checkbox(I18n.get("flashback.no_gui"), config.internalExport.noGui)) {
                config.internalExport.noGui = !config.internalExport.noGui;
            }
            ImGuiHelper.tooltip(I18n.get("flashback.no_gui_tooltip"));

            ImGuiHelper.separatorWithText(I18n.get("flashback.video_options"));

            renderVideoOptions(editorState, config);

            AudioCodec[] supportedAudioCodecs = config.internalExport.container.getSupportedAudioCodecs();
            if (supportedAudioCodecs.length > 0) {
                ImGuiHelper.separatorWithText(I18n.get("flashback.audio_options"));

                if (ImGui.checkbox(I18n.get("flashback.record_audio"), config.internalExport.recordAudio)) {
                    config.internalExport.recordAudio = !config.internalExport.recordAudio;
                }

                if (config.internalExport.recordAudio) {
                    if (ImGui.checkbox(I18n.get("flashback.stereo_audio"), config.internalExport.stereoAudio)) {
                        config.internalExport.stereoAudio = !config.internalExport.stereoAudio;
                    }

                    AudioCodec newAudioCodec = ImGuiHelper.enumCombo(I18n.get("flashback.audio_codec"), config.internalExport.audioCodec, supportedAudioCodecs);
                    if (newAudioCodec != config.internalExport.audioCodec) {
                        config.internalExport.audioCodec = newAudioCodec;
                    }

                    if (editorState != null && editorState.audioSourceEntity != null) {
                        ImGui.textUnformatted(I18n.get("flashback.audio_source.entity", editorState.audioSourceEntity));
                    } else {
                        ImGui.textUnformatted(I18n.get("flashback.audio_source.camera"));
                    }
                }
            } else {
                config.internalExport.recordAudio = false;
            }

            if (installedIncompatibleModsString != null) {
                ImGuiHelper.separatorWithText(I18n.get("flashback.incompatible_with_exporting"));
                ImGui.textWrapped(I18n.get("flashback.incompatible_with_exporting_description"));
                ImGui.pushTextWrapPos();
                ImGui.textColored(0xFF0000FF, installedIncompatibleModsString);
                ImGui.popTextWrapPos();
            }

            ImGui.dummy(0, 10 * ReplayUI.getUiScale());

            float buttonSize = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2f;
            if (ImGui.button(I18n.get("flashback.start_export"), buttonSize, ReplayUI.scaleUi(25))) {
                createExportSettings(null, config).thenAccept(settings -> {
                    if (settings != null) {
                        close = true;
                        Utils.exportSequenceCount += 1;
                        Flashback.EXPORT_JOB = new ExportJob(settings);
                        config.delayedSaveToDefaultFolder();
                    }
                });
            }
            ImGui.sameLine();
            if (ImGui.button(I18n.get("flashback.add_to_queue"), buttonSize, ReplayUI.scaleUi(25))) {
                jobName.set(I18n.get("flashback.job_n", ExportJobQueue.count()+1));
                ImGui.openPopup("QueuedJobName");
            }

            if (ImGui.beginPopup("QueuedJobName")) {
                ImGui.setNextItemWidth(100);
                ImGui.inputText(I18n.get("flashback.job_name"), jobName);

                if (ImGui.button(I18n.get("flashback.queue_job"))) {
                    createExportSettings(ImGuiHelper.getString(jobName), config).thenAccept(settings -> {
                        if (settings != null) {
                            close = true;
                            Utils.exportSequenceCount += 1;
                            ExportJobQueue.queuedJobs.add(settings);
                        }
                    });
                }
                ImGui.sameLine();
                if (ImGui.button(I18n.get("gui.back"))) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }

            ImGuiHelper.endPopupModalCloseable();
        }
    }

    private static void renderVideoOptions(EditorState editorState, FlashbackConfigV1 config) {
        if (editorState != null && !editorState.replayVisuals.renderSky) {
            if (ImGui.checkbox(I18n.get("flashback.transparent_sky"), config.internalExport.transparentBackground)) {
                config.internalExport.transparentBackground = !config.internalExport.transparentBackground;
            }
        } else {
            config.internalExport.transparentBackground = false;
        }

        VideoContainer[] containers;
        if (config.internalExport.transparentBackground) {
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
            ImGui.textUnformatted(I18n.get("flashback.no_supported_containers_found"));
            return;
        }

        if (config.internalExport.container == null || !Arrays.asList(containers).contains(config.internalExport.container)) {
            config.internalExport.container = containers[0];
        }

        config.internalExport.container = ImGuiHelper.enumCombo(I18n.get("flashback.container"), config.internalExport.container, containers);

        if (config.internalExport.container == VideoContainer.PNG_SEQUENCE) {
            ImGui.inputText(I18n.get("flashback.filenames"), pngSequenceFormat);
            return;
        }

        VideoCodec[] codecs = config.internalExport.container.getSupportedVideoCodecs(config.internalExport.transparentBackground);

        if (codecs.length == 0) {
            ImGui.textUnformatted(I18n.get("flashback.no_supported_codecs_found"));
            return;
        }

        if (config.internalExport.videoCodec == null || !Arrays.asList(codecs).contains(config.internalExport.videoCodec)) {
            config.internalExport.videoCodec = codecs[0];
        }

        if (codecs.length > 1) {
            VideoCodec newCodec = ImGuiHelper.enumCombo(I18n.get("flashback.codec"), config.internalExport.videoCodec, codecs);
            if (newCodec != config.internalExport.videoCodec) {
                config.internalExport.videoCodec = newCodec;
                config.internalExport.selectedVideoEncoder[0] = 0;
            }
        }

        String[] encoders = config.internalExport.videoCodec.getEncoders();
        if (encoders.length > 1) {
            ImGuiHelper.combo(I18n.get("flashback.encoder"), config.internalExport.selectedVideoEncoder, encoders);
        }

        if (config.internalExport.videoCodec != VideoCodec.GIF) {
            if (ImGui.checkbox(I18n.get("flashback.use_maximum_bitrate"), config.internalExport.useMaximumBitrate)) {
                config.internalExport.useMaximumBitrate = !config.internalExport.useMaximumBitrate;
            }
            if (!config.internalExport.useMaximumBitrate) {
                ImGui.inputText(I18n.get("flashback.bitrate"), bitrate);
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    int numBitrate = stringToBitrate(ImGuiHelper.getString(bitrate));
                    bitrate.set(bitrateToString(numBitrate));
                }
            }
        } else {
            ImGui.pushTextWrapPos();
            ImGui.textColored(0xFFFFFFFF, I18n.get("flashback.gif_output_warning"));
            ImGui.popTextWrapPos();
        }
    }

    private static CompletableFuture<ExportSettings> createExportSettings(@Nullable String name, FlashbackConfigV1 config) {
        int numBitrate;
        if (config.internalExport.useMaximumBitrate) {
            numBitrate = 0;
        } else {
            numBitrate = stringToBitrate(ImGuiHelper.getString(bitrate));
        }

        String defaultName = getDefaultFilename(name, config.internalExport.container.extension(), config);

        Function<String, ExportSettings> callback = pathStr -> {
            if (pathStr != null) {
                EditorState editorState = EditorStateManager.getCurrent();
                if (editorState == null) {
                    return null;
                }

                int start, end;
                if (startEndTick[0] >= 0 && startEndTick[1] >= 0) {
                    start = Math.max(0, startEndTick[0]);
                    end = Math.max(start, startEndTick[1]);
                } else {
                    var firstAndLastInTracks = editorState.getFirstAndLastTicksInTracks();
                    start = firstAndLastInTracks.start();
                    end = firstAndLastInTracks.end();

                    if (start < 0) {
                        start = 0;
                    }
                    if (end < 0 || start == end) {
                        ReplayServer replayServer = Flashback.getReplayServer();
                        if (replayServer != null) {
                            end = replayServer.getTotalReplayTicks();
                        } else {
                            end = start+100;
                        }
                    }
                }

                ReplayServer replayServer = Flashback.getReplayServer();
                if (replayServer != null) {
                    int totalTicks = replayServer.getTotalReplayTicks();
                    start = Math.min(start, totalTicks);
                    end = Math.min(end, totalTicks);
                }

                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null) {
                    return null;
                }

                boolean transparent = config.internalExport.transparentBackground && !editorState.replayVisuals.renderSky;
                String encoder = config.internalExport.videoCodec.getEncoders()[config.internalExport.selectedVideoEncoder[0]];

                VideoCodec useVideoCodec = config.internalExport.videoCodec;
                AudioCodec useAudioCodec = config.internalExport.audioCodec;
                boolean shouldRecordAudio = config.internalExport.recordAudio;

                if (config.internalExport.container == VideoContainer.PNG_SEQUENCE) {
                    useVideoCodec = null;
                    encoder = null;
                    shouldRecordAudio = false;
                }

                if (!shouldRecordAudio) {
                    useAudioCodec = null;
                }

                Path path = Path.of(pathStr);
                config.internalExport.defaultExportPath = path.getParent().toString();
                return new ExportSettings(name, editorState.copy(),
                    player.position(), player.getYRot(), player.getXRot(),
                    config.internalExport.resolution[0], config.internalExport.resolution[1], start, end,
                    Math.max(1, config.internalExport.framerate[0]), config.internalExport.resetRng, config.internalExport.container, useVideoCodec, encoder, numBitrate, transparent, config.internalExport.ssaa, config.internalExport.noGui,
                    shouldRecordAudio, config.internalExport.stereoAudio, useAudioCodec,
                    path, ImGuiHelper.getString(pngSequenceFormat));
            }

            return null;
        };

        String defaultExportPathString = config.internalExport.defaultExportPath;
        if (config.internalExport.container == VideoContainer.PNG_SEQUENCE) {
            return AsyncFileDialogs.openFolderDialog(defaultExportPathString).thenApply(callback);
        } else {
            return AsyncFileDialogs.saveFileDialog(defaultExportPathString, defaultName,
                config.internalExport.container.extension(), config.internalExport.container.extension()).thenApply(callback);
        }

    }

    public static @NotNull String getDefaultFilename(@Nullable String name, String extension, FlashbackConfigV1 config) {
        Path defaultPath = FabricLoader.getInstance().getGameDir();

        try {
            if (config.internalExport.defaultExportPath == null || config.internalExport.defaultExportPath.isBlank() || !Files.exists(Path.of(config.internalExport.defaultExportPath))) {
                config.internalExport.defaultExportPath = defaultPath.toString();
            } else {
                defaultPath = Path.of(config.internalExport.defaultExportPath);
            }
        } catch (Exception ignored) {}

        String defaultName = null;
        if (name != null) {
            try {
                defaultName = FileUtil.findAvailableName(defaultPath, name, "." + extension);
            } catch (Exception ignored) {}
        }
        if (defaultName == null) {
            String desiredName = Utils.resolveFilenameTemplate(Flashback.getConfig().exporting.defaultExportFilename);
            try {
                defaultName = FileUtil.findAvailableName(defaultPath, desiredName, "." + extension);
            } catch (Exception ignored) {}
        }
        if (defaultName == null) {
            String desiredName = Utils.resolveFilenameTemplate(Flashback.getConfig().exporting.defaultExportFilename);
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
        if (editorState == null) {
            startEndTick[0] = -1;
            startEndTick[1] = -1;
            return;
        }

        var startAndEnd = editorState.getExportStartAndEnd();
        startEndTick[0] = startAndEnd.start();
        startEndTick[1] = startAndEnd.end();
    }

}
