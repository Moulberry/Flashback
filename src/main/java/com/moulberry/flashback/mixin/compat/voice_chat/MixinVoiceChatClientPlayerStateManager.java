package com.moulberry.flashback.mixin.compat.voice_chat;

import com.moulberry.flashback.Flashback;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfModLoaded("voicechat")
@Pseudo
@Mixin(value = ClientPlayerStateManager.class, remap = false)
public class MixinVoiceChatClientPlayerStateManager {

    @Inject(method = "isPlayerDisconnected", at = @At("HEAD"), cancellable = true)
    public void isPlayerDisconnected(CallbackInfoReturnable<Boolean> cir) {
        if (Flashback.isInReplay()) {
            cir.setReturnValue(false);
        }
    }

}
