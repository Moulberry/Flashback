package com.moulberry.flashback.editor.ui.windows;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.WindowOpenState;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PlayerListWindow {

    private static String lastSearch = null;
    private static long lastUpdate = 0;
    private static boolean includeNpcs = false;
    private static boolean hasNpcs = false;
    private static final ImString search = ImGuiHelper.createResizableImString("");
    private static final List<PlayerInfo> searchedPlayers = new ArrayList<>();

    private static void updatePlayerList() {
        searchedPlayers.clear();
        hasNpcs = false;

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        Entity camera = Minecraft.getInstance().getCameraEntity();

        record SearchEntry(double distanceSq, PlayerInfo info) {}
        List<SearchEntry> searchEntries = new ArrayList<>();
        List<SearchEntry> searchEntriesLowPriority = new ArrayList<>();

        String searchLower = lastSearch.toLowerCase(Locale.ROOT);
        boolean blankSearch = lastSearch.isBlank();

        for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
            GameProfile profile = playerInfo.getProfile();

            if (profile.getId().equals(Minecraft.getInstance().player.getUUID())) {
                continue;
            }

            if (profile.getId().version() != 4) {
                hasNpcs = true;
                if (!includeNpcs) {
                    continue;
                }
            }

            double distanceSq = Double.MAX_VALUE;

            if (level != null && camera != null) {
                Player player = level.getPlayerByUUID(profile.getId());
                if (player != null) {
                    distanceSq = player.distanceToSqr(camera);
                }
            }

            SearchEntry searchEntry = new SearchEntry(distanceSq, playerInfo);
            if (blankSearch) {
                searchEntries.add(searchEntry);
            } else {
                String nameLower = profile.getName().toLowerCase(Locale.ROOT);
                if (nameLower.startsWith(searchLower)) {
                    searchEntries.add(searchEntry);
                } else if (nameLower.contains(searchLower)) {
                    searchEntriesLowPriority.add(searchEntry);
                }
            }
        }

        searchEntries.sort(Comparator.comparingDouble(SearchEntry::distanceSq));
        searchEntriesLowPriority.sort(Comparator.comparingDouble(SearchEntry::distanceSq));
        if (searchEntries.size() < 16) {
            searchEntries.addAll(searchEntriesLowPriority);
        }

        int count = Math.min(16, searchEntries.size());
        for (int i = 0; i < count; i++) {
            searchedPlayers.add(searchEntries.get(i).info);
        }
    }

    public static void render(ImBoolean open, boolean newlyOpened) {
        if (newlyOpened) {
            ImGuiViewport viewport = ImGui.getMainViewport();
            ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing, 0.5f, 0.5f);
        }

        ImGui.setNextWindowSizeConstraints(250, 50, 5000, 5000);
        if (ImGui.begin("Player List###PlayerList", open, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoFocusOnAppearing)) {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - ImGuiHelper.calcTextWidth("Search") - 32);
            if (ImGui.inputText("Search", search)) {
                lastSearch = null;
            }

            long currentTime = System.currentTimeMillis();
            if (lastSearch == null || currentTime > lastUpdate + 3000 || currentTime < lastUpdate) {
                lastSearch = ImGuiHelper.getString(search);
                lastUpdate = currentTime;
                updatePlayerList();
            }

            if (hasNpcs || includeNpcs) {
                if (ImGui.checkbox("Include NPCs", includeNpcs)) {
                    includeNpcs = !includeNpcs;
                    lastSearch = null;
                }
            }

            EditorState editorState = EditorStateManager.getCurrent();

            ImGui.separator();

            for (int i = 0; i < searchedPlayers.size(); i++) {
                ImGui.pushID(i);
                PlayerInfo playerInfo = searchedPlayers.get(i);
                GameProfile profile = playerInfo.getProfile();

                ImGui.text(profile.getName());
                ImGui.sameLine();
                if (ImGui.smallButton("TP")) {
                    Minecraft.getInstance().getConnection().sendUnsignedCommand("teleport " + profile.getId());
                    lastUpdate = currentTime;
                }
                if (editorState != null) {
                    ImGui.sameLine();
                    if (editorState.hideDuringExport.contains(profile.getId())) {
                        if (ImGui.smallButton("Show")) {
                            editorState.hideDuringExport.remove(profile.getId());
                            lastUpdate = currentTime;
                        }
                    } else if (ImGui.smallButton("Hide")) {
                        editorState.hideDuringExport.add(profile.getId());
                        lastUpdate = currentTime;
                    }
                }
                ImGui.sameLine();
                if (ImGui.smallButton("Spectate")) {
                    Minecraft.getInstance().getConnection().sendUnsignedCommand("spectate " + profile.getId());
                    lastUpdate = currentTime;
                }
                ImGui.popID();
            }

            ImGui.separator();

            if (editorState != null) {
                if (ImGui.button("Hide All")) {
                    changeVisibilityOfAll(editorState, false);
                    lastUpdate = currentTime;
                }
                ImGui.sameLine();
                if (ImGui.button("Show All")) {
                    changeVisibilityOfAll(editorState, true);
                    lastUpdate = currentTime;
                }
            }
        }
        ImGui.end();
    }

    private static void changeVisibilityOfAll(EditorState editorState, boolean visible) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }

        String searchLower = lastSearch.toLowerCase(Locale.ROOT);
        boolean blankSearch = lastSearch.isBlank();

        for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
            GameProfile profile = playerInfo.getProfile();

            if (profile.getId().equals(Minecraft.getInstance().player.getUUID())) {
                continue;
            }

            if (profile.getId().version() != 4 && !includeNpcs) {
                continue;
            }

            if (blankSearch) {
                if (visible) {
                    editorState.hideDuringExport.remove(profile.getId());
                } else {
                    editorState.hideDuringExport.add(profile.getId());
                }
            } else {
                String nameLower = profile.getName().toLowerCase(Locale.ROOT);
                if (nameLower.contains(searchLower)) {
                    if (visible) {
                        editorState.hideDuringExport.remove(profile.getId());
                    } else {
                        editorState.hideDuringExport.add(profile.getId());
                    }
                }
            }
        }
    }

}
