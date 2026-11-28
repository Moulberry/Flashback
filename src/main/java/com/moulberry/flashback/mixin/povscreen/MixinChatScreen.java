package com.moulberry.flashback.mixin.povscreen;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.moulberry.flashback.ext.ScreenExt;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class MixinChatScreen {

    @WrapWithCondition(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;updateCommandInfo()V"))
    public boolean init_updateCommandInfo(CommandSuggestions instance) {
        return !((ScreenExt) this).flashback$isFake();
    }

    @Inject(method = "onEdited", at = @At("HEAD"), cancellable = true)
    public void onEdited(String value, CallbackInfo ci) {
        if (((ScreenExt) this).flashback$isFake()) {
            ci.cancel();
        }
    }

}
