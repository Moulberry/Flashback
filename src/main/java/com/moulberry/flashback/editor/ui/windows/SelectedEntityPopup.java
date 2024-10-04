package com.moulberry.flashback.editor.ui.windows;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.state.EditorState;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

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

        if (!isHiddenDuringExport && entity instanceof Player player) {
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
            }

            ImGuiHelper.separatorWithText("Change Skin & Cape (UUID)");
            ImGui.setNextItemWidth(320);
            ImGui.inputTextWithHint("##SetSkinInput", "e.g. d0e05de7-6067-454d-beae-c6d19d886191", changeSkinInput);

            boolean hasApplySkin = false;
            if (!changeSkinInput.isEmpty()) {
                String string = ImGuiHelper.getString(changeSkinInput);
                try {
                    UUID changeSkinUuid = UUID.fromString(string);
                    if (ImGui.button("Apply Skin")) {
                        ProfileResult profile = Minecraft.getInstance().getMinecraftSessionService().fetchProfile(changeSkinUuid, true);
                        editorState.skinOverride.put(entity.getUUID(), profile.profile());
                    }
                    hasApplySkin = true;
                } catch (Exception ignored) {}
            }

            if (editorState.skinOverride.containsKey(entity.getUUID())) {
                if (hasApplySkin) {
                    ImGui.sameLine();
                }
                if (ImGui.button("Reset Skin")) {
                    editorState.skinOverride.remove(entity.getUUID());
                    changeSkinInput.set("");
                }
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

}
