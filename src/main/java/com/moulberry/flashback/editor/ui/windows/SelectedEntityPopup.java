package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.state.EditorState;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public class SelectedEntityPopup {

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
