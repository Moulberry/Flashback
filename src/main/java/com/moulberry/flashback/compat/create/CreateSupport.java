package com.moulberry.flashback.compat.create;

// Headers
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import com.simibubi.create.AllPackets;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.sync.ContraptionSeatMappingPacket;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Carriage.DimensionalCarriageEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageDataUpdatePacket;
import com.simibubi.create.content.trains.entity.CarriageSyncData;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainPacket;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphSyncPacket;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.SignalEdgeGroupPacket;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.foundation.utility.Couple;
import com.simibubi.create.foundation.utility.Pair;
import io.github.fabricators_of_create.porting_lib.entity.IEntityAdditionalSpawnData;
import io.netty.buffer.Unpooled;
import me.pepperbell.simplenetworking.S2CPacket;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// Support
public class CreateSupport {

    // Compatibility Check
    public static boolean isCreateLoaded() {
        return FabricLoader.getInstance().isModLoaded("create");
    }

    // Public API Delegators
    public static void backupClientRailways() {
        if (isCreateLoaded()) {
            Impl.backupClientRailways();
        }
    }

    public static void restoreClientRailways() {
        if (isCreateLoaded()) {
            Impl.restoreClientRailways();
        }
    }

    public static void onCustomPayload(ClientboundCustomPayloadPacket packet) {
        if (isCreateLoaded()) {
            Impl.onCustomPayload(packet);
        }
    }

    public static void sendRailwayDataToViewer(ServerPlayer player) {
        if (isCreateLoaded()) {
            Impl.sendRailwayDataToViewer(player);
        }
    }

    public static void resendRailwayDataToAll(ReplayServer server) {
        if (isCreateLoaded()) {
            Impl.resendRailwayDataToAll(server);
        }
    }

    public static void resendCarriagesToPlayer(ServerPlayer player) {
        if (isCreateLoaded()) {
            Impl.resendCarriagesToPlayer(player);
        }
    }

    public static void captureRailwaySnapshot(Consumer<Packet<? super ClientGamePacketListener>> consumer) {
        if (isCreateLoaded()) {
            Impl.captureRailwaySnapshot(consumer);
        }
    }

    public static void appendExtraSpawnData(Entity entity, List<Packet<? super ClientGamePacketListener>> gamePackets) {
        if (isCreateLoaded()) {
            Impl.appendExtraSpawnData(entity, gamePackets);
        }
    }

    public static void captureCarriageDataResend(Consumer<Packet<? super ClientGamePacketListener>> consumer) {
        if (isCreateLoaded()) {
            Impl.captureCarriageDataResend(consumer);
        }
    }

    public static void beforeRenderEntity(Entity entity) {
        if (isCreateLoaded()) {
            Impl.beforeRenderEntity(entity);
        }
    }

    public static void onTickContraptionHead(CarriageContraptionEntity entity, CallbackInfo ci) {
        if (isCreateLoaded()) {
            Impl.onTickContraptionHead(entity, ci);
        }
    }

    public static void onTickContraptionTail(CarriageContraptionEntity entity) {
        if (isCreateLoaded()) {
            Impl.onTickContraptionTail(entity);
        }
    }

    public static void onBindCarriageTail(CarriageContraptionEntity entity) {
        if (isCreateLoaded()) {
            Impl.onBindCarriageTail(entity);
        }
    }

    public static void onCarriageDataUpdateTail(CarriageContraptionEntity entity, CarriageSyncData newData) {
        if (isCreateLoaded()) {
            Impl.onCarriageDataUpdateTail(entity, newData);
        }
    }

    // Tick Storage Hook
    public static void onTickStorage(AbstractContraptionEntity entity, CallbackInfo ci) {
        if (isCreateLoaded()) {
            Impl.onTickStorage(entity, ci);
        }
    }

    // Implementation
    private static class Impl {

        // Reflection Fields
        private static Field graphNetIdField;
        private static Field nodesField;
        private static Field connectionsField;
        private static Field fullWipeField;
        private static Field addedNodesField;
        private static Field addedEdgesField;
        private static Field addedEdgePointsField;

        // Cache Collections
        private static final Map<UUID, TrackGraph> cachedTrackNetworks = new ConcurrentHashMap<>();
        private static final Map<UUID, Train> cachedTrains = new ConcurrentHashMap<>();
        private static final Map<UUID, SignalEdgeGroup> cachedSignalEdgeGroups = new ConcurrentHashMap<>();
        private static final List<Packet<? super ClientGamePacketListener>> recordedCreatePackets = Collections.synchronizedList(new ArrayList<>());

