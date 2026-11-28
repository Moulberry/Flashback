package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.povstate.PovScreenType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.SkipPacketDecoderException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record FlashbackSetPovMouseOffset(float mouseX, float mouseY) implements CustomPacketPayload {
    public static final Type<FlashbackSetPovMouseOffset> TYPE = new Type<>(Flashback.createIdentifier("set_pov_mouse_offset"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackSetPovMouseOffset> STREAM_CODEC = new SetPovMouseOffsetStreamCodec();


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class SetPovMouseOffsetStreamCodec implements StreamCodec<RegistryFriendlyByteBuf, FlashbackSetPovMouseOffset> {
        @Override
        public FlashbackSetPovMouseOffset decode(RegistryFriendlyByteBuf friendlyByteBuf) {
            return new FlashbackSetPovMouseOffset(
                friendlyByteBuf.readFloat(),
                friendlyByteBuf.readFloat()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf friendlyByteBuf, FlashbackSetPovMouseOffset setPovMouseOffset) {
            friendlyByteBuf.writeFloat(setPovMouseOffset.mouseX());
            friendlyByteBuf.writeFloat(setPovMouseOffset.mouseY());
        }
    }

}
