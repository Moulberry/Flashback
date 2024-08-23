package com.moulberry.flashback.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.PacketHelper;
import com.moulberry.flashback.action.*;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.io.ReplayWriter;
import io.netty.buffer.ByteBuf;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.core.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.scores.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class Recorder {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int CHUNK_LENGTH_SECONDS = 5 * 60;

    private final AsyncReplaySaver asyncReplaySaver;
    private final StreamCodec<ByteBuf, Packet<? super ClientConfigurationPacketListener>> configurationPacketCodec;
    private StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> gamePacketCodec;

    private int writtenTicksInChunk = 0;
    private int writtenTicks = 0;
    private final FlashbackMeta metadata = new FlashbackMeta();
    private boolean hasTakenScreenshot = false;

    private record PacketWithPhase(Packet<?> packet, ConnectionProtocol phase){}
    private final Queue<PacketWithPhase> pendingPackets = new ConcurrentLinkedQueue<>();

    private record Position(double x, double y, double z, float yaw, float pitch, float headYRot, boolean onGround) {
        public Position {
            yaw = Mth.wrapDegrees(yaw);
            pitch = Mth.wrapDegrees(pitch);
            headYRot = Mth.wrapDegrees(headYRot);
        }
    }
    private final WeakHashMap<Entity, Position> lastPositions = new WeakHashMap<>();

    // Local player data
    private final List<Object> lastPlayerEntityMeta = new ArrayList<>();
    private final Map<EquipmentSlot, ItemStack> lastPlayerEquipment = new EnumMap<>(EquipmentSlot.class);
    private BlockPos lastDestroyPos = null;
    private int lastDestroyProgress = -1;
    private boolean wasSwinging = false;
    private int lastSwingTime = -1;

    private boolean isConfiguring = false;
    private boolean finishedConfiguration = false;
    private boolean finishedPausing = false;
    private ResourceKey<Level> lastDimensionType = null;

    private volatile boolean needsInitialSnapshot = true;
    private volatile boolean closeForWriting = false;
    private volatile boolean isPaused = false;
    private volatile boolean wasPaused = false;

    public Recorder(RegistryAccess registryAccess) {
        this.asyncReplaySaver = new AsyncReplaySaver(registryAccess);
        this.configurationPacketCodec = ConfigurationProtocols.CLIENTBOUND.codec();
        this.gamePacketCodec = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registryAccess)).codec();

        this.metadata.dataVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        this.metadata.protocolVersion = SharedConstants.getProtocolVersion();
        this.metadata.versionString = SharedConstants.VERSION_STRING;
    }

    public void setRegistryAccess(RegistryAccess registryAccess) {
        this.asyncReplaySaver.submit(writer -> writer.setRegistryAccess(registryAccess));
        this.gamePacketCodec = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registryAccess)).codec();
    }

    public String getDebugString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[Flashback] Recording. T: ");
        builder.append(this.writtenTicks);
        builder.append(". S: ");
        builder.append(this.metadata.chunks.size());
        builder.append(" (");
        builder.append(this.writtenTicksInChunk);
        builder.append("/");
        builder.append(CHUNK_LENGTH_SECONDS*20);
        builder.append(")");
        return builder.toString();
    }

    public void endTick(boolean close) {
        if (this.closeForWriting) {
            return;
        } else if (close) {
            this.closeForWriting = true;
        }

        if (this.isPaused) {
            this.wasPaused = true;
        }

        if (this.needsInitialSnapshot) {
            this.needsInitialSnapshot = false;
            this.writeSnapshot(true);
        }

        this.finishedConfiguration |= this.flushPackets();

        Minecraft minecraft = Minecraft.getInstance();

        boolean isLevelLoaded = !(Minecraft.getInstance().screen instanceof ReceivingLevelScreen);
        boolean changedDimensions = false;

        if (minecraft.level != null && (minecraft.getOverlay() == null || !minecraft.getOverlay().isPauseScreen()) &&
                !minecraft.isPaused() && !this.isPaused && isLevelLoaded) {
            this.writeEntityPositions();
            this.writeLocalData();

            this.asyncReplaySaver.submit(writer -> writer.startAndFinishAction(ActionNextTick.INSTANCE));
            this.writtenTicksInChunk += 1;
            this.writtenTicks += 1;

            if (!this.hasTakenScreenshot && ((this.writtenTicks >= 20 && minecraft.screen == null) || close)) {
                NativeImage nativeImage = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
                this.asyncReplaySaver.writeIcon(nativeImage);
                this.hasTakenScreenshot = true;
            }

            // Write chunk after changing dimensions
            ResourceKey<Level> dimension = minecraft.level.dimension();
            if (this.lastDimensionType == null) {
                this.lastDimensionType = dimension;
            } else if (this.lastDimensionType != dimension) {
                this.lastDimensionType = dimension;
                changedDimensions = true;
            }
        }

        this.finishedPausing |= this.wasPaused && !this.isPaused;

        boolean writeChunk = close;
        if (minecraft.level != null) {
            boolean finished = this.finishedConfiguration || this.finishedPausing;
            writeChunk |= this.writtenTicksInChunk >= CHUNK_LENGTH_SECONDS*20 || finished || changedDimensions;
        }

        if (writeChunk) {
            // Add an extra tick to avoid edge-cases with 0-length replay chunks
            if (this.writtenTicksInChunk == 0) {
                this.asyncReplaySaver.submit(writer -> writer.startAndFinishAction(ActionNextTick.INSTANCE));
                this.writtenTicksInChunk += 1;
                this.writtenTicks += 1;
            }

            int chunkId = this.metadata.chunks.size();
            String chunkName = "c" + chunkId + ".flashback";

            var chunkMeta = new FlashbackChunkMeta();
            chunkMeta.duration = this.writtenTicksInChunk;
            this.metadata.chunks.put(chunkName, chunkMeta);
            this.metadata.totalTicks = this.writtenTicks;
            String metadata = GSON.toJson(this.metadata.toJson());

            this.asyncReplaySaver.writeReplayChunk(chunkName, metadata);

            this.writtenTicksInChunk = 0;

            if (!close) {
                // When we finish pausing, we write the snapshot as normal packets directly
                if (this.finishedPausing) {
                    this.asyncReplaySaver.submit(ReplayWriter::startSnapshot);
                    this.asyncReplaySaver.submit(ReplayWriter::endSnapshot);
                    this.writeSnapshot(false);
                } else {
                    this.writeSnapshot(true);
                }
            }

            this.finishedPausing = false;
            this.finishedConfiguration = false;
            if (minecraft.level != null) {
                this.lastDimensionType = minecraft.level.dimension();
            }
        }

        if (!this.isPaused) {
            this.wasPaused = false;
        }
    }

    public boolean isPaused() {
        return this.isPaused;
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
    }

    public Path finish() {
        return this.asyncReplaySaver.finish();
    }

    private void writeLocalData() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            this.lastPlayerEntityMeta.clear();
            this.lastPlayerEquipment.clear();
            this.lastDestroyPos = null;
            this.lastDestroyProgress = -1;
            return;
        }

        List<Packet<? super ClientGamePacketListener>> gamePackets = new ArrayList<>();

        // Update entity data
        SynchedEntityData.DataItem<?>[] items = Minecraft.getInstance().player.getEntityData().itemsById;
        List<SynchedEntityData.DataValue<?>> changedData = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            SynchedEntityData.DataItem<?> dataItem = items[i];
            Object value = dataItem.value().value();

            if (i >= this.lastPlayerEntityMeta.size()) {
                this.lastPlayerEntityMeta.add(i, value);
            } else {
                Object old = this.lastPlayerEntityMeta.get(i);
                if (!Objects.equals(old, value)) {
                    this.lastPlayerEntityMeta.set(i, value);
                    changedData.add(dataItem.value());
                }
            }
        }

        if (!changedData.isEmpty()) {
            gamePackets.add(new ClientboundSetEntityDataPacket(player.getId(), changedData));
        }

        // Update equipment
        List<Pair<EquipmentSlot, ItemStack>> changedSlots = new ArrayList<>();
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            ItemStack itemStack = player.getItemBySlot(equipmentSlot);

            if (!this.lastPlayerEquipment.containsKey(equipmentSlot)) {
                this.lastPlayerEquipment.put(equipmentSlot, itemStack.copy());
            } else if (!ItemStack.matches(this.lastPlayerEquipment.get(equipmentSlot), itemStack)) {
                ItemStack copied = itemStack.copy();
                this.lastPlayerEquipment.put(equipmentSlot, copied);
                changedSlots.add(Pair.of(equipmentSlot, copied));
            }
        }
        if (!changedSlots.isEmpty()) {
            gamePackets.add(new ClientboundSetEquipmentPacket(player.getId(), changedSlots));
        }

        // Update breaking
        MultiPlayerGameMode multiPlayerGameMode = Minecraft.getInstance().gameMode;
        if (multiPlayerGameMode == null) {
            this.lastDestroyPos = null;
            this.lastDestroyProgress = -1;
        } else {
            BlockPos destroyPos = multiPlayerGameMode.destroyBlockPos.immutable();
            int destroyProgress = multiPlayerGameMode.getDestroyStage();

            boolean changed = destroyProgress != this.lastDestroyProgress;
            if (destroyProgress >= 0) {
                changed |= !destroyPos.equals(this.lastDestroyPos);
            }

            if (changed) {
                gamePackets.add(new ClientboundBlockDestructionPacket(player.getId(), destroyPos, destroyProgress));
            }

            this.lastDestroyPos = destroyPos;
            this.lastDestroyProgress = destroyProgress;
        }

        // Update swinging
        if (player.swinging && (!this.wasSwinging || this.lastSwingTime > player.swingTime)) {
            int animation = player.swingingArm == InteractionHand.MAIN_HAND ? ClientboundAnimatePacket.SWING_MAIN_HAND : ClientboundAnimatePacket.SWING_OFF_HAND;
            gamePackets.add(new ClientboundAnimatePacket(player, animation));
        }
        this.lastSwingTime = player.swingTime;
        this.wasSwinging = player.swinging;

        this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, gamePackets);
    }

    private void writeEntityPositions() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            this.lastPositions.clear();
            return;
        }

        record IdWithPosition(int id, Position position) {}
        List<IdWithPosition> changedPositions = new ArrayList<>();

        for (Entity entity : level.entitiesForRendering()) {
            if (PacketHelper.shouldIgnoreEntity(entity)) {
                continue;
            }

            Position position;
            if (entity instanceof LivingEntity livingEntity) {
                double lerpHeadRot = livingEntity.lerpHeadSteps > 0 ? livingEntity.lerpYHeadRot : livingEntity.getYHeadRot();
                position = new Position(livingEntity.lerpTargetX(), entity.lerpTargetY(), entity.lerpTargetZ(),
                    entity.lerpTargetYRot(), entity.lerpTargetXRot(), (float) lerpHeadRot, entity.onGround());
            } else {
                var trackingPosition = entity.trackingPosition();
                position = new Position(trackingPosition.x, trackingPosition.y, trackingPosition.z,
                    entity.getYRot(), entity.getXRot(), entity.getYHeadRot(), entity.onGround());
            }
            Position lastPosition = this.lastPositions.get(entity);

            if (!Objects.equals(position, lastPosition)) {
                this.lastPositions.put(entity, position);
                changedPositions.add(new IdWithPosition(entity.getId(), position));
            }
        }

        this.asyncReplaySaver.submit(writer -> {
            writer.startAction(ActionMoveEntities.INSTANCE);
            RegistryFriendlyByteBuf friendlyByteBuf = writer.friendlyByteBuf();

            friendlyByteBuf.writeVarInt(1);
            friendlyByteBuf.writeResourceKey(level.dimension());

            friendlyByteBuf.writeVarInt(changedPositions.size());
            for (IdWithPosition changedPosition : changedPositions) {
                friendlyByteBuf.writeVarInt(changedPosition.id);
                friendlyByteBuf.writeDouble(changedPosition.position.x);
                friendlyByteBuf.writeDouble(changedPosition.position.y);
                friendlyByteBuf.writeDouble(changedPosition.position.z);
                friendlyByteBuf.writeFloat(changedPosition.position.yaw);
                friendlyByteBuf.writeFloat(changedPosition.position.pitch);
                friendlyByteBuf.writeFloat(changedPosition.position.headYRot);
                friendlyByteBuf.writeBoolean(changedPosition.position.onGround);
            }

            writer.finishAction(ActionMoveEntities.INSTANCE);
        });
    }

    public boolean flushPackets() {
        if (this.pendingPackets.isEmpty()) {
            return false;
        }

        boolean endedConfiguration = false;

        List<Packet<? super ClientGamePacketListener>> gamePackets = new ArrayList<>();
        List<Packet<? super ClientConfigurationPacketListener>> configurationPackets = new ArrayList<>();

        PacketWithPhase packet;
        while ((packet = this.pendingPackets.poll()) != null) {
            if (packet.phase == ConnectionProtocol.PLAY) {
                if (!configurationPackets.isEmpty()) {
                    this.asyncReplaySaver.writeConfigurationPackets(this.configurationPacketCodec, configurationPackets);
                    configurationPackets.clear();
                }

                gamePackets.add(((Packet<? super ClientGamePacketListener>) packet.packet));

                if (this.isConfiguring) {
                    endedConfiguration = true;
                    this.isConfiguring = false;
                }
            } else if (packet.phase == ConnectionProtocol.CONFIGURATION) {
                if (!gamePackets.isEmpty()) {
                    this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, gamePackets);
                    gamePackets.clear();
                }

                configurationPackets.add(((Packet<? super ClientConfigurationPacketListener>) packet.packet));

                this.isConfiguring = true;
            } else {
                throw new IllegalArgumentException("Unsupported phase: " + packet.phase);
            }
        }

        if (!configurationPackets.isEmpty()) {
            this.asyncReplaySaver.writeConfigurationPackets(this.configurationPacketCodec, configurationPackets);
        }
        if (!gamePackets.isEmpty()) {
            this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, gamePackets);

            if (this.isConfiguring) {
                endedConfiguration = true;
                this.isConfiguring = false;
            }
        }

        return endedConfiguration;
    }

    public void writeLevelEvent(int type, BlockPos blockPos, int data, boolean globalEvent) {
        if (this.closeForWriting || this.needsInitialSnapshot || this.wasPaused) {
            return;
        }

        this.pendingPackets.add(new PacketWithPhase(new ClientboundLevelEventPacket(type, blockPos, data, globalEvent), ConnectionProtocol.PLAY));
    }

    public void writePacketAsync(Packet<?> packet, ConnectionProtocol phase) {
        if (this.closeForWriting || this.needsInitialSnapshot || this.wasPaused) {
            return;
        }

        if (packet instanceof ClientboundBundlePacket bundlePacket) {
            for (Packet<? super ClientGamePacketListener> subPacket : bundlePacket.subPackets()) {
                this.writePacketAsync(subPacket, phase);
            }
            return;
        }

        if (IgnoredPacketSet.isIgnored(packet)) {
            return;
        }

        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            int localPlayerId = localPlayer.getId();
            if (packet instanceof ClientboundSetEntityDataPacket entityDataPacket && entityDataPacket.id() == localPlayerId) {
                return;
            }
            if (packet instanceof ClientboundSetEquipmentPacket entityEquipmentPacket && entityEquipmentPacket.getEntity() == localPlayerId) {
                return;
            }
        }

        this.pendingPackets.add(new PacketWithPhase(packet, phase));
    }

    public void writeSnapshot(boolean asActualSnapshot) {
        if (asActualSnapshot) {
            this.asyncReplaySaver.submit(ReplayWriter::startSnapshot);
        }

        ClientLevel level = Minecraft.getInstance().level;
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        ClientChunkCache clientChunkCache = level.getChunkSource();

        AtomicReferenceArray<LevelChunk> chunks = clientChunkCache.storage.chunks;

        // Configuration data

        List<Packet<? super ClientConfigurationPacketListener>> configurationPackets = new ArrayList<>();

        // Enabled features
        configurationPackets.add(new ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(level.enabledFeatures())));

        // Registry data
        RegistryOps<Tag> dynamicOps = localPlayer.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        RegistrySynchronization.packRegistries(dynamicOps, localPlayer.registryAccess(), Set.of(), (resourceKey, list) -> {
            configurationPackets.add(new ClientboundRegistryDataPacket(resourceKey, list));
        });

        // Tags
        Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> serializedTags = new HashMap<>();
        RegistryLayer.createRegistryAccess().compositeAccess().registries().forEach(entry -> {
            if (entry.value().size() > 0) {
                var tags = TagNetworkSerialization.serializeToNetwork(entry.value());
                if (tags.size() > 0) {
                    serializedTags.put(entry.key(), tags);
                }
            }
        });
        localPlayer.registryAccess().registries().forEach(entry -> {
            if (serializedTags.containsKey(entry.key())) {
                return;
            }
            if (RegistrySynchronization.NETWORKABLE_REGISTRIES.contains(entry.key()) && entry.value().size() > 0) {
                var tags = TagNetworkSerialization.serializeToNetwork(entry.value());
                if (tags.size() > 0) {
                    serializedTags.put(entry.key(), tags);
                }
            }
        });

        configurationPackets.add(new ClientboundUpdateTagsPacket(serializedTags));

        // Resource packs
        configurationPackets.add(new ClientboundResourcePackPopPacket(Optional.empty()));
        for (ServerPackManager.ServerPackData pack : Minecraft.getInstance().getDownloadedPackSource().manager.packs) {
            configurationPackets.add(new ClientboundResourcePackPushPacket(pack.id, pack.url.toString(), pack.hash == null ? "" : pack.hash.toString(),
                true, Optional.empty()));
        }

        this.asyncReplaySaver.writeConfigurationPackets(this.configurationPacketCodec, configurationPackets);
        List<Packet<? super ClientGamePacketListener>> gamePackets = new ArrayList<>();

        // Login packet
        CommonPlayerSpawnInfo commonPlayerSpawnInfo = new CommonPlayerSpawnInfo(level.dimensionTypeRegistration(), level.dimension(), 0,
            gameMode.getPlayerMode(), gameMode.getPreviousPlayerMode(), level.isDebug(), level.getLevelData().isFlat, Optional.empty(), 0);
        var loginPacket = new ClientboundLoginPacket(localPlayer.getId(), level.getLevelData().isHardcore(), connection.levels(),
            1, Minecraft.getInstance().options.getEffectiveRenderDistance(), level.getServerSimulationDistance(),
            localPlayer.isReducedDebugInfo(), localPlayer.shouldShowDeathScreen(), localPlayer.getDoLimitedCrafting(), commonPlayerSpawnInfo, false);
        gamePackets.add(loginPacket);

        // Create player info update packet
        var infoUpdatePacket = new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of());
        infoUpdatePacket.entries = new ArrayList<>(level.players().size());
        Set<UUID> addedEntries = new HashSet<>();
        for (AbstractClientPlayer player : level.players()) {
            if (addedEntries.add(player.getUUID())) {
                PlayerInfo info = connection.getPlayerInfo(player.getUUID());
                if (info != null) {
                    infoUpdatePacket.entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(player.getUUID(),
                        player.getGameProfile(), true, info.getLatency(), info.getGameMode(), info.getTabListDisplayName(), null));
                } else {
                    infoUpdatePacket.entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(player.getUUID(),
                        player.getGameProfile(), true, 0, GameType.DEFAULT_MODE, player.getDisplayName(), null));
                }
            }
        }
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            if (addedEntries.add(info.getProfile().getId())) {
                infoUpdatePacket.entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(info.getProfile().getId(),
                    info.getProfile(), true, info.getLatency(), info.getGameMode(), info.getTabListDisplayName(), null));
            }
        }
        gamePackets.add(infoUpdatePacket);

        // Tab list
        PlayerTabOverlay playerTabOverlay = Minecraft.getInstance().gui.getTabList();
        gamePackets.add(new ClientboundTabListPacket(
            playerTabOverlay.header != null ? playerTabOverlay.header : Component.empty(),
            playerTabOverlay.footer != null ? playerTabOverlay.footer : Component.empty()
        ));

        // Boss bar
        BossHealthOverlay bossOverlay = Minecraft.getInstance().gui.getBossOverlay();
        for (LerpingBossEvent event : bossOverlay.events.values()) {
            gamePackets.add(ClientboundBossEventPacket.createAddPacket(event));
        }

        // Scoreboard
        Scoreboard scoreboard = localPlayer.getScoreboard();
        for (PlayerTeam playerTeam : scoreboard.getPlayerTeams()) {
            gamePackets.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true));
        }
        HashSet<Objective> handledObjectives = new HashSet<>();
        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            Objective objective = scoreboard.getDisplayObjective(displaySlot);
            if (objective != null && handledObjectives.add(objective)) {
                gamePackets.add(new ClientboundSetObjectivePacket(objective, 0));

                for (DisplaySlot displaySlot2 : DisplaySlot.values()) {
                    if (scoreboard.getDisplayObjective(displaySlot2) == objective) {
                        gamePackets.add(new ClientboundSetDisplayObjectivePacket(displaySlot2, objective));
                    }
                }

                for (PlayerScoreEntry playerScoreEntry : scoreboard.listPlayerScores(objective)) {
                    gamePackets.add(new ClientboundSetScorePacket(playerScoreEntry.owner(), objective.getName(), playerScoreEntry.value(),
                            Optional.ofNullable(playerScoreEntry.display()), Optional.ofNullable(playerScoreEntry.numberFormatOverride())));
                }
            }
        }

        // Level info
        WorldBorder worldBorder = level.getWorldBorder();
        gamePackets.add(new ClientboundInitializeBorderPacket(worldBorder));
        gamePackets.add(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        gamePackets.add(new ClientboundSetDefaultSpawnPositionPacket(level.getSharedSpawnPos(), level.getSharedSpawnAngle()));
        if (level.isRaining()) {
            gamePackets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0f));
        } else {
            gamePackets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0f));
        }
        gamePackets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1.0f)));
        gamePackets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1.0f)));

        // Chunk data
        if (Runtime.getRuntime().availableProcessors() <= 1) {
            List<ClientboundLevelChunkWithLightPacket> levelChunkPackets = new ArrayList<>();

            for (int i = 0; i < chunks.length(); i++) {
                LevelChunk chunk = chunks.get(i);
                if (chunk != null) {
                    levelChunkPackets.add(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
                }
            }

            int centerX = localPlayer.getBlockX() >> 4;
            int centerZ = localPlayer.getBlockZ() >> 4;
            levelChunkPackets.sort(Comparator.comparingInt(task -> {
                int dx = task.getX() - centerX;
                int dz = task.getZ() - centerZ;
                return dx*dx + dz*dz;
            }));

            gamePackets.addAll(levelChunkPackets);
        } else {
            try (ForkJoinPool pool = new ForkJoinPool()) {
                final class PositionedTask {
                    private final ChunkPos pos;
                    private final ForkJoinTask<ClientboundLevelChunkWithLightPacket> task;
                    private ClientboundLightUpdatePacketData lightData = null;

                    PositionedTask(ChunkPos pos, ForkJoinTask<ClientboundLevelChunkWithLightPacket> task) {
                        this.pos = pos;
                        this.task = task;
                    }
                }
                List<PositionedTask> levelChunkPacketTasks = new ArrayList<>();

                for (int i = 0; i < chunks.length(); i++) {
                    LevelChunk chunk = chunks.get(i);
                    if (chunk != null) {
                        var task = pool.submit(() -> new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), new BitSet(), new BitSet()));
                        levelChunkPacketTasks.add(new PositionedTask(chunk.getPos(), task));
                    }
                }

                int centerX = localPlayer.getBlockX() >> 4;
                int centerZ = localPlayer.getBlockZ() >> 4;
                levelChunkPacketTasks.sort(Comparator.comparingInt(task -> {
                    int dx = task.pos.x - centerX;
                    int dz = task.pos.z - centerZ;
                    return dx*dx + dz*dz;
                }));

                // We get the light data on this thread to avoid
                // slowdown due to synchronization

                for (PositionedTask positionedTask : levelChunkPacketTasks) {
                    positionedTask.lightData = new ClientboundLightUpdatePacketData(positionedTask.pos,
                            level.getLightEngine(), null, null);
                }

                for (PositionedTask positionedTask : levelChunkPacketTasks) {
                    ClientboundLevelChunkWithLightPacket levelChunkWithLightPacket = positionedTask.task.join();
                    levelChunkWithLightPacket.lightData = positionedTask.lightData;
                    gamePackets.add(levelChunkWithLightPacket);
                }
            }
        }

        // Entity data
        for (Entity entity : level.entitiesForRendering()) {
            if (PacketHelper.shouldIgnoreEntity(entity)) {
                continue;
            }

            if (!(entity instanceof LocalPlayer)) {
                gamePackets.add(PacketHelper.createAddEntity(entity));
            }

            List<SynchedEntityData.DataValue<?>> nonDefaultEntityData = entity.getEntityData().getNonDefaultValues();
            if (nonDefaultEntityData != null && !nonDefaultEntityData.isEmpty()) {
                gamePackets.add(new ClientboundSetEntityDataPacket(entity.getId(), nonDefaultEntityData));
            }

            if (entity instanceof LivingEntity livingEntity) {
                Collection<AttributeInstance> syncableAttributes = livingEntity.getAttributes().getSyncableAttributes();
                if (!syncableAttributes.isEmpty()) {
                    gamePackets.add(new ClientboundUpdateAttributesPacket(entity.getId(), syncableAttributes));
                }

                List<Pair<EquipmentSlot, ItemStack>> changedSlots = new ArrayList<>();
                for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                    ItemStack itemStack = livingEntity.getItemBySlot(equipmentSlot);
                    if (!itemStack.isEmpty()) {
                        changedSlots.add(Pair.of(equipmentSlot, itemStack.copy()));
                    }
                }
                if (!changedSlots.isEmpty()) {
                    gamePackets.add(new ClientboundSetEquipmentPacket(entity.getId(), changedSlots));
                }
            }

            if (entity.isVehicle()) {
                gamePackets.add(new ClientboundSetPassengersPacket(entity));
            }
            if (entity.isPassenger()) {
                gamePackets.add(new ClientboundSetPassengersPacket(entity.getVehicle()));
            }

            if (entity instanceof Mob mob && mob.isLeashed()) {
                gamePackets.add(new ClientboundSetEntityLinkPacket(mob, mob.getLeashHolder()));
            }
        }

        this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, gamePackets);

        if (asActualSnapshot) {
            this.asyncReplaySaver.submit(ReplayWriter::endSnapshot);
        }
    }

}
