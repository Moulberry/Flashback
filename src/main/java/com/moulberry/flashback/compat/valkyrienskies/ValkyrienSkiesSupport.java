package com.moulberry.flashback.compat.valkyrienskies;

// Headers
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.action.ActionRegistry;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.io.ReplayWriter;
import io.netty.util.collection.LongObjectMap;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.impl.game.ships.ShipObject;
import org.valkyrienskies.core.impl.hooks.CoreHooksImplKt;
import org.valkyrienskies.core.impl.networking.impl.PacketShipDataCreate;
import org.valkyrienskies.core.impl.networking.simple.SimplePackets;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

// Support
public class ValkyrienSkiesSupport {

    public static void register() {
        ActionRegistry.register(ActionShipDataDeltaUpdate.INSTANCE);
        ActionRegistry.register(ActionShipDataCreate.INSTANCE);
        ActionRegistry.register(ActionShipRemove.INSTANCE);
    }

    public static void setCurrentServer(MinecraftServer server) {
        ValkyrienSkiesMod.setCurrentServer(server);
    }

    public static void clearDataForPlayingSnapshot(MinecraftServer server) {
        var world = ((IShipObjectWorldServerProvider) server).getShipObjectWorld();
        for (var ship : world.getAllShips().stream().toList()) {
            world.deleteShip(ship);
        }
    }

    public static void recordShipData(AsyncReplaySaver asyncReplaySaver) {
        var world = CoreHooksImplKt.getCoreHooks().getCurrentShipClientWorld();
        var shipData = world.getLoadedShips().stream().map(ship -> ((ShipObject) ship).getShipData()).toList();
        var simplePacket = new PacketShipDataCreate(shipData);

        asyncReplaySaver.submit(writer -> {
            writer.startAction(ActionShipDataCreate.INSTANCE);
            writer.friendlyByteBuf().writeBytes(SimplePackets.serialize(simplePacket));
            writer.finishAction(ActionShipDataCreate.INSTANCE);
        });
    }

    @SuppressWarnings("unchecked")
    public static void addShipChunks(ClientChunkCache clientChunkCache, Level level, List<ClientboundLevelChunkWithLightPacket> levelChunkPackets) {
        var shipChunks = (LongObjectMap<LevelChunk>) (Object) ((ClientChunkCacheDuck) clientChunkCache).vs$getShipChunks();
        for (LevelChunk chunk : shipChunks.values()) {
            levelChunkPackets.add(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
        }
    }

    @SuppressWarnings("unchecked")
    public static void addShipChunkTasks(ClientChunkCache clientChunkCache, Level level, ForkJoinPool pool, Consumer<LevelChunk> chunkConsumer) {
        var shipChunks = (LongObjectMap<LevelChunk>) (Object) ((ClientChunkCacheDuck) clientChunkCache).vs$getShipChunks();
        for (LevelChunk chunk : shipChunks.values()) {
            chunkConsumer.accept(chunk);
        }
    }

    public static boolean tryHandleCustomPayload(ClientboundCustomPayloadPacket cp, ReplayWriter writer) {
        if (cp.getIdentifier().equals(new ResourceLocation("valkyrienskies", "vs_packet"))) {
            var data = cp.getData();
            var action = getAction(data);
            if (action != null) {
                writer.startAction(action);
                writer.friendlyByteBuf().writeBytes(data);
                writer.finishAction(action);
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static Action getAction(FriendlyByteBuf data) {
        Action action = switch (data.getInt(data.readerIndex())) {
            case 0 -> ActionShipDataDeltaUpdate.INSTANCE;
            case 3 -> ActionShipDataCreate.INSTANCE;
            case 4 -> ActionShipRemove.INSTANCE;
            default -> null;
        };
        if (action != null) {
            data.readInt();
        }
        return action;
    }

}
