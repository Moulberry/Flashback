package com.moulberry.flashback.playback;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ReplayVisuals;
import com.moulberry.flashback.TempFolderProvider;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateCache;
import com.moulberry.flashback.ext.LevelChunkSectionExt;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.ext.ServerGamePacketListenerImplExt;
import com.moulberry.flashback.io.ReplayReader;
import com.moulberry.flashback.packet.FinishedServerTick;
import com.moulberry.flashback.record.FlashbackChunkMeta;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.record.Recorder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ReplayServer extends IntegratedServer {

    private static final Gson GSON = new Gson();

    public volatile int jumpToTick = -1;
    public volatile boolean replayPaused = true;
    public AtomicBoolean sendFinishedServerTick = new AtomicBoolean(false);
    public volatile float desiredTickRate = 20.0f;

    private int currentTick = 0;
    private int targetTick = 0;
    private final int totalTicks;
    public ResourceKey<Level> spawnLevel = null;
    private final ReplayGamePacketHandler gamePacketHandler;
    private final ReplayConfigurationPacketHandler configurationPacketHandler;
    private StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> gamePacketCodec;
    private final StreamCodec<ByteBuf, Packet<? super ClientConfigurationPacketListener>> configurationPacketCodec;
    private final List<ReplayPlayer> replayViewers = new ArrayList<>();
    public boolean followLocalPlayerNextTick = false;
    public boolean followLocalPlayerNextTickIfFar = false;
    public boolean isProcessingSnapshot = false;
    private boolean processedSnapshot = false;

    private final UUID playbackUUID;
    private final FlashbackMeta metadata;
    private final TreeMap<Integer, PlayableChunk> playableChunksByStart = new TreeMap<>();
    private final Int2ObjectMap<ClientboundLevelChunkWithLightPacket> levelChunkCachedPackets = new Int2ObjectOpenHashMap<>();
    private ReplayReader currentReplayReader = null;

    private record RemotePack(UUID id, String url, String hash){}
    private final Map<UUID, RemotePack> oldRemotePacks = new HashMap<>();
    private final Map<UUID, RemotePack> remotePacks = new HashMap<>();
    private final Map<UUID, BossEvent> bossEvents = new HashMap<>();
    private Component tabListHeader = Component.empty();
    private Component tabListFooter = Component.empty();

    private Component shutdownReason = null;

    public ReplayServer(Thread thread, Minecraft minecraft, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, Services services,
                        ChunkProgressListenerFactory chunkProgressListenerFactory, UUID playbackUUID, Path path) {
        super(thread, minecraft, levelStorageAccess, packRepository, worldStem, services, chunkProgressListenerFactory);
        this.playbackUUID = playbackUUID;
        this.gamePacketHandler = new ReplayGamePacketHandler(this);
        this.configurationPacketHandler = new ReplayConfigurationPacketHandler(this);

        this.gamePacketCodec = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.registryAccess())).codec();
        this.configurationPacketCodec = ConfigurationProtocols.CLIENTBOUND.codec();

        try {
            Path playbackFolder = TempFolderProvider.createTemp(TempFolderProvider.TempFolderType.PLAYBACK, playbackUUID);

            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(Files.newInputStream(path));
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(playbackFolder.toFile(), zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zipEntry = zis.getNextEntry();
            }

            String metadataJson = Files.readString(playbackFolder.resolve("metadata.json"));
            this.metadata = FlashbackMeta.fromJson(GSON.fromJson(metadataJson, JsonObject.class));
            if (this.metadata == null) {
                throw new RuntimeException("Invalid metadata file");
            }

            int ticks = 0;
            for (Map.Entry<String, FlashbackChunkMeta> entry : this.metadata.chunks.entrySet()) {
                var chunkMetaWithPath = new PlayableChunk(entry.getValue(), playbackFolder.resolve(entry.getKey()));
                this.playableChunksByStart.put(ticks, chunkMetaWithPath);
                ticks += entry.getValue().duration;
            }

            this.totalTicks = ticks;

            Path levelChunkCachePath = playbackFolder.resolve("level_chunk_cache");
            if (Files.exists(levelChunkCachePath)) {
                int chunkCacheIndex = 0;

                byte[] bytes = Files.readAllBytes(levelChunkCachePath);
                ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
                while (byteBuf.readerIndex() < byteBuf.writerIndex()) {
                    int size = byteBuf.readInt();

                    if (size < 0) {
                        Flashback.LOGGER.error("Expected positive size for chunk packet, got {}", size);
                        break;
                    }

                    if (byteBuf.readerIndex() > byteBuf.writerIndex() - size) {
                        Flashback.LOGGER.error("Ran out of bytes while reading level_chunk_cache, needed {}, had {}",
                                size, byteBuf.writerIndex() - byteBuf.readerIndex());
                        break;
                    }

                    RegistryFriendlyByteBuf registryFriendlyByteBuf = new RegistryFriendlyByteBuf(byteBuf.readSlice(size), this.registryAccess());

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
                Flashback.LOGGER.info("Loaded level_chunk_cache with {} entries", this.levelChunkCachedPackets.size());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public FlashbackMeta getMetadata() {
        return this.metadata;
    }

    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
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
        AtomicInteger newPlayerIds = new AtomicInteger(-981723987);
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
            public boolean canBypassPlayerLimit(GameProfile gameProfile) {
                return true;
            }

            @Override
            public void broadcastSystemMessage(Component component, Function<ServerPlayer, Component> function, boolean bl) {
            }

            @Override
            public void sendLevelInfo(ServerPlayer serverPlayer, ServerLevel serverLevel) {
                super.sendLevelInfo(serverPlayer, serverLevel);

                // Send all resource packs
                serverPlayer.connection.send(new ClientboundResourcePackPopPacket(Optional.empty()));
                for (RemotePack remotePack : remotePacks.values()) {
                    serverPlayer.connection.send(new ClientboundResourcePackPushPacket(remotePack.id,
                        remotePack.url, remotePack.hash, true, Optional.empty()));
                }

                // Send tab list customization
                serverPlayer.connection.send(new ClientboundTabListPacket(tabListHeader, tabListFooter));
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
                    for (int i = 0; i < players.size(); ++i) {
                        players.get(i).connection.disconnect(shutdownReason);
                    }
                }
            }
        });

        this.loadLevel();
        this.overworld().noSave = true;

        // Play initial snapshot
        ReplayReader replayReader = this.playableChunksByStart.get(0).getOrLoadReplayReader(this.registryAccess());
        replayReader.handleSnapshot(this);

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

    public int getReplayTick() {
        return this.targetTick;
    }

    private int lastReplayTick;
    private long lastTickTimeNanos;

    public float getPartialReplayTick() {
        int lastReplayTick = this.lastReplayTick;
        if (this.replayPaused || this.isPaused()) {
            return lastReplayTick;
        } else {
            long currentNanos = Util.getNanos();
            long nanosPerTick = this.tickRateManager().nanosecondsPerTick();

            double partial = (currentNanos - this.lastTickTimeNanos) / (double) nanosPerTick;
            partial = Math.max(0, Math.min(1, partial));

            return lastReplayTick + (float) partial;
        }
    }

    public int getTotalReplayTicks() {
        return this.totalTicks;
    }

    public boolean doClientRendering() {
        return !this.isProcessingSnapshot && !this.processedSnapshot;
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

        Packet<? super ClientGamePacketListener> packet = this.gamePacketCodec.decode(friendlyByteBuf);
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

    public void handleMoveEntities(RegistryFriendlyByteBuf friendlyByteBuf) {
        this.gamePacketHandler.flushPendingEntities();
        this.configurationPacketHandler.flushPendingConfiguration();

        int levelCount = friendlyByteBuf.readVarInt();
        for (int i = 0; i < levelCount; i++) {
            ResourceKey<Level> dimension = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
            ServerLevel level = this.levels.get(dimension);

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
                        entity.moveTo(x, y, z, yaw, pitch);
                        entity.setYHeadRot(headYaw);
                        if (entity.onGround() != onGround) {
                            entity.setOnGround(onGround);
                        }
                    }
                }
            }
        }
    }

    public void handleLevelChunkCached(int index) {
        ClientboundLevelChunkWithLightPacket packet = this.levelChunkCachedPackets.get(index);
        if (packet != null) {
            this.configurationPacketHandler.flushPendingConfiguration();
            this.gamePacketHandler.flushPendingEntities();

            int x = packet.getX();
            int z = packet.getZ();
            LevelChunk chunk = this.gamePacketHandler.level().getChunk(x, z);

            if (!doesCachedChunkIdMatch(chunk, index)) {
                packet.handle(this.gamePacketHandler);

                for (LevelChunkSection section : chunk.getSections()) {
                    ((LevelChunkSectionExt)section).flashback$setCachedChunkId(index);
                }
            }
        } else {
            Flashback.LOGGER.error("Missing cached level chunk: {}", index);
        }
    }

    private static boolean doesCachedChunkIdMatch(LevelChunk chunk, int chunkId) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (chunkId != ((LevelChunkSectionExt) section).flashback$getCachedChunkId()) {
                return false;
            }
        }

        return true;
    }

    public static final TicketType<ChunkPos> ENTITY_LOAD_TICKET = TicketType.create("replay_entity_load", Comparator.comparingLong(ChunkPos::toLong), 5);

    public void closeLevel(ServerLevel serverLevel) {
        this.clearLevel(serverLevel);
        try {
            serverLevel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void clearLevel(ServerLevel serverLevel) {
        for (ServerPlayer player : new ArrayList<>(serverLevel.players())) {
            if (player instanceof ReplayPlayer) {
                continue;
            }
            player.connection.disconnect(Component.empty());
        }
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof ReplayPlayer) {
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
    }

    @Override
    public boolean haveTime() {
        return super.haveTime() && this.jumpToTick < 0;
    }

    @Override
    public void tickServer(BooleanSupplier booleanSupplier) {
        this.lastReplayTick = this.currentTick;
        this.lastTickTimeNanos = this.nextTickTimeNanos - this.tickRateManager().nanosecondsPerTick();

        if (!this.replayPaused) {
            EditorState editorState = EditorStateCache.get(this.metadata.replayIdentifier);
            editorState.applyKeyframes(this, this.lastReplayTick);
        }

        // Update list of replay viewers
        this.replayViewers.clear();
        for (ServerPlayer player : this.getPlayerList().getPlayers()) {
            if (player instanceof ReplayPlayer replayPlayer) {
                this.replayViewers.add(replayPlayer);
            }
        }

        int extraForceEntityLerpTicks = 0;
        if (this.jumpToTick >= 0) {
            this.targetTick = this.jumpToTick;
            this.jumpToTick = -1;
            extraForceEntityLerpTicks = Math.max(0, Math.abs(this.targetTick - this.currentTick) - 1);
        } else if (!this.isPaused() && !this.replayPaused && this.targetTick < this.totalTicks) {
            // Normal playback
            this.targetTick += 1;
        } else if (this.targetTick == this.totalTicks && this.currentTick == this.totalTicks) {
            // Pause when reaching end of replay
            this.replayPaused = true;
        }

        // Update tick rate & frozen state
        if (this.tickRateManager().tickrate() != this.desiredTickRate) {
            this.tickRateManager().setTickRate(this.desiredTickRate);
        }

        boolean wantFrozen = this.replayPaused && this.targetTick == this.currentTick;
        if (this.tickRateManager().isFrozen() != wantFrozen) {
            this.tickRateManager().setFrozen(wantFrozen);
        }

        this.handleActions();
        this.tryFollowLocalPlayer();

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

        if (extraForceEntityLerpTicks > 0) {
            ReplayVisuals.forceEntityLerpTicks = Math.min(10, ReplayVisuals.forceEntityLerpTicks + extraForceEntityLerpTicks);
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

        if (this.processedSnapshot) {
            this.processedSnapshot = false;

            for (ReplayPlayer replayViewer : this.replayViewers) {
                replayViewer.connection.chunkSender.sendNextChunks(replayViewer);
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

            if (Flashback.EXPORT_JOB == null) {
                ReplayVisuals.forceEntityLerpTicks = 10;
                ((MinecraftExt)Minecraft.getInstance()).flashback$forceClientTick();
            }
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
            boolean shouldFollow = replayViewer.followLocalPlayerNextTick || this.followLocalPlayerNextTick;
            if (this.followLocalPlayerNextTickIfFar) {
                shouldFollow |= replayViewer.level() != currentLevel || replayViewer.distanceToSqr(follow) > 256*256;
            }
            if (shouldFollow) {
                replayViewer.followLocalPlayerNextTick = false;
                replayViewer.teleportTo(currentLevel, follow.getX(), follow.getY(), follow.getZ(),
                    follow.getYRot(), follow.getXRot());
            }
        }

        this.followLocalPlayerNextTick = false;
        this.followLocalPlayerNextTickIfFar = false;
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
        this.clearReplayTempFolder();

        TempFolderProvider.deleteTemp(TempFolderProvider.TempFolderType.PLAYBACK, this.playbackUUID);
    }

    public void clearReplayTempFolder() {
        TempFolderProvider.deleteTemp(TempFolderProvider.TempFolderType.SERVER, this.playbackUUID);
    }

    @Override
    public boolean isSingleplayerOwner(GameProfile gameProfile) {
        return gameProfile.getName().equals("ReplayViewer");
    }
}
