package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.packet.FinishedServerTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URL;
import java.util.UUID;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class MixinClientCommonPacketListenerImpl {

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    @Nullable
    private static URL parseResourcePackUrl(String string) {
        return null;
    }

    /**
     * Removes the resource pack prompt screen in replays
     */
    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    public void handleResourcePackPush(ClientboundResourcePackPushPacket clientboundResourcePackPushPacket, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            PacketUtils.ensureRunningOnSameThread(clientboundResourcePackPushPacket, (ClientCommonPacketListenerImpl)(Object)this, this.minecraft);

            UUID uuid = clientboundResourcePackPushPacket.id();
            URL uRL = parseResourcePackUrl(clientboundResourcePackPushPacket.url());
            if (uRL != null) {
                String string = clientboundResourcePackPushPacket.hash();
                this.minecraft.getDownloadedPackSource().allowServerPacks();
                this.minecraft.getDownloadedPackSource().pushPack(uuid, uRL, string);
            }

            ci.cancel();
        }
    }

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V", at = @At("HEAD"), cancellable = true)
    public void handleCustomPayload(ClientboundCustomPayloadPacket clientboundCustomPayloadPacket, CallbackInfo ci) {
        if (clientboundCustomPayloadPacket.payload() == FinishedServerTick.INSTANCE) {
            if (Flashback.EXPORT_JOB != null) {
                Flashback.EXPORT_JOB.onFinishedServerTick();
            }
            ci.cancel();
        }
    }

}
