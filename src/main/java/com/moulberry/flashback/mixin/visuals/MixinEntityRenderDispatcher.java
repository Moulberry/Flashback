package com.moulberry.flashback.mixin.visuals;

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
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityRenderDispatcher.class, priority = 990)
public abstract class MixinEntityRenderDispatcher {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    public void shouldRender(Entity entity, Frustum frustum, double d, double e, double f, CallbackInfoReturnable<Boolean> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.filteredEntities.isEmpty()) {
            String id = entity.getType().builtInRegistryHolder().key().location().toString();
            if (editorState.filteredEntities.contains(id)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V", at = @At("HEAD"), cancellable = true)
    public void renderBefore(Entity entity, double d, double e, double f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, EntityRenderer entityRenderer, CallbackInfo ci) {
        if (Flashback.isExporting()) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && editorState.hideDuringExport.contains(entity.getUUID())) {
                ci.cancel();
            }
        }
    }

    // Add a yellow outline to selected entity
    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V", at = @At(value = "RETURN"), require = 0)
    public void renderAfter(Entity entity, double d, double e, double f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, EntityRenderer entityRenderer, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            if (ReplayUI.isEntitySelected(entity.getUUID())) {
                poseStack.pushPose();
                poseStack.translate(d, e, f);
                AABB aabb = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());
                ShapeRenderer.renderLineBox(poseStack, multiBufferSource.getBuffer(RenderType.lines()), aabb, 1.0f, 1.0f, 0.0f, 1.0F);
                poseStack.popPose();
            } else if (!Flashback.isExporting()) {
                EditorState editorState = EditorStateManager.getCurrent();
                if (editorState != null && entity.getUUID().equals(editorState.audioSourceEntity)) {
                    poseStack.pushPose();
                    poseStack.translate(d, e, f);
                    AABB aabb = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());
                    ShapeRenderer.renderLineBox(poseStack, multiBufferSource.getBuffer(RenderType.lines()), aabb, 0.0f, 1.0f, 1.0f, 1.0F);
                    poseStack.popPose();
                }
            }
        }
    }

    // Prevent rendering shadows when blocks are turned off
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true, require = 0)
    private static void renderShadow(CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderBlocks) {
            ci.cancel();
        }
    }

}
