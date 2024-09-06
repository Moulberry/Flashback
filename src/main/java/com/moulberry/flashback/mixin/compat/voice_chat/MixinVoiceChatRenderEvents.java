package com.moulberry.flashback.mixin.compat.voice_chat;

import com.moulberry.flashback.Flashback;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import de.maxhenkel.voicechat.voice.client.RenderEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfModLoaded("voicechat")
@Pseudo
@Mixin(value = RenderEvents.class, remap = false)
public class MixinVoiceChatRenderEvents {

    @Inject(method = "shouldShowIcons", at = @At("HEAD"), cancellable = true)
    public void shouldShowIcons(CallbackInfoReturnable<Boolean> cir) {
        if (Flashback.isInReplay()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderIcon", at = @At("HEAD"), cancellable = true)
    public void renderIcon(GuiGraphics guiGraphics, ResourceLocation texture, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

}
