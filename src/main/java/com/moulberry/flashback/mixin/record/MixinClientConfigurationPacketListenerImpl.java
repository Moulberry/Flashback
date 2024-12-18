package com.moulberry.flashback.mixin.record;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class MixinClientConfigurationPacketListenerImpl extends ClientCommonPacketListenerImpl  {

    protected MixinClientConfigurationPacketListenerImpl(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
    }

    @Inject(method = "handleConfigurationFinished", at = @At("RETURN"))
    public void handleConfigurationFinished(ClientboundFinishConfigurationPacket clientboundFinishConfigurationPacket, CallbackInfo ci) {
        if (Flashback.RECORDER != null) {
            Flashback.RECORDER.setRegistryAccess(((ClientPacketListener)this.connection.getPacketListener()).registryAccess());
        }
    }

}
