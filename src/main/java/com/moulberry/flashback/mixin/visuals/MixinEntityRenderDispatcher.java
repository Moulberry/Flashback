package com.moulberry.flashback.mixin.visuals;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = EntityRenderDispatcher.class, priority = 990)
public abstract class MixinEntityRenderDispatcher {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    public void shouldRender(Entity entity, Frustum frustum, double d, double e, double f, CallbackInfoReturnable<Boolean> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.filteredEntities.isEmpty()) {
                String id = entity.getType().builtInRegistryHolder().key().location().toString();
                if (editorState.filteredEntities.contains(id)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
            if (editorState.hideDuringExport.contains(entity.getUUID())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    public void extractEntity(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState renderState = cir.getReturnValue();

        if (Flashback.isInReplay()) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState == null) {
                return;
            }

            if (!Flashback.isExporting()) {
                if (ReplayUI.isEntitySelected(entity.getUUID())) {
                    // Add a yellow outline to selected entity
                    AABB aABB = entity.getBoundingBox();
                    HitboxRenderState hitboxRenderState = new HitboxRenderState(aABB.minX - entity.getX(), aABB.minY - entity.getY(), aABB.minZ - entity.getZ(),
                            aABB.maxX - entity.getX(), aABB.maxY - entity.getY(), aABB.maxZ - entity.getZ(), 1.0F, 1.0F, 0.0F);
                    Vec3 view = entity.getViewVector(partialTick);
                    renderState.hitboxesRenderState = new HitboxesRenderState(view.x, view.y, view.z, ImmutableList.of(hitboxRenderState));
                } else if (entity.getUUID().equals(editorState.audioSourceEntity)) {
                    // Add a cyan outline to audio source entity
                    AABB aABB = entity.getBoundingBox();
                    HitboxRenderState hitboxRenderState = new HitboxRenderState(aABB.minX - entity.getX(), aABB.minY - entity.getY(), aABB.minZ - entity.getZ(),
                            aABB.maxX - entity.getX(), aABB.maxY - entity.getY(), aABB.maxZ - entity.getZ(), 0.0F, 1.0F, 1.0F);
                    Vec3 view = entity.getViewVector(partialTick);
                    renderState.hitboxesRenderState = new HitboxesRenderState(view.x, view.y, view.z, ImmutableList.of(hitboxRenderState));
                }
            }

            // Prevent rendering shadows when blocks are turned off
            if (!editorState.replayVisuals.renderBlocks) {
                renderState.shadowRadius = 0f;
                renderState.shadowPieces.clear();
            }
        }
    }

}
