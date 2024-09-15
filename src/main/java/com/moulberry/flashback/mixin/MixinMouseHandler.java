package com.moulberry.flashback.mixin;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.CustomImGuiImplGlfw;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @Inject(method = "isMouseGrabbed", at=@At("HEAD"), cancellable = true)
    public void isMouseGrabbed(CallbackInfoReturnable<Boolean> cir) {
        if (ReplayUI.isActive()) {
            cir.setReturnValue(ReplayUI.imguiGlfw.getMouseHandledBy() == CustomImGuiImplGlfw.MouseHandledBy.GAME);
        } else if (Flashback.isExporting()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = {"onPress", "onScroll", "onMove"}, at = @At("HEAD"), cancellable = true)
    public void onUseMouse(CallbackInfo ci) {
        if (Flashback.isExporting()) {
            ci.cancel();
        }
    }

    @Inject(method = "grabMouse", at=@At("HEAD"), cancellable = true)
    public void grabMouse(CallbackInfo ci) {
        if (ReplayUI.isActive() || Flashback.isExporting()) {
            ci.cancel();
        }
    }

    @Inject(method = "releaseMouse", at=@At("HEAD"), cancellable = true)
    public void releaseMouse(CallbackInfo ci) {
        if (ReplayUI.isActive()) {
            ci.cancel();
        }
    }

}