        // Static Initializer
        static {
            try {
                graphNetIdField = TrackGraph.class.getDeclaredField("netId");
                graphNetIdField.setAccessible(true);
                nodesField = TrackGraph.class.getDeclaredField("nodes");
                nodesField.setAccessible(true);
                connectionsField = TrackGraph.class.getDeclaredField("connectionsByNode");
                connectionsField.setAccessible(true);
                fullWipeField = TrackGraphSyncPacket.class.getDeclaredField("fullWipe");
                fullWipeField.setAccessible(true);
                addedNodesField = TrackGraphSyncPacket.class.getDeclaredField("addedNodes");
                addedNodesField.setAccessible(true);
                addedEdgesField = TrackGraphSyncPacket.class.getDeclaredField("addedEdges");
                addedEdgesField.setAccessible(true);
                addedEdgePointsField = TrackGraphSyncPacket.class.getDeclaredField("addedEdgePoints");
                addedEdgePointsField.setAccessible(true);
            } catch (Throwable t) {
                Flashback.LOGGER.warn("Failed to initialize Create reflection fields", t);
            }
        }

        // Railway Backup
        static void backupClientRailways() {
            try {
                if (CreateClient.RAILWAYS != null) {
                    if (CreateClient.RAILWAYS.trackNetworks != null && !CreateClient.RAILWAYS.trackNetworks.isEmpty()) {
                        cachedTrackNetworks.putAll(CreateClient.RAILWAYS.trackNetworks);
                    }
                    if (CreateClient.RAILWAYS.trains != null && !CreateClient.RAILWAYS.trains.isEmpty()) {
                        cachedTrains.putAll(CreateClient.RAILWAYS.trains);
                    }
                    if (CreateClient.RAILWAYS.signalEdgeGroups != null && !CreateClient.RAILWAYS.signalEdgeGroups.isEmpty()) {
                        cachedSignalEdgeGroups.putAll(CreateClient.RAILWAYS.signalEdgeGroups);
                    }
                }
            } catch (Throwable t) {
                Flashback.LOGGER.warn("Failed to backup Create client railways", t);
            }
        }

        // Railway Restore
        static void restoreClientRailways() {
            try {
                if (CreateClient.RAILWAYS != null) {
                    if (CreateClient.RAILWAYS.trackNetworks != null && CreateClient.RAILWAYS.trackNetworks.isEmpty() && !cachedTrackNetworks.isEmpty()) {
                        CreateClient.RAILWAYS.trackNetworks.putAll(cachedTrackNetworks);
                    }
                    if (CreateClient.RAILWAYS.trains != null && CreateClient.RAILWAYS.trains.isEmpty() && !cachedTrains.isEmpty()) {
                        CreateClient.RAILWAYS.trains.putAll(cachedTrains);
                    }
                    if (CreateClient.RAILWAYS.signalEdgeGroups != null && CreateClient.RAILWAYS.signalEdgeGroups.isEmpty() && !cachedSignalEdgeGroups.isEmpty()) {
                        CreateClient.RAILWAYS.signalEdgeGroups.putAll(cachedSignalEdgeGroups);
                    }
                }
            } catch (Throwable t) {
                Flashback.LOGGER.warn("Failed to restore Create client railways", t);
            }
        }

        // Custom Payload Tracking
        static void onCustomPayload(ClientboundCustomPayloadPacket packet) {
            if (packet != null && packet.getIdentifier() != null && packet.getIdentifier().getNamespace().equals("create")) {
                recordedCreatePackets.add(packet);
            }
        }

        // Replay Player Sync
        static void sendRailwayDataToViewer(ServerPlayer player) {
            try {
                restoreClientRailways();
                captureRailwaySnapshot(player.connection::send);
                synchronized (recordedCreatePackets) {
                    for (Packet<? super ClientGamePacketListener> packet : recordedCreatePackets) {
                        player.connection.send(packet);
                    }
                }
                resendCarriagesToPlayer(player);
            } catch (Throwable t) {
                Flashback.LOGGER.warn("Failed to send Create railway data to replay viewer", t);
            }
        }

        // Replay Broadcast
        static void resendRailwayDataToAll(ReplayServer server) {
            try {
                restoreClientRailways();
                for (ReplayPlayer viewer : server.getReplayViewers()) {
                    captureRailwaySnapshot(viewer.connection::send);
                    synchronized (recordedCreatePackets) {
                        for (Packet<? super ClientGamePacketListener> packet : recordedCreatePackets) {
                            viewer.connection.send(packet);
                        }
                    }
                    resendCarriagesToPlayer(viewer);
                }
            } catch (Throwable t) {
                Flashback.LOGGER.warn("Failed to resend Create railway data to all viewers", t);
            }
        }

