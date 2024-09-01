package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FlashbackRemoteFoodData(int entityId, int foodLevel, float saturationLevel) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteFoodData> TYPE = new Type<>(Flashback.createResourceLocation("remote_food_data"));

    public static final StreamCodec<FriendlyByteBuf, FlashbackRemoteFoodData> STREAM_CODEC = new FlashbackRemoteFoodDataStreamCodec();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class FlashbackRemoteFoodDataStreamCodec implements StreamCodec<FriendlyByteBuf, FlashbackRemoteFoodData> {
        @Override
        public FlashbackRemoteFoodData decode(FriendlyByteBuf friendlyByteBuf) {
            int entityId = friendlyByteBuf.readVarInt();
            int foodLevel = friendlyByteBuf.readVarInt();
            float saturationLevel = friendlyByteBuf.readFloat();
            return new FlashbackRemoteFoodData(entityId, foodLevel, saturationLevel);
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackRemoteFoodData remoteHotbarSlot) {
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.entityId);
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.foodLevel);
            friendlyByteBuf.writeFloat(remoteHotbarSlot.saturationLevel);
        }
    }

}
