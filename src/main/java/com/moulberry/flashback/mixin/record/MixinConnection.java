package com.moulberry.flashback.mixin.record;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinConnection {

    @Inject(method = "genericsFtw", at = @At("HEAD"))
    private static void genericsFtw(Packet<?> packet, PacketListener packetListener, CallbackInfo ci) {
        Recorder recorder = Flashback.RECORDER;
        if (recorder != null) {
            if (packetListener instanceof ClientGamePacketListener) {
                recorder.writePacketAsync(packet, ConnectionProtocol.PLAY);
            } else if (packetListener instanceof ClientConfigurationPacketListener) {
                recorder.writePacketAsync(packet, ConnectionProtocol.CONFIGURATION);
            }
        }
    }

}
