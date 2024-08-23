package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class FlashbackClearParticles implements CustomPacketPayload {
    public static final Type<FlashbackClearParticles> TYPE = new Type<>(Flashback.createResourceLocation("clear_particles"));
    public static final FlashbackClearParticles INSTANCE = new FlashbackClearParticles();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