        // Carriage Resend To Single Player
        static void resendCarriagesToPlayer(ServerPlayer player) {
            var level = Minecraft.getInstance().level;
            if (level == null) return;

            for (Entity entity : level.entitiesForRendering()) {
                if (entity instanceof CarriageContraptionEntity carriage) {
                    try {
                        Packet<?> packet = AllPackets.getChannel().createVanillaPacket((S2CPacket) new CarriageDataUpdatePacket(carriage));
                        player.connection.send((Packet<? super ClientGamePacketListener>) (Object) packet);

                        if (carriage.getContraption() != null && carriage.getContraption().getSeatMapping() != null && !carriage.getContraption().getSeatMapping().isEmpty()) {
                            ContraptionSeatMappingPacket seatPacket = new ContraptionSeatMappingPacket(carriage.getId(), carriage.getContraption().getSeatMapping());
                            Packet<?> sPacket = AllPackets.getChannel().createVanillaPacket((S2CPacket) seatPacket);
                            player.connection.send((Packet<? super ClientGamePacketListener>) (Object) sPacket);
                        }
                    } catch (Throwable t) {
                        Flashback.LOGGER.warn("Failed to send carriage state to player", t);
                    }
                }
            }
        }

        // Carriage Alignment Setup
        static void setupCarriageForRender(CarriageContraptionEntity cce) {
            try {
                restoreClientRailways();

                Carriage carriage = cce.getCarriage();
                if (carriage == null && cce.trainId != null) {
                    Train train = CreateClient.RAILWAYS != null && CreateClient.RAILWAYS.trains != null
                        ? CreateClient.RAILWAYS.trains.get(cce.trainId) : null;
                    if (train == null && !cachedTrains.isEmpty()) {
                        train = cachedTrains.get(cce.trainId);
                        if (train != null && CreateClient.RAILWAYS != null) {
                            CreateClient.RAILWAYS.trains.put(train.id, train);
                        }
                    }
                    if (train != null && train.carriages.size() > cce.carriageIndex) {
                        if (train.graph == null) {
                            Map<UUID, TrackGraph> networks = CreateClient.RAILWAYS != null && !CreateClient.RAILWAYS.trackNetworks.isEmpty()
                                ? CreateClient.RAILWAYS.trackNetworks : cachedTrackNetworks;
                            if (networks != null && !networks.isEmpty()) {
                                train.graph = networks.values().iterator().next();
                                train.derailed = false;
                            }
                        }
                        carriage = train.carriages.get(cce.carriageIndex);
                        cce.setCarriage(carriage);
                    }
                }

                if (carriage != null) {
                    if (carriage.train.graph == null) {
                        Map<UUID, TrackGraph> networks = CreateClient.RAILWAYS != null && !CreateClient.RAILWAYS.trackNetworks.isEmpty()
                            ? CreateClient.RAILWAYS.trackNetworks : cachedTrackNetworks;
                        if (networks != null && !networks.isEmpty()) {
                            carriage.train.graph = networks.values().iterator().next();
                            carriage.train.derailed = false;
                        }
                    }

                    DimensionalCarriageEntity dce = carriage.getDimensional(cce.level());
                    if (dce != null) {
                        dce.entity = new WeakReference<>(cce);
                        dce.pivot = null;
                        dce.pointsInitialised = true;
                        if (cce.carriageData != null) {
                            cce.carriageData.apply(cce, carriage);
                        }
                        if (!carriage.train.derailed) {
                            carriage.updateContraptionAnchors();
                        }
                        dce.updateRenderedCutoff();
                        dce.alignEntity(cce);
                    }

                    cce.validForRender = true;
                    cce.firstPositionUpdate = false;

                    try {
                        if (carriage.storage != null) {
                            try {
                                Method initMethod = carriage.storage.getClass().getMethod("initialize");
                                initMethod.invoke(carriage.storage);
                            } catch (Throwable ignored) {}
                        }
                        if (cce.getContraption() != null) {
                            try {
                                Method getStorageMethod = cce.getContraption().getClass().getMethod("getStorage");
                                Object storage = getStorageMethod.invoke(cce.getContraption());
                                if (storage != null) {
                                    Method initMethod = storage.getClass().getMethod("initialize");
                                    initMethod.invoke(storage);
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                Flashback.LOGGER.warn("Failed to setup carriage for render", t);
            }
        }

        // Tick Storage Hook
        static void onTickStorage(AbstractContraptionEntity entity, CallbackInfo ci) {
            try {
                if (entity == null || entity.level().isClientSide() || Flashback.isInReplay()) {
                    ci.cancel();
                }
            } catch (Throwable ignored) {}
        }

        // Entity Pre-Render Hook
        static void beforeRenderEntity(Entity entity) {
            if (entity instanceof CarriageContraptionEntity cce) {
                setupCarriageForRender(cce);
            }
        }

        // Contraption Tick Head Hook
        static void onTickContraptionHead(CarriageContraptionEntity entity, CallbackInfo ci) {
            try {
                if (!entity.level().isClientSide()) {
                    if (Flashback.isInReplay()) {
                        ci.cancel();
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Contraption Tick Tail Hook
        static void onTickContraptionTail(CarriageContraptionEntity entity) {
            try {
                if (entity.level().isClientSide()) {
                    setupCarriageForRender(entity);
                }
            } catch (Throwable ignored) {}
        }

        // Bind Carriage Tail Hook
        static void onBindCarriageTail(CarriageContraptionEntity entity) {
            setupCarriageForRender(entity);
        }

        // Carriage Data Update Tail Hook
        static void onCarriageDataUpdateTail(CarriageContraptionEntity entity, CarriageSyncData newData) {
            setupCarriageForRender(entity);
        }

        // Railway Capture
        @SuppressWarnings({"unchecked", "rawtypes"})
        static void captureRailwaySnapshot(Consumer<Packet<? super ClientGamePacketListener>> consumer) {
            try {
                captureRailwaySnapshotInternal(consumer);
            } catch (Throwable t) {
                Flashback.LOGGER.warn("Failed to capture Create railway state in snapshot", t);
            }
        }

        // Internal Railway Snapshot
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void captureRailwaySnapshotInternal(Consumer<Packet<? super ClientGamePacketListener>> consumer) throws Exception {
            backupClientRailways();

            Map<UUID, TrackGraph> networks = CreateClient.RAILWAYS != null && !CreateClient.RAILWAYS.trackNetworks.isEmpty()
                ? CreateClient.RAILWAYS.trackNetworks : cachedTrackNetworks;

            if (networks != null && fullWipeField != null) {
                for (TrackGraph graph : networks.values()) {
                    if (graph == null) continue;
                    int netId = graphNetIdField != null ? graphNetIdField.getInt(graph) : 0;
                    TrackGraphSyncPacket packet = new TrackGraphSyncPacket(graph.id, netId);
                    fullWipeField.setBoolean(packet, true);

                    Map<Integer, Object> addedNodes = (Map<Integer, Object>) addedNodesField.get(packet);
                    Collection<Object> addedEdges = (Collection<Object>) addedEdgesField.get(packet);
                    Collection<Object> addedEdgePoints = (Collection<Object>) addedEdgePointsField.get(packet);

                    Map<?, TrackNode> nodes = (Map<?, TrackNode>) nodesField.get(graph);
                    if (nodes != null) {
                        for (TrackNode node : nodes.values()) {
                            addedNodes.put(node.getNetId(), Pair.of(node.getLocation(), node.getNormal()));
                        }
                    }

                    Map<TrackNode, Map<TrackNode, TrackEdge>> connections =
                        (Map<TrackNode, Map<TrackNode, TrackEdge>>) connectionsField.get(graph);
                    Set<TrackEdgePoint> sentPoints = new HashSet<>();
                    if (connections != null) {
                        for (Map.Entry<TrackNode, Map<TrackNode, TrackEdge>> e : connections.entrySet()) {
                            TrackNode n1 = e.getKey();
                            for (Map.Entry<TrackNode, TrackEdge> e2 : e.getValue().entrySet()) {
                                TrackNode n2 = e2.getKey();
                                TrackEdge edge = e2.getValue();
                                Couple<Integer> key = Couple.create(n1.getNetId(), n2.getNetId());
                                addedEdges.add(Pair.of(Pair.of(key, edge.getTrackMaterial()), edge.getTurn()));

                                if (edge.getEdgeData() != null && edge.getEdgeData().getPoints() != null) {
                                    for (TrackEdgePoint point : edge.getEdgeData().getPoints()) {
                                        if (sentPoints.add(point)) {
                                            addedEdgePoints.add(point);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    for (EdgePointType<?> type : EdgePointType.TYPES.values()) {
                        for (TrackEdgePoint point : graph.getPoints(type)) {
                            if (sentPoints.add(point)) {
                                addedEdgePoints.add(point);
                            }
                        }
                    }

                    Packet<?> vanillaPacket = AllPackets.getChannel().createVanillaPacket((S2CPacket) packet);
                    consumer.accept((Packet<? super ClientGamePacketListener>) (Object) vanillaPacket);
                }
            }

            Map<UUID, SignalEdgeGroup> signalGroups = CreateClient.RAILWAYS != null && !CreateClient.RAILWAYS.signalEdgeGroups.isEmpty()
                ? CreateClient.RAILWAYS.signalEdgeGroups : cachedSignalEdgeGroups;

            if (signalGroups != null && !signalGroups.isEmpty()) {
                ArrayList<SignalEdgeGroup> asList = new ArrayList<>(signalGroups.values());
                SignalEdgeGroupPacket groupPacket = new SignalEdgeGroupPacket(
                    asList.stream().map(g -> g.id).toList(),
                    asList.stream().map(g -> g.color).toList(),
                    true
                );
                consumer.accept((Packet<? super ClientGamePacketListener>) (Object) AllPackets.getChannel().createVanillaPacket((S2CPacket) groupPacket));
            }

            Map<UUID, Train> trains = CreateClient.RAILWAYS != null && !CreateClient.RAILWAYS.trains.isEmpty()
                ? CreateClient.RAILWAYS.trains : cachedTrains;

            if (trains != null) {
                for (Train train : trains.values()) {
                    if (train == null) continue;
                    try {
                        TrainPacket trainPacket = new TrainPacket(train, true);
                        Packet<?> p = AllPackets.getChannel().createVanillaPacket((S2CPacket) trainPacket);
                        consumer.accept((Packet<? super ClientGamePacketListener>) (Object) p);
                    } catch (Throwable t) {
                        try {
                            Class<?> addTrainPacketClass = Class.forName("com.simibubi.create.content.trains.entity.AddTrainPacket");
                            Constructor<?> ctor = addTrainPacketClass.getConstructor(Train.class);
                            Object addPacket = ctor.newInstance(train);
                            Method createVanilla = AllPackets.getChannel().getClass().getMethod("createVanillaPacket", Object.class);
                            Packet<?> p = (Packet<?>) createVanilla.invoke(AllPackets.getChannel(), addPacket);
                            consumer.accept((Packet<? super ClientGamePacketListener>) (Object) p);
                        } catch (Throwable ignored) {}
                    }
                }
            }

            captureCarriageDataResend(consumer);
        }

        // Complex Spawn Data
        static void appendExtraSpawnData(Entity entity, List<Packet<? super ClientGamePacketListener>> gamePackets) {
            if (entity instanceof IEntityAdditionalSpawnData extra) {
                try {
                    FriendlyByteBuf extraBuf = new FriendlyByteBuf(Unpooled.buffer());
                    extraBuf.writeVarInt(entity.getId());
                    extra.writeSpawnData(extraBuf);
                    gamePackets.add(new ClientboundCustomPayloadPacket(IEntityAdditionalSpawnData.EXTRA_DATA_PACKET, extraBuf));
                } catch (Throwable t) {
                    Flashback.LOGGER.warn("Failed to capture extra spawn data for entity {}", entity.getId(), t);
                }
            }
        }

        // Carriage Resend
        @SuppressWarnings({"unchecked", "rawtypes"})
        static void captureCarriageDataResend(Consumer<Packet<? super ClientGamePacketListener>> consumer) {
            var level = Minecraft.getInstance().level;
            if (level == null) return;

            for (Entity entity : level.entitiesForRendering()) {
                if (entity instanceof CarriageContraptionEntity carriage) {
                    try {
                        Packet<?> packet = AllPackets.getChannel().createVanillaPacket((S2CPacket) new CarriageDataUpdatePacket(carriage));
                        consumer.accept((Packet<? super ClientGamePacketListener>) (Object) packet);

                        if (carriage.getContraption() != null && carriage.getContraption().getSeatMapping() != null && !carriage.getContraption().getSeatMapping().isEmpty()) {
                            ContraptionSeatMappingPacket seatPacket = new ContraptionSeatMappingPacket(carriage.getId(), carriage.getContraption().getSeatMapping());
                            Packet<?> sPacket = AllPackets.getChannel().createVanillaPacket((S2CPacket) seatPacket);
                            consumer.accept((Packet<? super ClientGamePacketListener>) (Object) sPacket);
                        }
                    } catch (Throwable t) {
                        Flashback.LOGGER.warn("Failed to capture carriage data for entity {}", entity.getId(), t);
                    }
                }
            }
        }

    }

}
