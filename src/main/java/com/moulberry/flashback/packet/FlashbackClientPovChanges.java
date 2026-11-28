package com.moulberry.flashback.packet;

import com.google.common.collect.MapMaker;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.PositionAndAngle;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.SkipPacketDecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GameProtocols;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FlashbackClientPovChanges(UUID playerUuid, List<Packet<? super ClientGamePacketListener>> packets) implements CustomPacketPayload {
    public static final Type<FlashbackClientPovChanges> TYPE = new Type<>(Flashback.createIdentifier("client_pov_changes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackClientPovChanges> STREAM_CODEC = new FlashbackClientPovChangesStreamCodec();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static final Map<RegistryAccess, StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>>> packetCodecMap = new MapMaker().weakKeys().makeMap();

    private static StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> getPacketCodecForRegistryAccess(RegistryAccess registryAccess) {
        var packetCodec = packetCodecMap.get(registryAccess);
        if (packetCodec != null) {
            return packetCodec;
        }
        packetCodec = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registryAccess)).codec();
        packetCodecMap.put(registryAccess, packetCodec);
        return packetCodec;
    }

    public static class FlashbackClientPovChangesStreamCodec implements StreamCodec<RegistryFriendlyByteBuf, FlashbackClientPovChanges> {
        @Override
        public FlashbackClientPovChanges decode(RegistryFriendlyByteBuf friendlyByteBuf) {
            var gamePacketCodec = getPacketCodecForRegistryAccess(friendlyByteBuf.registryAccess());

            UUID playerUuid = friendlyByteBuf.readUUID();
            int packetCount = friendlyByteBuf.readVarInt();

            List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();

            for (int i = 0; i < packetCount; i++) {
                int size = friendlyByteBuf.readVarInt();
                var slice = new RegistryFriendlyByteBuf(friendlyByteBuf.readSlice(size), friendlyByteBuf.registryAccess());
                try {
                    packets.add(gamePacketCodec.decode(slice));
                } catch (SkipPacketDecoderException ignored) {}
            }

            return new FlashbackClientPovChanges(playerUuid, packets);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf friendlyByteBuf, FlashbackClientPovChanges povChanges) {
            var gamePacketCodec = getPacketCodecForRegistryAccess(friendlyByteBuf.registryAccess());

            friendlyByteBuf.writeUUID(povChanges.playerUuid);
            friendlyByteBuf.writeVarInt(povChanges.packets.size());

            var tempByteBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), friendlyByteBuf.registryAccess());
            for (Packet<? super ClientGamePacketListener> packet : povChanges.packets) {
                tempByteBuf.clear();
                gamePacketCodec.encode(tempByteBuf, packet);

                friendlyByteBuf.writeVarInt(tempByteBuf.readableBytes());
                friendlyByteBuf.writeBytes(tempByteBuf);
            }
        }
    }

}
