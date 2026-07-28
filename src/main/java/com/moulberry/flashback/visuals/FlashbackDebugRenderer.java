package com.moulberry.flashback.visuals;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

public class FlashbackDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;

    public FlashbackDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float partialTick) {
        if (!Flashback.isInReplay() || this.minecraft.level == null) {
            return;
        }

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            return;
        }

        if (Flashback.isExporting() || !ReplayUI.isActive()) {
            return;
        }

        // Selected entity (yellow outline)
        UUID selected = ReplayUI.getSelectedEntity();
        if (selected != null) {
            boundingBox(selected, 0xFFFFFF00, partialTick);
        }

        // Audio source entity (cyan outline)
        UUID audioSource = editorState.audioSourceEntity;
        if (audioSource != null && !Objects.equals(audioSource, selected)) {
            boundingBox(audioSource, 0xFF00FFFF, partialTick);
        }

        // Hidden entities (translucent white outline)
        if (editorState.maybeHasHiddenEntities()) {
            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
                if (!editorState.isEntityHidden(entity) || entity == this.minecraft.player) {
                    continue;
                }

                boundingBox(entity, 0x30FFFFFF, partialTick);
            }
        }

    }

    private void boundingBox(UUID uuid, int colour, float partialTick) {
        Entity entity = this.minecraft.level.getEntity(uuid);
        if (entity != null) {
            boundingBox(entity, colour, partialTick);
        }
    }

    private void boundingBox(Entity entity, int colour, float partialTick) {
        Vec3 position = entity.position();
        Vec3 interpPosition = entity.getPosition(partialTick);
        Vec3 interpDelta = interpPosition.subtract(position);
        Gizmos.cuboid(entity.getBoundingBox().move(interpDelta), GizmoStyle.stroke(colour));
    }

}
