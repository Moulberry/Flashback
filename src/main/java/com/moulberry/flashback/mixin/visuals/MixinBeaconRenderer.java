package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeaconRenderer.class)
public class MixinBeaconRenderer {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    public void shouldRender(CallbackInfoReturnable<Boolean> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderBeaconBeams) {
            cir.setReturnValue(false);
        }
    }

}
