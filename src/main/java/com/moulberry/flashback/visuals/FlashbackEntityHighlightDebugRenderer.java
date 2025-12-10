package com.moulberry.flashback.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class FlashbackEntityHighlightDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    private final UUID uuid;
    private final int colour;

    public FlashbackEntityHighlightDebugRenderer(Minecraft minecraft, UUID uuid, int colour) {
        this.minecraft = minecraft;
        this.uuid = uuid;
        this.colour = colour;
    }

    @Override
    public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float partialTick) {
        var entity = this.minecraft.level.getEntity(this.uuid);
        if (entity == null) {
            return;
        }

        Vec3 position = entity.position();
        Vec3 interpPosition = entity.getPosition(partialTick);
        Vec3 interpDelta = interpPosition.subtract(position);
        Gizmos.cuboid(entity.getBoundingBox().move(interpDelta), GizmoStyle.stroke(colour));
    }

}
