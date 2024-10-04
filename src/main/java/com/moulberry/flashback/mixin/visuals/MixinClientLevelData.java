package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.ClientLevelData.class)
public class MixinClientLevelData {

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true, require = 0)
    public void getDayTime(CallbackInfoReturnable<Long> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideTimeOfDay >= 0) {
            cir.setReturnValue(editorState.replayVisuals.overrideTimeOfDay);
        }
    }

}
