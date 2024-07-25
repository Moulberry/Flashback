package com.moulberry.flashback.editor.ui.windows;

import com.mojang.blaze3d.platform.Window;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.combo_options.VideoPreset;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateCache;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.playback.ReplayServer;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImFloat;
import imgui.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.List;

public class StartExportWindow {

    private static boolean open = false;

    private static final int[] lastFramebufferSize = new int[]{0, 0};

    private static final int[] resolution = new int[]{1920, 1080};
    private static final int[] startEndTick = new int[]{0, 100};
    private static final ImFloat framerate = new ImFloat(60);
    private static VideoContainer container = VideoContainer.MP4;
    private static VideoCodec codec = VideoCodec.H264;
    private static VideoPreset preset = VideoPreset.MEDIUM;
    private static int[] selectedEncoder = new int[]{0};
    private static boolean useMaximumBitrate = false;
    private static final ImString bitrate = ImGuiHelper.createResizableImString("20m");

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
            ImGuiHelper.separatorWithText("Capture Options");

            ImGui.inputInt2("Resolution", resolution);
            if (ImGui.inputInt2("Start/end tick", startEndTick)) {
                EditorState editorState = EditorStateCache.getCurrent();
                ReplayServer replayServer = Flashback.getReplayServer();
                if (editorState != null && replayServer != null) {
                    editorState.setExportTicks(startEndTick[0], startEndTick[1], replayServer.getTotalReplayTicks());
                }
            }
            ImGui.inputFloat("Framerate", framerate);

            ImGuiHelper.separatorWithText("Video Options");

            VideoContainer newContainer = ImGuiHelper.enumCombo("Container", container);
            if (newContainer != container) {
                container = newContainer;

                boolean supported = false;
                for (VideoCodec supportedCodec : container.getSupportedCodecs()) {
                    if (codec == supportedCodec) {
                        supported = true;
                        break;
                    }
                }

                if (!supported) {
                    codec = container.getSupportedCodecs()[0];
                }
            }

            VideoCodec newCodec = ImGuiHelper.enumCombo("Codec", codec, container.getSupportedCodecs());
            if (newCodec != codec) {
                codec = newCodec;
                selectedEncoder[0] = 0;
            }

            String[] encoders = codec.getEncoders();
            if (encoders.length > 1) {
                ImGuiHelper.combo("Encoder", selectedEncoder, encoders);
            }

//            VideoPreset newPreset = ImGuiHelper.enumCombo("Preset", preset);
//            if (newPreset != preset) {
//                preset = newPreset;
//
//                useMaximumBitrate = false;
//                if (preset.ordinal() <= VideoPreset.SUPERFAST.ordinal()) {
//                    bitrate.set("5m");
//                } else if (preset.ordinal() <= VideoPreset.MEDIUM.ordinal()) {
//                    bitrate.set("10m");
//                } else {
//                    bitrate.set("20m");
//                }
//            }

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

            if (ImGui.button("Begin")) {
                int numBitrate;
                if (useMaximumBitrate) {
                    numBitrate = 0;
                } else {
                    numBitrate = stringToBitrate(ImGuiHelper.getString(bitrate));
                }

                String encoder = codec.getEncoders()[selectedEncoder[0]];

                Path defaultExportPath = FabricLoader.getInstance().getGameDir();
                String defaultExportPathString = defaultExportPath.toString();
                AsyncFileDialogs.saveFileDialog(defaultExportPathString, "output." + container.extension(),
                        container.extension(), container.extension()).thenAccept(pathStr -> {
                    if (pathStr != null) {
                        int start = Math.max(0, startEndTick[0]);
                        int end = Math.max(start, startEndTick[1]);

                        ReplayServer replayServer = Flashback.getReplayServer();
                        if (replayServer != null) {
                            int totalTicks = replayServer.getTotalReplayTicks();
                            start = Math.min(start, totalTicks);
                            end = Math.min(end, totalTicks);
                        }

                        Path path = Path.of(pathStr);
                        ExportSettings settings = new ExportSettings(resolution[0], resolution[1], start, end,
                                Math.max(1, framerate.get()), container, codec, encoder, numBitrate, path);
                        Flashback.EXPORT_JOB = new ExportJob(settings);
                    }
                });

                ImGui.closeCurrentPopup();
            }

            ImGuiHelper.endPopupModalCloseable();
        }
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

        EditorState editorState = EditorStateCache.getCurrent();

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
