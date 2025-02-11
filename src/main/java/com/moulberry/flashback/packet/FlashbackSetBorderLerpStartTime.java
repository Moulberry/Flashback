
package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FlashbackSetBorderLerpStartTime(long time) implements CustomPacketPayload {
    public static final Type<FlashbackSetBorderLerpStartTime> TYPE = new Type<>(Flashback.createResourceLocation("set_border_lerp_start_time"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackSetBorderLerpStartTime> STREAM_CODEC = new SetBorderLerpStartTimeCodec();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class SetBorderLerpStartTimeCodec implements StreamCodec<RegistryFriendlyByteBuf, FlashbackSetBorderLerpStartTime> {
        @Override
        public FlashbackSetBorderLerpStartTime decode(RegistryFriendlyByteBuf friendlyByteBuf) {
            long millis = friendlyByteBuf.readLong();
            return new FlashbackSetBorderLerpStartTime(millis);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf friendlyByteBuf, FlashbackSetBorderLerpStartTime setBorderLerpStartTime) {
            friendlyByteBuf.writeLong(setBorderLerpStartTime.time);
        }
    }

}
