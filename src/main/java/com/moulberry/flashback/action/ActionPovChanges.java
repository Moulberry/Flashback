package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.io.ReplayWriter;
import com.moulberry.flashback.playback.ReplayServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.SkipPacketDecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ActionPovChanges implements Action {

    private static final Identifier NAME = Flashback.createIdentifier("action/pov_changes_optional");
    public static final ActionPovChanges INSTANCE = new ActionPovChanges();
    private ActionPovChanges() {
    }

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf friendlyByteBuf) {
        UUID playerUuid = friendlyByteBuf.readUUID();
        int packetCount = friendlyByteBuf.readVarInt();

        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();

        for (int i = 0; i < packetCount; i++) {
            int size = friendlyByteBuf.readVarInt();
            var slice = new RegistryFriendlyByteBuf(friendlyByteBuf.readSlice(size), replayServer.registryAccess());

            try {
                packets.add(replayServer.getGamePacketCodec().decode(slice));
            } catch (SkipPacketDecoderException ignored) {}
        }

        replayServer.handlePovChanges(playerUuid, packets);
    }

    public void write(
        StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> gamePacketCodec,
        ReplayWriter writer,
        UUID playerUuid,
        List<Packet<? super ClientGamePacketListener>> packets
    ) {
        writer.startAction(this);
        RegistryFriendlyByteBuf friendlyByteBuf = writer.friendlyByteBuf();
        friendlyByteBuf.writeUUID(playerUuid);
        friendlyByteBuf.writeVarInt(packets.size());

        var tempByteBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), writer.registryAccess());
        for (Packet<? super ClientGamePacketListener> packet : packets) {
            tempByteBuf.clear();
            gamePacketCodec.encode(tempByteBuf, packet);

            friendlyByteBuf.writeVarInt(tempByteBuf.readableBytes());
            friendlyByteBuf.writeBytes(tempByteBuf);
        }

        writer.finishAction(this);

    }

}
