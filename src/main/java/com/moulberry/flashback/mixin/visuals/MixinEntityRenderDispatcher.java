package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityRenderDispatcher.class, priority = 990)
public abstract class MixinEntityRenderDispatcher {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    public void shouldRender(Entity entity, Frustum frustum, double d, double e, double f, CallbackInfoReturnable<Boolean> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.filteredEntities.isEmpty()) {
                String id = entity.getType().builtInRegistryHolder().key().identifier().toString();
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

            // Prevent rendering shadows when blocks are turned off
            if (!editorState.replayVisuals.renderBlocks) {
                renderState.shadowRadius = 0f;
                renderState.shadowPieces.clear();
            }
        }
    }

}
