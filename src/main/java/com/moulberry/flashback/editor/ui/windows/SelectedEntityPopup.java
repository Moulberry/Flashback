package com.moulberry.flashback.editor.ui.windows;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.blaze3d.platform.Window;
import com.moulberry.flashback.FilePlayerSkin;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.combo_options.GlowingOverride;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.state.EditorState;
import imgui.ImGui;
import imgui.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SelectedEntityPopup {

    private static ImString changeSkinInput = ImGuiHelper.createResizableImString("");
    static {
        changeSkinInput.inputData.allowedChars = "0123456789abcdef-";
    }
    private static ImString changeNameInput = ImGuiHelper.createResizableImString("");

    public static void open(Entity entity, EditorState editorState) {
        String nameOverride = editorState.nameOverride.get(entity.getUUID());
        if (nameOverride != null) {
            changeNameInput.set(nameOverride);
        } else {
            changeNameInput.set("");
        }

        GameProfile skinOverride = editorState.skinOverride.get(entity.getUUID());
        if (skinOverride != null) {
            changeSkinInput.set(skinOverride.getId().toString());
        } else {
            changeSkinInput.set("");
        }
    }

    public static void render(Entity entity, EditorState editorState) {
        UUID uuid = entity.getUUID();
        ImGui.text("Entity: " + uuid);

        ImGui.separator();

        if (ImGui.button("Look At")) {
            Minecraft.getInstance().cameraEntity.lookAt(EntityAnchorArgument.Anchor.EYES, entity.getEyePosition());
        }
        ImGui.sameLine();
        if (ImGui.button("Spectate")) {
            Minecraft.getInstance().player.connection.sendUnsignedCommand("spectate " + entity.getUUID());
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button("Copy UUID")) {
            GLFW.glfwSetClipboardString(Minecraft.getInstance().getWindow().getWindow(), entity.getUUID().toString());
            ReplayUI.setInfoOverlay("Copied '" + entity.getUUID() + "'");
            ImGui.closeCurrentPopup();
        }
        if (uuid.equals(editorState.audioSourceEntity)) {
            if (ImGui.button("Unset Audio Source")) {
                editorState.audioSourceEntity = null;
                editorState.markDirty();
            }
        } else if (ImGui.button("Set Audio Source")) {
            editorState.audioSourceEntity = entity.getUUID();
            editorState.markDirty();
        }
        boolean isHiddenDuringExport = editorState.hideDuringExport.contains(entity.getUUID());
        if (ImGui.checkbox("Hide During Export", isHiddenDuringExport)) {
            if (isHiddenDuringExport) {
                editorState.hideDuringExport.remove(entity.getUUID());
            } else {
                editorState.hideDuringExport.add(entity.getUUID());
            }
            editorState.markDirty();
        }

        if (!isHiddenDuringExport) {
            if (entity instanceof Player player) {
                boolean hideNametag = editorState.hideNametags.contains(entity.getUUID());
                if (ImGui.checkbox("Render Nametag", !hideNametag)) {
                    if (hideNametag) {
                        editorState.hideNametags.remove(entity.getUUID());
                    } else {
                        editorState.hideNametags.add(entity.getUUID());
                    }
                }

                if (!hideNametag) {
                    boolean changedName = ImGui.inputTextWithHint("Name##SetNameInput", player.getScoreboardName(), changeNameInput);

                    if (changedName) {
                        String string = ImGuiHelper.getString(changeNameInput);
                        if (string.isEmpty()) {
                            editorState.nameOverride.remove(entity.getUUID());
                        } else {
                            editorState.nameOverride.put(entity.getUUID(), string);
                        }
                    }

                    if (editorState.hideTeamPrefix.contains(player.getUUID())) {
                        if (ImGui.checkbox("Hide Team Prefix", true)) {
                            editorState.hideTeamPrefix.remove(player.getUUID());
                        }
                    } else {
                        PlayerTeam team = player.getTeam();
                        if (team != null && !Utils.isComponentEmpty(team.getPlayerPrefix())) {
                            if (ImGui.checkbox("Hide Team Prefix", false)) {
                                editorState.hideTeamPrefix.add(player.getUUID());
                            }
                        }
                    }

                    if (editorState.hideTeamSuffix.contains(player.getUUID())) {
                        if (ImGui.checkbox("Hide Team Suffix", true)) {
                            editorState.hideTeamSuffix.remove(player.getUUID());
                        }
                    } else {
                        PlayerTeam team = player.getTeam();
                        if (team != null && !Utils.isComponentEmpty(team.getPlayerSuffix())) {
                            if (ImGui.checkbox("Hide Team Suffix", false)) {
                                editorState.hideTeamSuffix.add(player.getUUID());
                            }
                        }
                    }
                }

                showGlowingDropdown(entity, editorState);

                ImGuiHelper.separatorWithText("Change Skin & Cape (UUID)");
                ImGui.setNextItemWidth(320);
                ImGui.inputTextWithHint("##SetSkinInput", "e.g. d0e05de7-6067-454d-beae-c6d19d886191", changeSkinInput);

                if (!changeSkinInput.isEmpty()) {
                    String string = ImGuiHelper.getString(changeSkinInput);
                    try {
                        UUID changeSkinUuid = UUID.fromString(string);
                        if (ImGui.button("Apply Skin from UUID")) {
                            ProfileResult profile = Minecraft.getInstance().getMinecraftSessionService().fetchProfile(changeSkinUuid, true);
                            editorState.skinOverride.put(entity.getUUID(), profile.profile());
                            editorState.skinOverrideFromFile.remove(entity.getUUID());
                        }
                    } catch (Exception ignored) {}
                }

                if (ImGui.button("Upload Skin from File")) {
                    Path gameDir = FabricLoader.getInstance().getGameDir();
                    CompletableFuture<String> future = AsyncFileDialogs.openFileDialog(gameDir.toString(),
                        "Skin Texture", "png");
                    future.thenAccept(pathStr -> {
                        if (pathStr != null) {
                            editorState.skinOverride.remove(entity.getUUID());
                            editorState.skinOverrideFromFile.put(entity.getUUID(), new FilePlayerSkin(pathStr));
                        }
                    });
                }

                if (editorState.skinOverride.containsKey(entity.getUUID()) || editorState.skinOverrideFromFile.containsKey(entity.getUUID())) {
                    if (ImGui.button("Reset Skin")) {
                        editorState.skinOverride.remove(entity.getUUID());
                        editorState.skinOverrideFromFile.remove(entity.getUUID());
                        changeSkinInput.set("");
                    }
                }
            } else {
                showGlowingDropdown(entity, editorState);
            }
        }

//        ImGui.sameLine();
//        ImGui.button("Track Entity");
//
//        ImGui.checkbox("Force Glowing", false);
//        ImGui.sameLine();
//        ImGui.colorButton("Glow Colour", new float[4]);
//        ImGui.sameLine();
//        ImGui.text("Glow Colour");
//
//        if (entity instanceof LivingEntity) {
//            ImGui.checkbox("Show Nametag", true);
//            ImGui.checkbox("Override Nametag", false);
//        }
//        if (entity instanceof Player) {
//            ImGui.checkbox("Override Skin", false);
//        }
    }

    private static void showGlowingDropdown(Entity entity, EditorState editorState) {
        GlowingOverride glowingOverride = GlowingOverride.DEFAULT;
        if (editorState.glowingOverride.containsKey(entity.getUUID())) {
            glowingOverride = editorState.glowingOverride.get(entity.getUUID());
        }
        GlowingOverride newGlowingOverride = ImGuiHelper.enumCombo("Glowing", glowingOverride);
        if (newGlowingOverride != glowingOverride) {
            if (newGlowingOverride == GlowingOverride.DEFAULT) {
                editorState.glowingOverride.remove(entity.getUUID());
            } else {
                editorState.glowingOverride.put(entity.getUUID(), newGlowingOverride);
            }
        }
    }

}
