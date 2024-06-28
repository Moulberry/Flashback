package com.moulberry.flashback.playback;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.FlashbackClient;
import com.moulberry.flashback.ReplayVisuals;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.ext.ServerGamePacketListenerImplExt;
import com.moulberry.flashback.io.ReplayReader;
import com.moulberry.flashback.keyframe.Keyframes;
import com.moulberry.flashback.player.ReplayPlayer;
import com.moulberry.flashback.record.FlashbackChunkMeta;
import com.moulberry.flashback.record.FlashbackMeta;
import io.netty.buffer.ByteBuf;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.GameProtocols;
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
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.FileUtils;

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
    public volatile float desiredTickRate = 20.0f;
    public AtomicBoolean finishedServerTick = new AtomicBoolean(false);

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
    public boolean isProcessingSnapshot = false;
    private boolean processedSnapshot = false;

    private final UUID playbackUUID = UUID.randomUUID();
    private final FlashbackMeta metadata;
    private final TreeMap<Integer, PlayableChunk> playableChunksByStart = new TreeMap<>();
    private ReplayReader currentReplayReader = null;

    private record RemotePack(UUID id, String url, String hash){}
    private final Map<UUID, RemotePack> oldRemotePacks = new HashMap<>();
    private final Map<UUID, RemotePack> remotePacks = new HashMap<>();
    private final Map<UUID, BossEvent> bossEvents = new HashMap<>();
    private Component tabListHeader = Component.empty();
    private Component tabListFooter = Component.empty();

    public ReplayServer(Thread thread, Minecraft minecraft, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, Services services, ChunkProgressListenerFactory chunkProgressListenerFactory) {
        super(thread, minecraft, levelStorageAccess, packRepository, worldStem, services, chunkProgressListenerFactory);
        this.gamePacketHandler = new ReplayGamePacketHandler(this);
        this.configurationPacketHandler = new ReplayConfigurationPacketHandler(this);

        this.gamePacketCodec = GameProtocols.CLIENTBOUND.bind(RegistryFriendlyByteBuf.decorator(this.registryAccess())).codec();
        this.configurationPacketCodec = ConfigurationProtocols.CLIENTBOUND.codec();

        Path path = FabricLoader.getInstance().getGameDir().resolve("debug_recording.zip");
        try {
            Path playbackFolder = FlashbackClient.getReplayPlaybackTemp(playbackUUID);

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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

        this.gamePacketCodec = GameProtocols.CLIENTBOUND.bind(RegistryFriendlyByteBuf.decorator(this.registryAccess())).codec();

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
                        entity.setOnGround(onGround);
                    }
                }
            }
        }
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

        Keyframes.applyKeyframes(this, this.lastReplayTick);

        // Update list of replay viewers
        this.replayViewers.clear();
        for (ServerPlayer player : this.getPlayerList().getPlayers()) {
            if (player instanceof ReplayPlayer replayPlayer) {
                this.replayViewers.add(replayPlayer);
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

        int extraForceEntityLerpTicks = 0;
        if (this.jumpToTick >= 0) {
            this.targetTick = this.jumpToTick;
            this.jumpToTick = -1;
            extraForceEntityLerpTicks = Math.max(0, Math.abs(this.targetTick - this.currentTick) - 1);
        } else if (!this.isPaused() && !this.replayPaused && this.targetTick < this.totalTicks) {
            // Normal playback
            this.targetTick += 1;
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

            if (FlashbackClient.EXPORT_JOB == null) {
                ReplayVisuals.forceEntityLerpTicks = 10;
                ((MinecraftExt)Minecraft.getInstance()).flashback$forceClientTick();
            }
        }

        this.finishedServerTick.set(true);
    }

    private void handleActions() {
        this.targetTick = Math.max(0, Math.min(this.totalTicks, this.targetTick));

        if (this.targetTick == this.currentTick) {
            return;
        }

        if (this.targetTick < this.currentTick) {
            this.processedSnapshot = true;
            Map.Entry<Integer, PlayableChunk> entry = this.playableChunksByStart.floorEntry(this.targetTick);
            ReplayReader replayReader = entry.getValue().getOrLoadReplayReader(this.registryAccess());
            replayReader.handleSnapshot(this);
            this.currentTick = entry.getKey();
        }

        Map.Entry<Integer, PlayableChunk> entry = this.playableChunksByStart.floorEntry(this.currentTick);
        if (entry == null) {
            return;
        }

        this.currentReplayReader = entry.getValue().getOrLoadReplayReader(this.registryAccess());
        // todo: is this problematic if we somehow have a chunk with only 1 tick in it?
        if (entry.getKey() == this.currentTick) {
            this.currentReplayReader.resetToStart();
        }

        // todo: what do we do when the number of ticks in the chunk doesn't match the number of ticks we think it has

        while (this.currentTick < this.targetTick) {
            if (!this.currentReplayReader.handleNextAction(this)) {
                Map.Entry<Integer, PlayableChunk> newEntry = this.playableChunksByStart.floorEntry(this.currentTick);
                if (newEntry.getValue() == entry.getValue()) {
                    this.targetTick = this.currentTick;
                    return;
                }
                entry = newEntry;

                if (newEntry.getKey() != this.currentTick) {
                    throw new RuntimeException("didn't match?");
                }

                this.currentReplayReader = entry.getValue().getOrLoadReplayReader(this.registryAccess());
                this.currentReplayReader.resetToStart();
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
            if (replayViewer.followLocalPlayerNextTick || this.followLocalPlayerNextTick) {
                replayViewer.followLocalPlayerNextTick = false;
                replayViewer.teleportTo(currentLevel, follow.getX(), follow.getY(), follow.getZ(),
                    follow.getYRot(), follow.getXRot());
            }
        }
        this.followLocalPlayerNextTick = false;
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

        Path playbackTemp = FlashbackClient.getReplayPlaybackTemp(this.playbackUUID);
        try {
            FileUtils.deleteDirectory(playbackTemp.toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void clearReplayTempFolder() {
        Path replayTemp = FlashbackClient.getReplayServerTemp();
        try {
            FileUtils.deleteDirectory(replayTemp.toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isSingleplayerOwner(GameProfile gameProfile) {
        return gameProfile.getName().equals("ReplayViewer");
    }
}
