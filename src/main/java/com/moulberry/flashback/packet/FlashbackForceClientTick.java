package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class FlashbackForceClientTick implements CustomPacketPayload {
    public static final Type<FlashbackForceClientTick> TYPE = new Type<>(Flashback.createResourceLocation("force_client_tick"));
    public static final FlashbackForceClientTick INSTANCE = new FlashbackForceClientTick();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
