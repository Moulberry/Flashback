package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class FlashbackClearEntities implements CustomPacketPayload {
    public static final Type<FlashbackClearEntities> TYPE = new Type<>(Flashback.createResourceLocation("clear_entities"));
    public static final FlashbackClearEntities INSTANCE = new FlashbackClearEntities();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
