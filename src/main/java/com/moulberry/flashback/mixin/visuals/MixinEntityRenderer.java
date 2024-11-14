package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

}
