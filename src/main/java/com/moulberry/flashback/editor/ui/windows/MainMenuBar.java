package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.exporting.ExportJobQueue;
import com.moulberry.flashback.screen.select_replay.SelectReplayScreen;
import imgui.moulberry90.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;

public class MainMenuBar {

    public static void render() {
        if (ImGui.beginMainMenuBar()) {
            renderInner();
            ImGui.endMainMenuBar();
        }
    }

    public static void renderInner() {
        FlashbackConfigV1 config = Flashback.getConfig();

        if (ImGui.beginMenu(I18n.get("flashback.menu.file") + "##File")) {
            if (ImGui.menuItem(I18n.get("flashback.menu.file.export_video") + "##ExportVideo")) {
                StartExportWindow.open();
            }
            if (!ExportJobQueue.queuedJobs.isEmpty()) {
                String name = I18n.get("flashback.menu.file.export_queue", ExportJobQueue.count());
                if (ImGui.menuItem(name + "###QueuedJobs")) {
                    ExportQueueWindow.open();
                }
            }
            if (ImGui.menuItem(I18n.get("flashback.export_screenshot") + "##ExportScreenshot")) {
                ExportScreenshotWindow.open();
            }
            ImGui.separator();
            if (ImGui.menuItem(I18n.get("flashback.select_replay.open") + "##Open")) {
                Flashback.openReplayFromFileBrowser();
            }
            if (!config.internal.recentReplays.isEmpty()) {
                if (ImGui.beginMenu(I18n.get("flashback.open_recent_replay") + "##OpenRecentReplay")) {
                    Path replayFolder = Flashback.getReplayFolder();
                    for (String recentReplay : config.internal.recentReplays) {
                        Path path = Path.of(recentReplay);

                        if (Files.exists(path)) {
                            String display = path.toString();

                            try {
                                Path relative = replayFolder.relativize(path);
                                String relativeStr = relative.toString();
                                if (!relativeStr.contains("..")) {
                                    display = relativeStr;
                                }
                            } catch (Exception ignored) {}

                            if (ImGui.menuItem(display)) {
                                Flashback.openReplayWorld(path);
                                break;
                            }
                        }
                    }
                    ImGui.endMenu();
                }
            }
            if (ImGui.menuItem(I18n.get("flashback.exit_replay") + "##ExitReplay")) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    minecraft.level.disconnect(Component.empty());
                }
                minecraft.disconnectWithProgressScreen();
                minecraft.setScreen(new SelectReplayScreen(new TitleScreen()));
            }
            ImGui.endMenu();
        }
        if (ImGui.menuItem(I18n.get("flashback.preferences") + "##Preferences")) {
            PreferencesWindow.open();
        }

        ImGui.separator();

        if (ImGui.menuItem(I18n.get("flashback.player_list") + "##PlayerList")) {
            toggleWindow("player_list");
        }
        if (ImGui.menuItem(I18n.get("flashback.movement") + "##Movement")) {
            toggleWindow("movement");
        }
        if (ImGui.menuItem(I18n.get("flashback.render_filter") + "##RenderFilter")) {
            toggleWindow("render_filter");
        }

        ImGui.separator();

        if (ImGui.menuItem(I18n.get("flashback.hide_replay_ui") + "##HideReplayUI")) {
            Minecraft.getInstance().options.hideGui = true;
        }
    }

    private static void toggleWindow(String windowName) {
        var openedWindows = Flashback.getConfig().internal.openedWindows;
        boolean playerListIsOpen = openedWindows.contains(windowName);
        if (playerListIsOpen) {
            openedWindows.remove(windowName);
        } else {
            openedWindows.add(windowName);
        }
        Flashback.getConfig().delayedSaveToDefaultFolder();
    }

}
