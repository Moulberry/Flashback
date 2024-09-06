package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

public record FlashbackRemoteSetSlot(int entityId, int slot, ItemStack itemStack) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteSetSlot> TYPE = new Type<>(Flashback.createResourceLocation("remote_set_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteSetSlot> STREAM_CODEC = new RemoteSetSlotStreamCodec();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class RemoteSetSlotStreamCodec implements StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteSetSlot> {
        @Override
        public FlashbackRemoteSetSlot decode(RegistryFriendlyByteBuf friendlyByteBuf) {
            int entityId = friendlyByteBuf.readVarInt();
            int slot = friendlyByteBuf.readByte();
            ItemStack itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(friendlyByteBuf);
            return new FlashbackRemoteSetSlot(entityId, slot, itemStack);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf friendlyByteBuf, FlashbackRemoteSetSlot remoteHotbarSlot) {
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.entityId);
            friendlyByteBuf.writeByte(remoteHotbarSlot.slot);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(friendlyByteBuf, remoteHotbarSlot.itemStack);
        }
    }

}
