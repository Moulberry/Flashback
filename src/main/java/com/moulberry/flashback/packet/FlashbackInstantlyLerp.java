package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class FlashbackInstantlyLerp implements CustomPacketPayload {
    public static final Type<FlashbackInstantlyLerp> TYPE = new Type<>(Flashback.createResourceLocation("instantly_lerp"));
    public static final FlashbackInstantlyLerp INSTANCE = new FlashbackInstantlyLerp();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
