package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FlashbackRemoteSelectHotbarSlot(int entityId, int slot) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteSelectHotbarSlot> TYPE = new Type<>(Flashback.createResourceLocation("remote_select_hotbar_slot"));

    public static final StreamCodec<FriendlyByteBuf, FlashbackRemoteSelectHotbarSlot> STREAM_CODEC = new RemoteSelectHotbarSlotStreamCodec();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class RemoteSelectHotbarSlotStreamCodec implements StreamCodec<FriendlyByteBuf, FlashbackRemoteSelectHotbarSlot> {
        @Override
        public FlashbackRemoteSelectHotbarSlot decode(FriendlyByteBuf friendlyByteBuf) {
            int entityId = friendlyByteBuf.readVarInt();
            int slot = friendlyByteBuf.readByte();
            return new FlashbackRemoteSelectHotbarSlot(entityId, slot);
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackRemoteSelectHotbarSlot remoteHotbarSlot) {
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.entityId);
            friendlyByteBuf.writeByte(remoteHotbarSlot.slot);
        }
    }

}
