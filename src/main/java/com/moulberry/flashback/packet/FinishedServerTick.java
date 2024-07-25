package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class FinishedServerTick implements CustomPacketPayload {
    public static final Type<FinishedServerTick> TYPE = new Type<>(Flashback.createResourceLocation("finished_server_tick"));
    public static final FinishedServerTick INSTANCE = new FinishedServerTick();

    private FinishedServerTick() {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
