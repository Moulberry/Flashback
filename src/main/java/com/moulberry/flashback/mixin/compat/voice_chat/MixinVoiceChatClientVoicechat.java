package com.moulberry.flashback.mixin.compat.voice_chat;

import com.moulberry.flashback.Flashback;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import de.maxhenkel.voicechat.voice.client.ClientVoicechat;
import de.maxhenkel.voicechat.voice.client.ClientVoicechatConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@IfModLoaded("voicechat")
@Pseudo
@Mixin(value = ClientVoicechat.class, remap = false)
public class MixinVoiceChatClientVoicechat {

    @Inject(method = "startMicThread", at = @At("HEAD"), cancellable = true)
    public void startMicThread(ClientVoicechatConnection connection, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

}
