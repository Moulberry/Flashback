package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.world.item.ItemStack;

public record FlashbackRawCustomPayload(byte[] packetBytes, boolean configPhase) implements CustomPacketPayload {
    public static final Type<FlashbackRawCustomPayload> TYPE = new Type<>(Flashback.createResourceLocation("raw_custom_payload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRawCustomPayload> STREAM_CODEC = new ProcessPacketRawStreamCodec();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class ProcessPacketRawStreamCodec implements StreamCodec<RegistryFriendlyByteBuf, FlashbackRawCustomPayload> {
        @Override
        public FlashbackRawCustomPayload decode(RegistryFriendlyByteBuf friendlyByteBuf) {
            byte[] packetBytes = friendlyByteBuf.readByteArray();
            boolean configPhase = friendlyByteBuf.readBoolean();
            return new FlashbackRawCustomPayload(packetBytes, configPhase);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf friendlyByteBuf, FlashbackRawCustomPayload packet) {
            friendlyByteBuf.writeByteArray(packet.packetBytes());
            friendlyByteBuf.writeBoolean(packet.configPhase());
        }
    }

}
