package com.moulberry.flashback.mixin.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    public void renderNameTag(Entity entity, Component component, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, float f, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderNametags) {
            ci.cancel();
        }
    }

}
