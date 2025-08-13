package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 1100)
public class MixinClientPacketListener {

    /*
     * When in a replay, sometimes the replay will have recorded a packet which opens a screen on the client
     * This doesn't happen in vanilla, but it can happen with mods (e.g. Traveler's Backpack)
     * This code will essentially prevent a custom payload from opening a screen while in a replay
     */

    @Unique
    private Screen screenBeforeHandleCustomPayload = null;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    public void handleCustomPayloadHead(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            this.screenBeforeHandleCustomPayload = Minecraft.getInstance().screen;
        }
    }

    @Inject(method = "handleCustomPayload", at = @At("RETURN"))
    public void handleCustomPayloadReturn(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            Minecraft.getInstance().setScreen(this.screenBeforeHandleCustomPayload);
        }
    }

}
