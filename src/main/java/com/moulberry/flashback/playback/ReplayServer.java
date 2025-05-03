package com.moulberry.flashback.playback;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.PacketHelper;
import com.moulberry.flashback.SneakyThrow;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.ext.ConnectionExt;
import com.moulberry.flashback.ext.LevelChunkExt;
import com.moulberry.flashback.TempFolderProvider;
import com.moulberry.flashback.ext.ServerTickRateManagerExt;
import com.moulberry.flashback.keyframe.handler.ReplayServerKeyframeHandler;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import com.moulberry.flashback.packet.FlashbackClearEntities;
import com.moulberry.flashback.packet.FlashbackClearParticles;
import com.moulberry.flashback.packet.FlashbackForceClientTick;
import com.moulberry.flashback.packet.FlashbackInstantlyLerp;
import com.moulberry.flashback.packet.FlashbackRemoteExperience;
import com.moulberry.flashback.packet.FlashbackRemoteFoodData;
import com.moulberry.flashback.packet.FlashbackRemoteSelectHotbarSlot;
import com.moulberry.flashback.packet.FlashbackRemoteSetSlot;
import com.moulberry.flashback.packet.FlashbackSetBorderLerpStartTime;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.ext.ServerGamePacketListenerImplExt;
import com.moulberry.flashback.io.ReplayReader;
import com.moulberry.flashback.packet.FinishedServerTick;
import com.moulberry.flashback.record.FlashbackChunkMeta;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.record.Recorder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public class ReplayServer extends IntegratedServer {

    private static final Gson GSON = new Gson();
    public static int REPLAY_VIEWER_IDS_START = -981723987;
    public static String REPLAY_VIEWER_NAME = "Replay Viewer";
    public static final int CHUNK_CACHE_SIZE = 10000;

    public volatile int jumpToTick = -1;
    public volatile boolean replayPaused = true;
    public AtomicBoolean forceApplyKeyframes = new AtomicBoolean(false);
    public AtomicBoolean sendFinishedServerTick = new AtomicBoolean(false);
    private volatile float desiredTickRate = 20.0f;
    private volatile float desiredTickRateManual = 20.0f;
    private boolean desiredFrozen = false;
    private int desiredFrozenDelay = -1;
    private boolean isFrozen = false;
    private int frozenDelay = -1;

    private int currentTick = 0;
    private volatile int targetTick = 0;
    private final int totalTicks;
    public ResourceKey<Level> spawnLevel = null;
    private final ReplayGamePacketHandler gamePacketHandler;
    private final ReplayConfigurationPacketHandler configurationPacketHandler;
    private StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> gamePacketCodec;
    private final StreamCodec<ByteBuf, Packet<? super ClientConfigurationPacketListener>> configurationPacketCodec;
    private final List<ReplayPlayer> replayViewers = new ArrayList<>();
    public boolean followLocalPlayerNextTickIfWrongDimension = false;
    public boolean isProcessingSnapshot = false;
    private boolean processedSnapshot = false;
    public volatile boolean fastForwarding = false;

    private int printFailedDecodePacketCount = 8;

    private final UUID playbackUUID;
    private final FlashbackMeta metadata;
    private final TreeMap<Integer, PlayableChunk> playableChunksByStart = new TreeMap<>();
    private final Int2ObjectMap<ClientboundLevelChunkWithLightPacket> levelChunkCachedPackets = new Int2ObjectOpenHashMap<>();
    private final IntSet loadedChunkCacheFiles = new IntOpenHashSet();
    private ReplayReader currentReplayReader = null;

    private record RemotePack(UUID id, String url, String hash){}
    private final Map<UUID, RemotePack> oldRemotePacks = new HashMap<>();
    private final Map<UUID, RemotePack> remotePacks = new HashMap<>();
    private final Map<UUID, BossEvent> bossEvents = new HashMap<>();
    private Component tabListHeader = Component.empty();
    private Component tabListFooter = Component.empty();
    private final Map<ResourceKey<Level>, IntSet> needsPositionUpdate = new HashMap<>();

    private Component shutdownReason = null;
    private FileSystem playbackFileSystem = null;
    private boolean initializedWithSnapshot = false;

    public ReplayServer(Thread thread, Minecraft minecraft, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, Services services,
                        ChunkProgressListenerFactory chunkProgressListenerFactory, UUID playbackUUID, Path path) {
        super(thread, minecraft, levelStorageAccess, packRepository, worldStem, services, chunkProgressListenerFactory);
        this.playbackUUID = playbackUUID;
        this.gamePacketHandler = new ReplayGamePacketHandler(this);
        this.configurationPacketHandler = new ReplayConfigurationPacketHandler(this);

        this.gamePacketCodec = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.registryAccess())).codec();
        this.configurationPacketCodec = ConfigurationProtocols.CLIENTBOUND.codec();

        try {
            this.playbackFileSystem = FileSystems.newFileSystem(path);

            Path metadataPath = this.playbackFileSystem.getPath("/metadata.json");
            String metadataJson = Files.readString(metadataPath);
            this.metadata = FlashbackMeta.fromJson(GSON.fromJson(metadataJson, JsonObject.class));
            if (this.metadata == null) {
                throw new RuntimeException("Invalid metadata file");
            }

            int ticks = 0;
            for (Map.Entry<String, FlashbackChunkMeta> entry : this.metadata.chunks.entrySet()) {
                var chunkMetaWithPath = new PlayableChunk(entry.getValue(), this.playbackFileSystem.getPath("/"+entry.getKey()));
                this.playableChunksByStart.put(ticks, chunkMetaWithPath);
                ticks += entry.getValue().duration;
            }

            this.totalTicks = ticks;

            Path levelChunkCachePath = this.playbackFileSystem.getPath("/level_chunk_cache");
            if (Files.exists(levelChunkCachePath)) {
                loadLevelChunkCache(levelChunkCachePath, 0, "/level_chunk_cache");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadLevelChunkCache(Path levelChunkCachePath, int chunkCacheIndex, String name) throws IOException {
        int startIndex = chunkCacheIndex;

        try (InputStream is = Files.newInputStream(levelChunkCachePath)) {
            while (true) {
                byte[] sizeBuffer = is.readNBytes(4);
                if (sizeBuffer.length < 4) {
                    break;
                }

                int size = (sizeBuffer[0] & 0xff) << 24 |
                    (sizeBuffer[1] & 0xff) << 16 |
                    (sizeBuffer[2] & 0xff) <<  8 |
                    sizeBuffer[3] & 0xff;

                byte[] chunk = is.readNBytes(size);
                if (chunk.length < size) {
                    Flashback.LOGGER.error("Ran out of bytes while reading level_chunk_cache, needed {}, had {}",
                        size, chunk.length);
                    break;
                }

                RegistryFriendlyByteBuf registryFriendlyByteBuf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(chunk), this.registryAccess());

                try {
                    Packet<?> packet = this.gamePacketCodec.decode(registryFriendlyByteBuf);
                    if (packet instanceof ClientboundLevelChunkWithLightPacket levelChunkWithLightPacket) {
                        this.levelChunkCachedPackets.put(chunkCacheIndex, levelChunkWithLightPacket);
                    } else {
                        throw new IllegalStateException("Level chunk cache contains wrong packet: " + packet);
                    }
                } catch (Exception e) {
                    Flashback.LOGGER.error("Encountered error while reading level_chunk_cache", e);
                }

                chunkCacheIndex += 1;
            }
        }

        Flashback.LOGGER.info("Loaded {} with {} entries", name, chunkCacheIndex - startIndex);
    }

    public FlashbackMeta getMetadata() {
        return this.metadata;
    }

    public void updateRegistry(FeatureFlagSet featureFlagSet, Collection<String> selectedPacks, List<Packet<? super ClientConfigurationPacketListener>> initialPackets,
            List<ConfigurationTask> configurationTasks) {
        this.worldData.setDataConfiguration(new WorldDataConfiguration(
            this.worldData.getDataConfiguration().dataPacks(),
            featureFlagSet
        ));
        this.reloadResources(selectedPacks);

        this.gamePacketCodec = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.registryAccess())).codec();

        if (this.currentReplayReader != null) {
            this.currentReplayReader.changeRegistryAccess(this.registryAccess());
        }

        List<ServerPlayer> players = new ArrayList<>(this.getPlayerList().getPlayers());
        for (ServerPlayer player : players) {
            if (player instanceof ReplayPlayer) {
                ((ServerGamePacketListenerImplExt)player.connection).flashback$switchToConfigWithTasks(initialPackets, configurationTasks);
            }
        }
        this.replayViewers.clear();
    }

    @Override
    public boolean initServer() {
        Entity.ENTITY_COUNTER.set(1000000);

        AtomicInteger newPlayerIds = new AtomicInteger(REPLAY_VIEWER_IDS_START);
        this.setPlayerList(new PlayerList(this, this.registries(), this.playerDataStorage, 1) {
            @Override
            public ServerPlayer getPlayerForLogin(GameProfile gameProfile, ClientInformation clientInformation) {
                ServerLevel level = ReplayServer.this.overworld();
                if (spawnLevel != null) {
                    ServerLevel serverLevel = ReplayServer.this.getLevel(spawnLevel);
                    if (serverLevel != null) {
                        level = serverLevel;
                    }
                }

                ReplayPlayer player = new ReplayPlayer(ReplayServer.this, level, gameProfile, clientInformation);
                player.setId(newPlayerIds.getAndDecrement());
                player.followLocalPlayerNextTick = true;
                return player;
            }

            @Override
            public void placeNewPlayer(Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie) {
                if (Flashback.getConfig().filterUnnecessaryPackets) {
                    ((ConnectionExt)connection).flashback$setFilterUnnecessaryPackets();
                }
                super.placeNewPlayer(connection, serverPlayer, commonListenerCookie);
            }

            @Override
            public boolean canBypassPlayerLimit(GameProfile gameProfile) {
                return true;
            }

            @Override
            public void broadcastSystemMessage(Component component, Function<ServerPlayer, Component> function, boolean bl) {
            }

            @Override
            public void sendPlayerPermissionLevel(ServerPlayer serverPlayer) {
                if (serverPlayer instanceof FakePlayer) {
                    return;
                }
                super.sendPlayerPermissionLevel(serverPlayer);
            }

            @Override
            public void sendAllPlayerInfo(ServerPlayer serverPlayer) {
                if (serverPlayer instanceof FakePlayer) {
                    return;
                }
                super.sendAllPlayerInfo(serverPlayer);
            }

            @Override
            public void broadcastAll(Packet<?> packet) {
                if (packet instanceof ClientboundSetBorderLerpSizePacket) {
                    long time = ReplayServer.this.currentTick * 50L;
                    for (ReplayPlayer replayViewer : replayViewers) {
                        ServerPlayNetworking.send(replayViewer, new FlashbackSetBorderLerpStartTime(time));
                    }
                }

                super.broadcastAll(packet);
            }

            @Override
            public void broadcast(@Nullable Player player, double x, double y, double z, double distance, ResourceKey<Level> resourceKey, Packet<?> packet) {
                UUID audioSourceEntity = null;

                EditorState editorState = ReplayServer.this.getEditorState();
                if (editorState != null) {
                    audioSourceEntity = editorState.audioSourceEntity;
                }

                for (ReplayPlayer replayViewer : ReplayServer.this.replayViewers) {
                    if (replayViewer != player && replayViewer.level().dimension() == resourceKey) {
                        Vec3 source = replayViewer.position();

                        if (audioSourceEntity != null) {
                            Entity entity = replayViewer.serverLevel().getEntity(audioSourceEntity);
                            if (entity != null) {
                                source = entity.position();
                            }
                        }

                        double dx = x - source.x;
                        double dy = y - source.y;
                        double dz = z - source.z;
                        if (dx*dx + dy*dy + dz*dz < distance*distance) {
                            replayViewer.connection.send(packet);
                        }
                    }
                }
            }

            @Override
            public void sendLevelInfo(ServerPlayer serverPlayer, ServerLevel serverLevel) {
                if (serverPlayer instanceof FakePlayer) {
                    return;
                }

                super.sendLevelInfo(serverPlayer, serverLevel);

                // Send all resource packs
                serverPlayer.connection.send(new ClientboundResourcePackPopPacket(Optional.empty()));
                for (RemotePack remotePack : remotePacks.values()) {
                    serverPlayer.connection.send(new ClientboundResourcePackPushPacket(remotePack.id,
                        remotePack.url, remotePack.hash, true, Optional.empty()));
                }

                // Send tab list customization
                serverPlayer.connection.send(new ClientboundTabListPacket(tabListHeader, tabListFooter));

                // Send world border
                serverPlayer.connection.send(new ClientboundInitializeBorderPacket(serverLevel.getWorldBorder()));
            }

            @Override
            protected void save(ServerPlayer serverPlayer) {
            }

            @Override
            public void saveAll() {
            }

            @Override
            public void removeAll() {
                if (shutdownReason == null) {
                    super.removeAll();
                } else {
                    List<ServerPlayer> players = this.getPlayers();
                    for (ServerPlayer player : players) {
                        player.connection.disconnect(shutdownReason);
                    }
                }
            }

            @Override
            public ServerStatsCounter getPlayerStats(Player player) {
                File statsDir = this.getServer().getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
                File statsFile = new File(statsDir, player.getUUID() + ".json");
                return new ServerStatsCounter(this.getServer(), statsFile);
            }
        });

        super.initServer();

        return true;
    }

    public void pushRemotePack(UUID uuid, String url, String hash) {
        this.remotePacks.put(uuid, new RemotePack(uuid, url, hash));
    }

    public void popRemotePack(UUID uuid) {
        this.remotePacks.remove(uuid);
    }

    public void popAllRemotePacks() {
        this.remotePacks.clear();
    }

    public void setTabListCustomization(Component header, Component footer) {
        this.tabListHeader = header;
        this.tabListFooter = footer;
        this.getPlayerList().broadcastAll(new ClientboundTabListPacket(header, footer));
    }

    public void goToReplayTick(int tick) {
        this.jumpToTick = tick;
    }

    public float getDesiredTickRate(boolean manual) {
        if (manual) {
            return this.desiredTickRateManual;
        } else {
            return this.desiredTickRate;
        }
    }

    public void setDesiredTickRate(float tickrate, boolean manual) {
        if (manual) {
            this.desiredTickRateManual = tickrate;
        } else {
            this.desiredTickRate = tickrate;
        }
    }

    public void setFrozen(boolean frozen, int delay) {
        this.desiredFrozen = frozen;
        this.desiredFrozenDelay = delay;
    }

    public int getReplayTick() {
        return this.targetTick;
    }

    private int lastReplayTick;
    private long lastTickTimeNanos;

    public float getPartialReplayTick() {
        if (this.replayPaused || this.isPaused()) {
            return this.targetTick;
        } else {
            long currentNanos = Util.getNanos();
            long nanosPerTick = this.tickRateManager().nanosecondsPerTick();

            double partial = (currentNanos - this.lastTickTimeNanos) / (double) nanosPerTick;
            partial = Math.max(0, Math.min(1, partial));

            return this.lastReplayTick + (float) partial;
        }
    }

    public int getTotalReplayTicks() {
        return this.totalTicks;
    }

    public boolean doClientRendering() {
        return !this.isProcessingSnapshot && !this.processedSnapshot && !this.fastForwarding;
    }

    public void updateBossBar(ClientboundBossEventPacket packet) {
        packet.dispatch(new ClientboundBossEventPacket.Handler() {
            @Override
            public void add(UUID uuid, Component component, float progress, BossEvent.BossBarColor bossBarColor, BossEvent.BossBarOverlay bossBarOverlay, boolean darkenScreen, boolean playBossMusic, boolean createWorldFog) {
                BossEvent old = bossEvents.remove(uuid);
                BossEvent newEvent = new BossEvent(uuid, component, bossBarColor, bossBarOverlay) {};
                newEvent.setProgress(progress);
                newEvent.setDarkenScreen(darkenScreen);
                newEvent.setPlayBossMusic(playBossMusic);
                newEvent.setCreateWorldFog(createWorldFog);
                if (old != null) {
                    if (old.getProgress() != progress) {
                        getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateProgressPacket(newEvent));
                    }
                    if (!old.getName().equals(component)) {
                        getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateNamePacket(newEvent));
                    }
                    if (!old.getColor().equals(bossBarColor) || !old.getOverlay().equals(bossBarOverlay)) {
                        getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateStylePacket(newEvent));
                    }
                    if (old.shouldDarkenScreen() != darkenScreen || old.shouldPlayBossMusic() != playBossMusic || old.shouldCreateWorldFog() != createWorldFog) {
                        getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdatePropertiesPacket(newEvent));
                    }
                } else {
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createAddPacket(newEvent));
                }
                bossEvents.put(uuid, newEvent);
            }

            @Override
            public void remove(UUID uuid) {
                if (bossEvents.remove(uuid) != null) {
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createRemovePacket(uuid));
                }
            }

            @Override
            public void updateProgress(UUID uuid, float progress) {
                BossEvent current = bossEvents.get(uuid);
                if (current != null) {
                    current.setProgress(progress);
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateProgressPacket(current));
                }
            }

            @Override
            public void updateName(UUID uuid, Component component) {
                BossEvent current = bossEvents.get(uuid);
                if (current != null) {
                    current.setName(component);
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateNamePacket(current));
                }
            }

            @Override
            public void updateStyle(UUID uuid, BossEvent.BossBarColor bossBarColor, BossEvent.BossBarOverlay bossBarOverlay) {
                BossEvent current = bossEvents.get(uuid);
                if (current != null) {
                    current.setColor(bossBarColor);
                    current.setOverlay(bossBarOverlay);
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateStylePacket(current));
                }
            }

            @Override
            public void updateProperties(UUID uuid, boolean darkenScreen, boolean playBossMusic, boolean createWorldFog) {
                BossEvent current = bossEvents.get(uuid);
                if (current != null) {
                    current.setDarkenScreen(darkenScreen);
                    current.setPlayBossMusic(playBossMusic);
                    current.setCreateWorldFog(createWorldFog);
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdatePropertiesPacket(current));
                }
            }
        });
    }

    public Collection<ReplayPlayer> getReplayViewers() {
        return this.replayViewers;
    }

    public int getLocalPlayerId() {
        return this.gamePacketHandler.localPlayerId;
    }

    public void handleNextTick() {
        if (this.isProcessingSnapshot) {
            throw new IllegalStateException("Can't go to next tick while processing snapshot");
        }

        this.gamePacketHandler.flushPendingEntities();
        currentTick += 1;
    }

    public void handleConfigurationPacket(RegistryFriendlyByteBuf friendlyByteBuf) {
        Packet<? super ClientConfigurationPacketListener> packet = this.configurationPacketCodec.decode(friendlyByteBuf);
        this.gamePacketHandler.flushPendingEntities();
        packet.handle(this.configurationPacketHandler);
    }

    public void handleGamePacket(RegistryFriendlyByteBuf friendlyByteBuf) {
        this.configurationPacketHandler.flushPendingConfiguration();

        Packet<? super ClientGamePacketListener> packet;
        try {
            packet = this.gamePacketCodec.decode(friendlyByteBuf);
        } catch (DecoderException decoderException) {
            // Failed to decode packet, lets try ignoring it
            if (printFailedDecodePacketCount > 0) {
                Flashback.LOGGER.error("Failed to decode packet from replay stream", decoderException);
                printFailedDecodePacketCount -= 1;
            }

            this.gamePacketHandler.flushPendingEntities();
            friendlyByteBuf.readerIndex(friendlyByteBuf.writerIndex());
            return;
        }
        if (!AllowPendingEntityPacketSet.allowPendingEntity(packet)) {
            this.gamePacketHandler.flushPendingEntities();
        }
        packet.handle(this.gamePacketHandler);
    }

    public void handleCreateLocalPlayer(RegistryFriendlyByteBuf friendlyByteBuf) {
        this.configurationPacketHandler.flushPendingConfiguration();
        this.gamePacketHandler.flushPendingEntities();
        this.gamePacketHandler.handleCreateLocalPlayer(friendlyByteBuf);
    }

    public void handleAccuratePlayerPosition(RegistryFriendlyByteBuf friendlyByteBuf) {
        FlashbackConfig config = Flashback.getConfig();
        if (config.disableIncreasedFirstPersonUpdates) {
            friendlyByteBuf.readerIndex(friendlyByteBuf.writerIndex());
            return;
        }

        var packet = FlashbackAccurateEntityPosition.STREAM_CODEC.decode(friendlyByteBuf);

        for (ReplayPlayer replayViewer : this.replayViewers) {
            ServerPlayNetworking.send(replayViewer, packet);
        }
    }

    public void handleMoveEntities(RegistryFriendlyByteBuf friendlyByteBuf) {
        this.gamePacketHandler.flushPendingEntities();
        this.configurationPacketHandler.flushPendingConfiguration();

        int levelCount = friendlyByteBuf.readVarInt();
        for (int i = 0; i < levelCount; i++) {
            ResourceKey<Level> dimension = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
            ServerLevel level = this.levels.get(dimension);

            IntSet positionUpdateSet = null;
            if (level != null) {
                positionUpdateSet = this.needsPositionUpdate.computeIfAbsent(dimension, k -> new IntOpenHashSet());
            }

            int count = friendlyByteBuf.readVarInt();
            for (int j = 0; j < count; j++) {
                int id = friendlyByteBuf.readVarInt();
                double x = friendlyByteBuf.readDouble();
                double y = friendlyByteBuf.readDouble();
                double z = friendlyByteBuf.readDouble();
                float yaw = friendlyByteBuf.readFloat();
                float pitch = friendlyByteBuf.readFloat();
                float headYaw = friendlyByteBuf.readFloat();
                boolean onGround = friendlyByteBuf.readBoolean();

                if (level != null) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) {
                        if (entity.isPassenger()) {
                            entity.setYRot(yaw);
                            entity.setXRot(pitch);
                        } else {
                            entity.moveTo(x, y, z, yaw, pitch);
                            updatePositionOfPassengers(entity);
                        }

                        entity.setYHeadRot(headYaw);
                        if (entity.onGround() != onGround) {
                            entity.setOnGround(onGround);
                        }

                        positionUpdateSet.add(id);
                    } else if (!this.isFrozen) {
                        byte yRot = (byte) Mth.floor(yaw * 256.0F / 360.0F);
                        byte xRot = (byte) Mth.floor(pitch * 256.0F / 360.0F);
                        this.getPlayerList().broadcastAll(PacketHelper.createTeleportForUnknown(id, x, y, z, yRot, xRot, onGround));
                    }
                }
            }
        }
    }

    private void updatePositionOfPassengers(Entity vehicle) {
        for (Entity passenger : vehicle.getPassengers()) {
            vehicle.positionRider(passenger);
            updatePositionOfPassengers(passenger);
        }
    }

    public void handleLevelChunkCached(int index) {
        ClientboundLevelChunkWithLightPacket packet = this.levelChunkCachedPackets.get(index);
        if (packet == null) {
            int cacheIndex = index / ReplayServer.CHUNK_CACHE_SIZE;
            if (this.loadedChunkCacheFiles.add(cacheIndex)) {
                String pathString = "/level_chunk_caches/" + cacheIndex;
                Path levelChunkCachePath = this.playbackFileSystem.getPath(pathString);
                if (Files.exists(levelChunkCachePath)) {
                    try {
                        loadLevelChunkCache(levelChunkCachePath, cacheIndex * ReplayServer.CHUNK_CACHE_SIZE, pathString);
                    } catch (IOException e) {
                        SneakyThrow.sneakyThrow(e);
                    }
                }
                packet = this.levelChunkCachedPackets.get(index);
            }
        }

        if (packet != null) {
            this.configurationPacketHandler.flushPendingConfiguration();
            this.gamePacketHandler.flushPendingEntities();

            try {
                int x = packet.getX();
                int z = packet.getZ();
                LevelChunk chunk = this.gamePacketHandler.level().getChunk(x, z);

                if (Flashback.EXPORT_JOB != null || !doesCachedChunkIdMatch(chunk, index) || this.gamePacketHandler.forceSendChunksDueToMovingPistonShenanigans.contains(ChunkPos.asLong(x, z))) {
                    packet.handle(this.gamePacketHandler);

                    if (chunk instanceof LevelChunkExt ext) {
                        ext.flashback$setCachedChunkId(index);
                    }
                }
            } catch (Exception ignored) {
                // Some mods are apparently incapable of returning a chunk when requesting it which causes an error here
                // Why a mod would be designed to do that? Only god knows
            }
        } else {
            Flashback.LOGGER.error("Missing cached level chunk: {}", index);
        }
    }

    private static boolean doesCachedChunkIdMatch(LevelChunk chunk, int chunkId) {
        if (chunk instanceof LevelChunkExt ext) {
            return ext.flashback$getCachedChunkId() == chunkId;
        }
        return false;
    }

    public static final TicketType<ChunkPos> ENTITY_LOAD_TICKET = TicketType.create("replay_entity_load", Comparator.comparingLong(ChunkPos::toLong), 5);

    @Override
    public void loadLevel() {
        super.loadLevel();

        for (ServerLevel level : this.levels.values()) {
            level.noSave = true;
        }
    }

    public void closeLevel(ServerLevel serverLevel) {
        if (serverLevel == null) {
            return;
        }
        this.clearLevel(serverLevel);
        ServerWorldEvents.UNLOAD.invoker().onWorldUnload(this, serverLevel);
        try {
            serverLevel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void clearLevel(ServerLevel serverLevel) {
        if (serverLevel == null) {
            return;
        }
        for (ServerPlayer player : new ArrayList<>(serverLevel.players())) {
            if (player instanceof ReplayPlayer replayPlayer) {
                replayPlayer.lastFirstPersonDataUUID = null;
                continue;
            }
            player.discard();
        }
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof ServerPlayer) {
                continue;
            }
            entities.add(entity);
        }
        for (Entity entity : entities) {
            if (entity != null) {
                entity.discard();
            }
        }
        serverLevel.setDayTime(0);

        for (ServerPlayer player : serverLevel.players()) {
            if (player instanceof ReplayPlayer replayPlayer) {
                ServerPlayNetworking.send(replayPlayer, FlashbackClearEntities.INSTANCE);
            }
        }
    }

    @Override
    public boolean haveTime() {
        return super.haveTime() && this.jumpToTick < 0;
    }

    public EditorState getEditorState() {
        if (Flashback.isExporting()) {
            return Flashback.EXPORT_JOB.getSettings().editorState();
        }
        return EditorStateManager.get(this.metadata.replayIdentifier);
    }

    @Override
    public boolean isReady() {
        return super.isReady() && this.initializedWithSnapshot;
    }

    @Override
    public void tickServer(BooleanSupplier booleanSupplier) {
        if (!this.initializedWithSnapshot) {
            this.initializedWithSnapshot = true;

            // Play initial snapshot
            ReplayReader replayReader = this.playableChunksByStart.get(0).getOrLoadReplayReader(this.registryAccess());
            replayReader.handleSnapshot(this);
        }

        this.lastReplayTick = this.targetTick;
        this.lastTickTimeNanos = this.nextTickTimeNanos - this.tickRateManager().nanosecondsPerTick();

        // Update list of replay viewers
        this.replayViewers.clear();

        for (ServerPlayer player : this.getPlayerList().getPlayers()) {
            if (player instanceof ReplayPlayer replayPlayer) {
                if (replayPlayer.isShiftKeyDown()) {
                    replayPlayer.spectatingUuid = null;
                    replayPlayer.spectatingUuidTickCount = 0;
                    replayPlayer.forceRespectateTickCount = 0;
                } else {
                    Entity cameraEntity = replayPlayer.getCamera();
                    if (cameraEntity != null && cameraEntity != replayPlayer) {
                        replayPlayer.spectatingUuid = cameraEntity.getUUID();
                        replayPlayer.spectatingUuidTickCount = 20;
                    } else if (replayPlayer.spectatingUuidTickCount > 0) {
                        replayPlayer.spectatingUuidTickCount -= 1;
                    } else {
                        replayPlayer.spectatingUuid = null;
                    }
                }
                this.replayViewers.add(replayPlayer);
            }
        }

        // Pause replay if game is paused (by opening the ESC pause menu for example)
        if (!this.replayPaused && this.isPaused()) {
            this.replayPaused = true;
        }

        // Update current tick
        boolean normalPlayback = false;
        if (this.jumpToTick >= 0) {
            this.targetTick = this.jumpToTick;
            this.jumpToTick = -1;
        } else if (!this.replayPaused && this.targetTick < this.totalTicks) {
            // Normal playback
            this.targetTick += 1;
            normalPlayback = true;
        } else if (this.targetTick == this.totalTicks && this.currentTick == this.totalTicks) {
            // Pause when reaching end of replay
            this.replayPaused = true;
        }

        ServerTickRateManager tickRateManager = this.tickRateManager();
        ((ServerTickRateManagerExt)tickRateManager).flashback$setSuppressClientUpdates(true);
        if (Flashback.EXPORT_JOB != null || this.targetTick == this.currentTick || normalPlayback || this.isFrozen) {
            this.runUpdates(booleanSupplier);
        } else {
            int realTargetTick = this.targetTick;

            if (this.targetTick < this.currentTick) {
                int minTick = this.playableChunksByStart.floorKey(this.targetTick) + 1;
                this.targetTick = Math.max(minTick, realTargetTick - 20);
            } else {
                this.targetTick = Math.max(this.currentTick+1, realTargetTick - 20);
            }

            if (this.targetTick >= realTargetTick) {
                this.targetTick = realTargetTick;
                this.runUpdates(booleanSupplier);
            } else {
                while (this.targetTick <= realTargetTick) {
                    this.fastForwarding = this.targetTick < realTargetTick;

                    this.runUpdates(booleanSupplier);

                    if (this.targetTick == realTargetTick) {
                        break;
                    } else {
                        this.targetTick += 1;
                    }
                }
                this.fastForwarding = false;
            }
        }
        ((ServerTickRateManagerExt)tickRateManager).flashback$setSuppressClientUpdates(false);

        if (this.forceApplyKeyframes.compareAndSet(true, false)) {
            ((MinecraftExt)Minecraft.getInstance()).flashback$applyKeyframes();
        }

        this.tryFollowLocalPlayer();

        // Update first person data
        for (ReplayPlayer replayViewer : this.replayViewers) {
            // Ensure replay viewers are still spectating
            if (replayViewer.spectatingUuid != null) {
                Entity camera = replayViewer.getCamera();
                if (replayViewer.forceRespectateTickCount > 0 || camera == null || camera == replayViewer || camera.isRemoved()) {
                    Entity targetEntity = replayViewer.serverLevel().getEntity(replayViewer.spectatingUuid);
                    if (targetEntity != null && !targetEntity.isRemoved()) {
                        replayViewer.setCamera(null);
                        replayViewer.setCamera(targetEntity);
                        replayViewer.spectatingUuid = targetEntity.getUUID();

                        if (replayViewer.forceRespectateTickCount == 0) {
                            replayViewer.forceRespectateTickCount = 5;
                        }
                    }
                }
            }
            if (replayViewer.forceRespectateTickCount > 0) {
                replayViewer.forceRespectateTickCount -= 1;
            }

            Entity camera = replayViewer.getCamera();
            if (camera != replayViewer && camera instanceof Player playerCamera) {
                Inventory inventory = playerCamera.getInventory();
                if (!Objects.equals(replayViewer.lastFirstPersonDataUUID, playerCamera.getUUID())) {
                    replayViewer.lastFirstPersonDataUUID = playerCamera.getUUID();

                    replayViewer.lastFirstPersonExperienceProgress = playerCamera.experienceProgress;
                    replayViewer.lastFirstPersonTotalExperience = playerCamera.totalExperience;
                    replayViewer.lastFirstPersonExperienceLevel = playerCamera.experienceLevel;
                    ServerPlayNetworking.send(replayViewer, new FlashbackRemoteExperience(playerCamera.getId(), playerCamera.experienceProgress,
                        playerCamera.totalExperience, playerCamera.experienceLevel));

                    FoodData foodData = playerCamera.getFoodData();
                    replayViewer.lastFirstPersonFoodLevel = foodData.getFoodLevel();
                    replayViewer.lastFirstPersonSaturationLevel = foodData.getSaturationLevel();
                    ServerPlayNetworking.send(replayViewer, new FlashbackRemoteFoodData(playerCamera.getId(), foodData.getFoodLevel(), foodData.getSaturationLevel()));

                    replayViewer.lastFirstPersonSelectedSlot = inventory.selected;
                    ServerPlayNetworking.send(replayViewer, new FlashbackRemoteSelectHotbarSlot(playerCamera.getId(), inventory.selected));

                    for (int i = 0; i < 9; i++) {
                        ItemStack hotbarItem = inventory.getItem(i);
                        replayViewer.lastFirstPersonHotbarItems[i] = hotbarItem.copy();
                        ServerPlayNetworking.send(replayViewer, new FlashbackRemoteSetSlot(playerCamera.getId(), i, hotbarItem.copy()));
                    }
                } else {
                    if (replayViewer.lastFirstPersonExperienceProgress != playerCamera.experienceProgress ||
                            replayViewer.lastFirstPersonTotalExperience != playerCamera.totalExperience ||
                            replayViewer.lastFirstPersonExperienceLevel != playerCamera.experienceLevel) {
                        replayViewer.lastFirstPersonExperienceProgress = playerCamera.experienceProgress;
                        replayViewer.lastFirstPersonTotalExperience = playerCamera.totalExperience;
                        replayViewer.lastFirstPersonExperienceLevel = playerCamera.experienceLevel;
                        ServerPlayNetworking.send(replayViewer, new FlashbackRemoteExperience(playerCamera.getId(), playerCamera.experienceProgress,
                            playerCamera.totalExperience, playerCamera.experienceLevel));
                    }

                    FoodData foodData = playerCamera.getFoodData();
                    if (replayViewer.lastFirstPersonFoodLevel != foodData.getFoodLevel() || replayViewer.lastFirstPersonSaturationLevel != foodData.getSaturationLevel()) {
                        replayViewer.lastFirstPersonFoodLevel = foodData.getFoodLevel();
                        replayViewer.lastFirstPersonSaturationLevel = foodData.getSaturationLevel();
                        ServerPlayNetworking.send(replayViewer, new FlashbackRemoteFoodData(playerCamera.getId(), foodData.getFoodLevel(), foodData.getSaturationLevel()));
                    }

                    if (replayViewer.lastFirstPersonSelectedSlot != inventory.selected) {
                        replayViewer.lastFirstPersonSelectedSlot = inventory.selected;
                        ServerPlayNetworking.send(replayViewer, new FlashbackRemoteSelectHotbarSlot(playerCamera.getId(), inventory.selected));
                    }

                    for (int i = 0; i < 9; i++) {
                        ItemStack hotbarItem = inventory.getItem(i);
                        if (!ItemStack.matches(replayViewer.lastFirstPersonHotbarItems[i], hotbarItem)) {
                            replayViewer.lastFirstPersonHotbarItems[i] = hotbarItem.copy();
                            ServerPlayNetworking.send(replayViewer, new FlashbackRemoteSetSlot(playerCamera.getId(), i, hotbarItem.copy()));
                        }
                    }
                }
            } else {
                replayViewer.lastFirstPersonDataUUID = null;
            }
        }

        // Update resourcepacks
        if (this.remotePacks.isEmpty()) {
            if (!this.oldRemotePacks.isEmpty()) {
                this.oldRemotePacks.clear();
                this.getPlayerList().broadcastAll(new ClientboundResourcePackPopPacket(Optional.empty()));
            }
        } else {
            for (Map.Entry<UUID, RemotePack> entry : this.remotePacks.entrySet()) {
                RemotePack remotePack = entry.getValue();
                RemotePack oldRemotePack = this.oldRemotePacks.get(entry.getKey());
                if (oldRemotePack == null) {
                    this.getPlayerList().broadcastAll(new ClientboundResourcePackPushPacket(remotePack.id, remotePack.url, remotePack.hash,
                        true, Optional.empty()));
                } else if (!oldRemotePack.equals(remotePack)) {
                    this.getPlayerList().broadcastAll(new ClientboundResourcePackPopPacket(Optional.of(remotePack.id)));
                    this.getPlayerList().broadcastAll(new ClientboundResourcePackPushPacket(remotePack.id, remotePack.url, remotePack.hash,
                        true, Optional.empty()));
                }
                this.oldRemotePacks.put(entry.getKey(), remotePack);
            }
            this.oldRemotePacks.keySet().removeIf(uuid -> {
                if (!this.remotePacks.containsKey(uuid)) {
                    this.getPlayerList().broadcastAll(new ClientboundResourcePackPopPacket(Optional.of(uuid)));
                    return true;
                } else {
                    return false;
                }
            });
        }

        if (this.sendFinishedServerTick.compareAndExchange(true, false)) {
            for (ReplayPlayer replayViewer : this.replayViewers) {
                ServerPlayNetworking.send(replayViewer, FinishedServerTick.INSTANCE);
            }
            if (this.replayViewers.isEmpty() && Flashback.EXPORT_JOB != null) {
                Flashback.EXPORT_JOB.onFinishedServerTick();
            }
        }
    }

    private void runUpdates(BooleanSupplier booleanSupplier) {
        this.desiredTickRate = 20.0f;
        this.desiredFrozen = false;
        this.getEditorState().applyKeyframes(new ReplayServerKeyframeHandler(this), this.targetTick);

        if (this.desiredFrozen && this.frozenDelay < 0) {
            if (this.desiredFrozenDelay <= 0) {
                this.frozenDelay = 1;
            } else if (this.desiredFrozenDelay <= 5) {
                this.frozenDelay = 2;
            } else {
                this.frozenDelay = 3;
            }
        }

        float tickRate = this.desiredTickRate;
        if (Flashback.EXPORT_JOB == null) {
            tickRate *= this.desiredTickRateManual / 20f;
        }

        if (this.desiredFrozen) {
            if (this.frozenDelay > 0) {
                this.frozenDelay -= 1;
            }
            if (this.frozenDelay == 0) {
                this.isFrozen = true;
            }
        } else {
            this.frozenDelay = -1;
            this.isFrozen = false;
        }

        // Update tick rate & frozen state
        ServerTickRateManager tickRateManager = this.tickRateManager();
        if (tickRateManager.tickrate() != tickRate) {
            tickRateManager.setTickRate(tickRate);
        }
        if (tickRateManager.isFrozen() != isFrozen) {
            tickRateManager.setFrozen(isFrozen);
        }

        boolean tickChanged = this.targetTick != this.currentTick;

        if (this.isFrozen && this.targetTick >= this.currentTick) {
            tickChanged = false;
        } else {
            this.handleActions();
        }

        // Add tickets for keeping entities loaded
        for (ServerLevel level : this.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
                level.getChunkSource().addRegionTicket(ENTITY_LOAD_TICKET, chunkPos, 3, chunkPos);
            }
        }

        // Tick underlying server
        super.tickServer(booleanSupplier);

        // Teleport entities
        if (!this.isFrozen && !this.needsPositionUpdate.isEmpty()) {
            for (Map.Entry<ResourceKey<Level>, IntSet> entry : this.needsPositionUpdate.entrySet()) {
                ResourceKey<Level> dimension = entry.getKey();
                IntSet entities = entry.getValue();

                ServerLevel level = this.getLevel(dimension);
                if (level != null) {
                    Int2ObjectMap<ChunkMap.TrackedEntity> entityMap = level.getChunkSource().chunkMap.entityMap;

                    IntIterator iterator = entities.intIterator();
                    while (iterator.hasNext()) {
                        int entityId = iterator.nextInt();

                        ChunkMap.TrackedEntity trackedEntity = entityMap.get(entityId);
                        if (trackedEntity == null) {
                            continue;
                        }

                        ServerEntity serverEntity = trackedEntity.serverEntity;

                        Vec3 trackingPosition = serverEntity.entity.trackingPosition();

                        int quantizedYRot = Mth.floor(serverEntity.entity.getYRot() * 256.0F / 360.0F);
                        int quantizedXRot = Mth.floor(serverEntity.entity.getXRot() * 256.0F / 360.0F);

                        if (!serverEntity.entity.isPassenger() && !serverEntity.positionCodec.getBase().equals(trackingPosition)) {
                            trackedEntity.broadcast(new ClientboundTeleportEntityPacket(serverEntity.entity));
                            serverEntity.positionCodec.setBase(trackingPosition);
                            serverEntity.lastSentYRot = quantizedYRot;
                            serverEntity.lastSentXRot = quantizedXRot;
                        } else if (quantizedYRot != serverEntity.lastSentYRot || quantizedXRot != serverEntity.lastSentXRot) {
                            trackedEntity.broadcast(new ClientboundMoveEntityPacket.Rot(entityId, (byte) quantizedYRot, (byte) quantizedXRot, serverEntity.wasOnGround));
                            serverEntity.lastSentYRot = quantizedYRot;
                            serverEntity.lastSentXRot = quantizedXRot;
                        }
                    }
                }
            }
            this.needsPositionUpdate.clear();
        }

        if (Flashback.EXPORT_JOB == null) {
            if (this.processedSnapshot) {
                this.processedSnapshot = false;

                for (ReplayPlayer replayViewer : this.replayViewers) {
                    replayViewer.connection.chunkSender.sendNextChunks(replayViewer);
                    ServerPlayNetworking.send(replayViewer, FlashbackInstantlyLerp.INSTANCE);
                    ServerPlayNetworking.send(replayViewer, FlashbackClearParticles.INSTANCE);
                }
                for (ServerLevel level : this.getAllLevels()) {
                    for (ServerPlayer player : level.players()) {
                        if (player instanceof ReplayPlayer) {
                            // Called twice, first one will update the chunk tracking & second one will update the entity tracking
                            level.getChunkSource().move(player);
                            level.getChunkSource().move(player);
                        }
                    }
                }
            }

            if (this.replayPaused) {
                if (tickChanged) {
                    if (tickRateManager.isFrozen()) {
                        tickRateManager.setFrozen(false);
                    }
                    ((ServerTickRateManagerExt)tickRateManager).flashback$setSuppressClientUpdates(false);
                    for (ReplayPlayer replayViewer : this.replayViewers) {
                        ServerPlayNetworking.send(replayViewer, FlashbackForceClientTick.INSTANCE);
                    }
                    tickRateManager.setFrozen(true);
                    ((ServerTickRateManagerExt)tickRateManager).flashback$setSuppressClientUpdates(true);
                } else if (!tickRateManager.isFrozen()) {
                    tickRateManager.setFrozen(true);
                }
            } else if (tickRateManager.isFrozen() != isFrozen) {
                tickRateManager.setFrozen(isFrozen);
            }
        }
    }

    @Override
    public void synchronizeTime(ServerLevel level) {
    }

    private void handleActions() {
        this.targetTick = Math.max(0, Math.min(this.totalTicks, this.targetTick));

        if (this.targetTick == this.currentTick) {
            return;
        }

        Map.Entry<Integer, PlayableChunk> oldEntry = this.playableChunksByStart.floorEntry(this.currentTick);

        int duration;
        if (oldEntry != null) {
            duration = oldEntry.getValue().chunkMeta.duration;
        } else {
            duration = Recorder.CHUNK_LENGTH_SECONDS * 20;
        }
        duration = Math.max(60 * 20, duration);

        boolean shouldJump = this.targetTick < this.currentTick;
        if (this.targetTick > this.currentTick + duration) {
            if (oldEntry != null) {
                Map.Entry<Integer, PlayableChunk> targetEntry = this.playableChunksByStart.floorEntry(this.targetTick);
                shouldJump |= oldEntry.getValue() != targetEntry.getValue();
            } else {
                shouldJump = true;
            }
        }

        if (shouldJump) {
            this.processedSnapshot = true;

            this.clearDataForPlayingSnapshot();

            Map.Entry<Integer, PlayableChunk> entry = this.playableChunksByStart.floorEntry(this.targetTick);
            ReplayReader replayReader = entry.getValue().getOrLoadReplayReader(this.registryAccess());
            replayReader.handleSnapshot(this);
            entry.getValue().getOrLoadReplayReader(this.registryAccess()).resetToStart();
            this.currentTick = entry.getKey();
        }

        Map.Entry<Integer, PlayableChunk> entry = this.playableChunksByStart.floorEntry(this.currentTick);
        if (entry == null) {
            return;
        }

        this.currentReplayReader = entry.getValue().getOrLoadReplayReader(this.registryAccess());
        if (this.currentTick == entry.getKey()) {
            this.currentReplayReader.resetToStart();

            if (!this.processedSnapshot && entry.getValue().chunkMeta.forcePlaySnapshot) {
                this.processedSnapshot = true;
                this.clearDataForPlayingSnapshot();
                this.currentReplayReader.handleSnapshot(this);
            }
        }

        while (this.currentTick < this.targetTick) {
            if (!this.currentReplayReader.handleNextAction(this)) {
                Map.Entry<Integer, PlayableChunk> newEntry = this.playableChunksByStart.floorEntry(this.currentTick);
                if (newEntry.getValue() == entry.getValue()) {
                    this.targetTick = this.currentTick;
                    this.replayPaused = true;
                    return;
                }
                entry = newEntry;

                if (newEntry.getKey() != this.currentTick) {
                    Flashback.LOGGER.error("Error processing replay: ran out of entries before expected end of PlayableChunk");
                    Flashback.LOGGER.error("Current tick: {}", this.currentTick);
                    Flashback.LOGGER.error("New entry start: {}", newEntry.getKey());
                    this.stopWithReason(Component.literal("Error processing replay: ran out of entries before expected end of PlayableChunk"));
                    return;
                }

                this.currentReplayReader = entry.getValue().getOrLoadReplayReader(this.registryAccess());
                this.currentReplayReader.resetToStart();

                if (entry.getValue().chunkMeta.forcePlaySnapshot) {
                    this.processedSnapshot = true;
                    this.clearDataForPlayingSnapshot();
                    this.currentReplayReader.handleSnapshot(this);
                }
            }

            if (entry.getKey() + entry.getValue().chunkMeta.duration < this.currentTick) {
                Flashback.LOGGER.error("Error processing replay: actual duration of PlayableChunk inconsistent with recorded duration");
                Flashback.LOGGER.error("Current tick: {}", this.currentTick);
                Flashback.LOGGER.error("PlayableChunk tick base: {}", entry.getKey());
                Flashback.LOGGER.error("PlayableChunk duration: {}", entry.getValue().chunkMeta.duration);
                this.stopWithReason(Component.literal("Error processing replay: actual duration of PlayableChunk inconsistent with recorded duration"));
            }
        }

        this.currentReplayReader = null;
    }

    private void clearDataForPlayingSnapshot() {
        for (ReplayPlayer replayViewer : this.replayViewers) {
            for (UUID uuid : this.bossEvents.keySet()) {
                replayViewer.connection.send(ClientboundBossEventPacket.createRemovePacket(uuid));
            }
        }
        this.bossEvents.clear();
    }

    private void tryFollowLocalPlayer() {
        if (this.spawnLevel == null) {
            return;
        }

        ServerLevel currentLevel = this.getLevel(this.spawnLevel);
        if (currentLevel == null) {
            return;
        }

        Entity follow = currentLevel.getEntity(this.gamePacketHandler.localPlayerId);
        if (follow == null) {
            return;
        }

        for (ReplayPlayer replayViewer : this.getReplayViewers()) {
            boolean shouldFollow = replayViewer.followLocalPlayerNextTick;
            if (this.followLocalPlayerNextTickIfWrongDimension) {
                shouldFollow |= replayViewer.level() != currentLevel;
            }
            if (shouldFollow) {
                replayViewer.followLocalPlayerNextTick = false;
                replayViewer.teleportTo(currentLevel, follow.getX(), follow.getY(), follow.getZ(),
                    follow.getYRot(), follow.getXRot());
            }
        }

        this.followLocalPlayerNextTickIfWrongDimension = false;
    }

    private void stopWithReason(Component reason) {
        this.shutdownReason = reason;
        this.halt(false);
    }

    @Override
    public boolean saveEverything(boolean bl, boolean bl2, boolean bl3) {
        return false;
    }

    @Override
    public boolean saveAllChunks(boolean bl, boolean bl2, boolean bl3) {
        return false;
    }

    @Override
    public void stopServer() {
        // Remove all levels
        for (ServerLevel level : this.levels.values()) {
            if (level == null) {
                continue;
            }
            try {
                level.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        this.levels.clear();

        // Stop server
        super.stopServer();
        TempFolderProvider.deleteTemp(TempFolderProvider.TempFolderType.SERVER, this.playbackUUID);

        if (this.playbackFileSystem != null) {
            try {
                this.playbackFileSystem.close();
            } catch (IOException e) {
                Flashback.LOGGER.error("Failed to close playback zip", e);
            }
        }

        this.levelChunkCachedPackets.clear();
        this.playableChunksByStart.clear();
    }

    public void clearReplayTempFolder() {
        Path temp = TempFolderProvider.createTemp(TempFolderProvider.TempFolderType.SERVER, this.playbackUUID);

        // Delete all files, but not directories
        try {
            Files.walkFileTree(temp, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!Files.isDirectory(file)) {
                    Files.deleteIfExists(file);
                }
                return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            Flashback.LOGGER.error("Unable to delete replay temp folder", e);
        }
    }

    @Override
    public boolean isSingleplayerOwner(GameProfile gameProfile) {
        return gameProfile.getName().equals(REPLAY_VIEWER_NAME) && gameProfile.getProperties().containsKey("IsReplayViewer");
    }
}
