package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isInvisible()Z"))
    public boolean isInvisible(Entity instance, Operation<Boolean> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.hideDuringExport.contains(instance.getUUID())) {
            return true;
        }
        return original.call(instance);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z"))
    public boolean shouldShowName(EntityRenderer<?, ?> instance, Entity entity, double distance, Operation<Boolean> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.replayVisuals.renderNametags) {
                return false;
            } else if (editorState.hideNametags.contains(entity.getUUID())) {
                return false;
            } else if (editorState.hideDuringExport.contains(entity.getUUID())) {
                return false;
            }
        }
        return original.call(instance, entity, distance);
    }

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    public void shouldShowName(Entity entity, double d, CallbackInfoReturnable<Boolean> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.replayVisuals.renderNametags) {
                cir.setReturnValue(false);
            } else if (editorState.hideNametags.contains(entity.getUUID())) {
                cir.setReturnValue(false);
            } else if (editorState.hideDuringExport.contains(entity.getUUID())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    public void renderNameTag(EntityRenderState entityRenderState, Component component, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderNametags) {
            ci.cancel();
        }
    }

}
