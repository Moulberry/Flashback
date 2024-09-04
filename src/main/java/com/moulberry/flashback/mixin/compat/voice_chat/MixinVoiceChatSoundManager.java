package com.moulberry.flashback.mixin.compat.voice_chat;

import com.moulberry.flashback.Flashback;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import de.maxhenkel.voicechat.voice.client.SoundManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfModLoaded("voicechat")
@Pseudo
@Mixin(value = SoundManager.class, remap = false)
public class MixinVoiceChatSoundManager {

    @Inject(method = "openContext", at = @At("HEAD"), cancellable = true)
    public void openContext(CallbackInfoReturnable<Boolean> cir) {
        if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().recordAudio()) {
            long context = Minecraft.getInstance().getSoundManager().soundEngine.library.context;
            cir.setReturnValue(EXTThreadLocalContext.alcSetThreadContext(context));
        }
    }

}
