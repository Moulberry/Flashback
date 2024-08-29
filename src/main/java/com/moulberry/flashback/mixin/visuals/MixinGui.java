package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.editor.ui.CustomImGuiImplGlfw;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
public class MixinGui {

    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
    public void renderChat(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.showChat) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true)
    public void renderTitle(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.showTitleText) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    public void renderScoreboardSidebar(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.showScoreboard) {
            ci.cancel();
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    public void renderOverlayMessage(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.showActionBar) {
            ci.cancel();
        }
    }

    @Inject(method = "canRenderCrosshairForSpectator", at = @At("HEAD"), cancellable = true)
    public void canRenderCrosshairForSpectator(HitResult hitResult, CallbackInfoReturnable<Boolean> cir) {
        if (Flashback.isExporting()) {
            cir.setReturnValue(false);
            return;
        }
        if (ReplayUI.isActive() && ReplayUI.imguiGlfw.getMouseHandledBy() == CustomImGuiImplGlfw.MouseHandledBy.GAME) {
            cir.setReturnValue(true);
            return;
        }
        if (Flashback.getSpectatingPlayer() != null) {
            cir.setReturnValue(true);
        }
    }

}
