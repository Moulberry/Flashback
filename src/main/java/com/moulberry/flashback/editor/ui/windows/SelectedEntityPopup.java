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
import imgui.flashback.ImGui;
import imgui.flashback.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.EnumSet;
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
            changeSkinInput.set(skinOverride.id().toString());
        } else {
            changeSkinInput.set("");
        }
    }

    public static void render(Entity entity, EditorState editorState) {
        UUID uuid = entity.getUUID();
        ImGui.textUnformatted(I18n.get("flashback.entity_label", uuid));

        ImGui.separator();

        if (ImGui.button(I18n.get("flashback.look_at"))) {
            Minecraft.getInstance().getCameraEntity().lookAt(EntityAnchorArgument.Anchor.EYES, entity.getEyePosition());
        }
        ImGui.sameLine();
        if (ImGui.button(I18n.get("flashback.spectate"))) {
            Minecraft.getInstance().player.connection.sendCommand("spectate " + entity.getUUID());
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(I18n.get("flashback.copy_uuid"))) {
            Minecraft.getInstance().keyboardHandler.setClipboard(entity.getUUID().toString());
            ReplayUI.setInfoOverlay("Copied '" + entity.getUUID() + "'");
            ImGui.closeCurrentPopup();
        }
        if (uuid.equals(editorState.audioSourceEntity)) {
            if (ImGui.button(I18n.get("flashback.unset_audio_source"))) {
                editorState.audioSourceEntity = null;
                editorState.markDirty();
            }
        } else if (ImGui.button(I18n.get("flashback.set_audio_source"))) {
            editorState.audioSourceEntity = entity.getUUID();
            editorState.markDirty();
            Minecraft.getInstance().levelRenderer.debugRenderer.refreshRendererList();
        }
        boolean isHiddenDuringExport = editorState.hideDuringExport.contains(entity.getUUID());
        if (ImGui.checkbox(I18n.get("flashback.hide_during_export"), isHiddenDuringExport)) {
            if (isHiddenDuringExport) {
                editorState.hideDuringExport.remove(entity.getUUID());
            } else {
                editorState.hideDuringExport.add(entity.getUUID());
            }
            editorState.markDirty();
        }

        if (!isHiddenDuringExport) {
            if (entity instanceof AbstractClientPlayer player) {
                if (editorState.hideCape.contains(player.getUUID())) {
                    if (ImGui.checkbox(I18n.get("flashback.hide_cape"), true)) {
                        editorState.hideCape.remove(player.getUUID());
                    }
                } else if (player.isModelPartShown(PlayerModelPart.CAPE) && player.getSkin().cape() != null) {
                    if (ImGui.checkbox(I18n.get("flashback.hide_cape"), false)) {
                        editorState.hideCape.add(player.getUUID());
                    }
                }

                boolean hideNametag = editorState.hideNametags.contains(entity.getUUID());
                if (ImGui.checkbox(I18n.get("flashback.render_nametag"), !hideNametag)) {
                    if (hideNametag) {
                        editorState.hideNametags.remove(entity.getUUID());
                    } else {
                        editorState.hideNametags.add(entity.getUUID());
                    }
                }

                if (!hideNametag) {
                    String nameTitle = I18n.get("flashback.name");
                    boolean changedName = ImGui.inputTextWithHint(nameTitle+"##SetNameInput", player.getScoreboardName(), changeNameInput);

                    if (changedName) {
                        String string = ImGuiHelper.getString(changeNameInput);
                        if (string.isEmpty()) {
                            editorState.nameOverride.remove(entity.getUUID());
                        } else {
                            editorState.nameOverride.put(entity.getUUID(), string);
                        }
                    }

                    if (editorState.hideTeamPrefix.contains(player.getUUID())) {
                        if (ImGui.checkbox(I18n.get("flashback.hide_team_prefix"), true)) {
                            editorState.hideTeamPrefix.remove(player.getUUID());
                        }
                    } else {
                        PlayerTeam team = player.getTeam();
                        if (team != null && !Utils.isComponentEmpty(team.getPlayerPrefix())) {
                            if (ImGui.checkbox(I18n.get("flashback.hide_team_prefix"), false)) {
                                editorState.hideTeamPrefix.add(player.getUUID());
                            }
                        }
                    }

                    if (editorState.hideTeamSuffix.contains(player.getUUID())) {
                        if (ImGui.checkbox(I18n.get("flashback.hide_team_suffix"), true)) {
                            editorState.hideTeamSuffix.remove(player.getUUID());
                        }
                    } else {
                        PlayerTeam team = player.getTeam();
                        if (team != null && !Utils.isComponentEmpty(team.getPlayerSuffix())) {
                            if (ImGui.checkbox(I18n.get("flashback.hide_team_suffix"), false)) {
                                editorState.hideTeamSuffix.add(player.getUUID());
                            }
                        }
                    }

                    if (editorState.hideBelowName.contains(player.getUUID())) {
                        if (ImGui.checkbox(I18n.get("flashback.hide_text_below_name"), true)) {
                            editorState.hideBelowName.remove(player.getUUID());
                        }
                    } else {
                        Scoreboard scoreboard = player.level().getScoreboard();
                        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
                        if (objective != null) {
                            if (ImGui.checkbox(I18n.get("flashback.hide_text_below_name"), false)) {
                                editorState.hideBelowName.add(player.getUUID());
                            }
                        }
                    }
                }

                showGlowingDropdown(entity, editorState);

                ImGuiHelper.separatorWithText(I18n.get("flashback.change_skin_and_cape"));
                ImGui.setNextItemWidth(320);
                ImGui.inputTextWithHint("##SetSkinInput", "e.g. d0e05de7-6067-454d-beae-c6d19d886191", changeSkinInput);

                if (!changeSkinInput.isEmpty()) {
                    String string = ImGuiHelper.getString(changeSkinInput);
                    try {
                        UUID changeSkinUuid = UUID.fromString(string);
                        if (ImGui.button(I18n.get("flashback.apply_skin_from_uuid"))) {
                            ProfileResult profile = Minecraft.getInstance().services().sessionService().fetchProfile(changeSkinUuid, true);
                            if (profile != null) {
                                editorState.skinOverride.put(entity.getUUID(), profile.profile());
                                editorState.skinOverrideFromFile.remove(entity.getUUID());
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (ImGui.button(I18n.get("flashback.upload_skin_from_file"))) {
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
                    if (ImGui.button(I18n.get("flashback.reset_skin"))) {
                        editorState.skinOverride.remove(entity.getUUID());
                        editorState.skinOverrideFromFile.remove(entity.getUUID());
                        changeSkinInput.set("");
                    }
                }
            } else {
                showGlowingDropdown(entity, editorState);
            }

            if (entity instanceof LivingEntity) {
                if (ImGui.collapsingHeader("Equipment")) {
                    EnumSet<EquipmentSlot> hiddenEquipment = editorState.hiddenEquipment.get(entity.getUUID());
                    if (hiddenEquipment == null) {
                        hiddenEquipment = EnumSet.noneOf(EquipmentSlot.class);
                    } else {
                        hiddenEquipment = hiddenEquipment.clone();
                    }

                    boolean changed = false;

                    for (EquipmentSlot value : EquipmentSlot.values()) {
                        if (entity instanceof Player && (value == EquipmentSlot.BODY || value == EquipmentSlot.SADDLE)) {
                            continue;
                        }

                        boolean hidden = hiddenEquipment.contains(value);
                        if (ImGui.checkbox(value.getName(), !hidden)) {
                            if (hidden) {
                                hiddenEquipment.remove(value);
                            } else {
                                hiddenEquipment.add(value);
                            }
                            changed = true;
                        }
                    }

                    if (changed) {
                        if (hiddenEquipment.isEmpty()) {
                            editorState.hiddenEquipment.remove(entity.getUUID());
                        } else {
                            editorState.hiddenEquipment.put(entity.getUUID(), hiddenEquipment);
                        }
                    }
                }
            }
        }
    }

    private static void showGlowingDropdown(Entity entity, EditorState editorState) {
        GlowingOverride glowingOverride = GlowingOverride.DEFAULT;
        if (editorState.glowingOverride.containsKey(entity.getUUID())) {
            glowingOverride = editorState.glowingOverride.get(entity.getUUID());
        }
        GlowingOverride newGlowingOverride = ImGuiHelper.enumCombo(I18n.get("flashback.glowing"), glowingOverride);
        if (newGlowingOverride != glowingOverride) {
            if (newGlowingOverride == GlowingOverride.DEFAULT) {
                editorState.glowingOverride.remove(entity.getUUID());
            } else {
                editorState.glowingOverride.put(entity.getUUID(), newGlowingOverride);
            }
        }
    }

}
