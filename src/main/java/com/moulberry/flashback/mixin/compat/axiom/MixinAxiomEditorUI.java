package com.moulberry.flashback.mixin.compat.axiom;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfModLoaded("axiom")
@Pseudo
@Mixin(targets = "com.moulberry.axiom.editor.EditorUI", remap = false)
public class MixinAxiomEditorUI {

    @Shadow
    private static boolean enabled;

    @Inject(method = "isActiveInternal", at = @At("HEAD"), cancellable = true)
    private static void isActiveInternal(CallbackInfoReturnable<Boolean> cir) {
        if (ReplayUI.isActive() || Flashback.isInReplay()) {
            enabled = false;
            cir.setReturnValue(false);
        }
    }

}
