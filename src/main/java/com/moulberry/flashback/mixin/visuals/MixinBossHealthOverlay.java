package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class MixinBossHealthOverlay {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.showBossBar) {
            ci.cancel();
        }
    }

}
