package com.moulberry.flashback.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.datafixers.util.Pair;
import com.moulberry.flashback.FlashbackClient;
import com.moulberry.flashback.action.ActionConfigurationPacket;
import com.moulberry.flashback.action.ActionCreateLocalPlayer;
import com.moulberry.flashback.action.ActionGamePacket;
import com.moulberry.flashback.action.ActionMoveEntities;
import com.moulberry.flashback.action.ActionNextTick;
import com.moulberry.flashback.io.ReplayWriter;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.GameProtocols;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.LevelChunk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

public class Recorder {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CHUNK_LENGTH_SECONDS = 15;

    private final ReplayWriter writer;
    private final StreamCodec<ByteBuf, Packet<? super ClientConfigurationPacketListener>> configurationPacketCodec;
    private StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> gamePacketCodec;

    private int writtenTicksInChunk = 0;
    private FlashbackMeta metadata = new FlashbackMeta();
    private final UUID recordUUID = UUID.randomUUID();
    private ReentrantLock fileDataLock = new ReentrantLock();

    private record PacketWithPhase(Packet<?> packet, ConnectionProtocol phase){}
    private final Queue<PacketWithPhase> pendingPackets = new ConcurrentLinkedQueue<>();

    private record Position(double x, double y, double z, float yaw, float pitch, float headYRot, boolean onGround) {}
    private final WeakHashMap<Entity, Position> lastPositions = new WeakHashMap<>();

    // Local player data
    private final List<Object> lastPlayerEntityMeta = new ArrayList<>();
    private final Map<EquipmentSlot, ItemStack> lastPlayerEquipment = new EnumMap<>(EquipmentSlot.class);
    private BlockPos lastDestroyPos = null;
    private int lastDestroyProgress = -1;
    private boolean wasSwinging = false;
    private int lastSwingTime = -1;

    public Recorder(RegistryAccess registryAccess) {
        this.writer = new ReplayWriter(registryAccess);
        this.configurationPacketCodec = ConfigurationProtocols.CLIENTBOUND.codec();
        this.gamePacketCodec = GameProtocols.CLIENTBOUND.bind(RegistryFriendlyByteBuf.decorator(registryAccess)).codec();
    }

    public void setRegistryAccess(RegistryAccess registryAccess) {
        this.writer.setRegistryAccess(registryAccess);
        this.gamePacketCodec = GameProtocols.CLIENTBOUND.bind(RegistryFriendlyByteBuf.decorator(registryAccess)).codec();
    }

    public void writeInitial() {
        this.writeSnapshot();
    }

    public void endTick(boolean forceSaveToDisk) {
        this.flushPackets();

        if (Minecraft.getInstance().level != null && Minecraft.getInstance().getOverlay() == null && !Minecraft.getInstance().isPaused()) {
            this.writeEntityPositions();
            this.writeLocalData();
            this.writer.startAndFinishAction(ActionNextTick.INSTANCE);
            this.writtenTicksInChunk += 1;
        }

        if (this.writtenTicksInChunk >= CHUNK_LENGTH_SECONDS*20 || forceSaveToDisk) {
            this.fileDataLock.lock();
            try {
                Path replayRecordFolder = FlashbackClient.getReplayRecordTemp(recordUUID);
                Files.createDirectories(replayRecordFolder);

                int chunkCount = this.metadata.chunks.size();
                String chunkName = "c" + chunkCount + ".flashback";

                Path chunkFile = replayRecordFolder.resolve(chunkName);
                Files.write(chunkFile, this.writer.popBytes());

                Path metaFile = replayRecordFolder.resolve("metadata.json");
                if (Files.exists(metaFile)) {
                    Files.move(metaFile, replayRecordFolder.resolve("metadata.json.old"), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                }

                var chunkMeta = new FlashbackChunkMeta();
                chunkMeta.duration = this.writtenTicksInChunk;
                this.metadata.chunks.put(chunkName, chunkMeta);
                Files.writeString(metaFile, GSON.toJson(this.metadata.toJson()));

                this.writtenTicksInChunk = 0;

                if (!forceSaveToDisk) {
                    this.writeSnapshot();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                this.fileDataLock.unlock();
            }
        }
    }

    public void export(Path outputFile) {
        this.fileDataLock.lock();
        try {
            Path replayRecordFolder = FlashbackClient.getReplayRecordTemp(this.recordUUID);
            Exporter.export(replayRecordFolder, outputFile);
        } finally {
            this.fileDataLock.unlock();
        }
    }

    public void writeLocalData() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            this.lastPlayerEntityMeta.clear();
            this.lastPlayerEquipment.clear();
            this.lastDestroyPos = null;
            this.lastDestroyProgress = -1;
            return;
        }

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
            this.writeGamePacket(new ClientboundSetEntityDataPacket(player.getId(), changedData));
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
            this.writeGamePacket(new ClientboundSetEquipmentPacket(player.getId(), changedSlots));
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
                this.writeGamePacket(new ClientboundBlockDestructionPacket(player.getId(), destroyPos, destroyProgress));
            }

            this.lastDestroyPos = destroyPos;
            this.lastDestroyProgress = destroyProgress;
        }

        // Update swinging
        if (player.swinging && (!this.wasSwinging || this.lastSwingTime > player.swingTime)) {
            int animation = player.swingingArm == InteractionHand.MAIN_HAND ? ClientboundAnimatePacket.SWING_MAIN_HAND : ClientboundAnimatePacket.SWING_OFF_HAND;
            this.writeGamePacket(new ClientboundAnimatePacket(player, animation));
        }
        this.lastSwingTime = player.swingTime;
        this.wasSwinging = player.swinging;

    }

    public void writeEntityPositions() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            this.lastPositions.clear();
            return;
        }

        this.writer.startAction(ActionMoveEntities.INSTANCE);
        RegistryFriendlyByteBuf friendlyByteBuf = this.writer.friendlyByteBuf();

        friendlyByteBuf.writeVarInt(1);
        friendlyByteBuf.writeResourceKey(level.dimension());

        record IdWithPosition(int id, Position position) {}
        List<IdWithPosition> changedPositions = new ArrayList<>();

        for (Entity entity : level.entitiesForRendering()) {
            if (entity.isPassenger()) {
                continue;
            }

            Position position;
            if (entity instanceof LivingEntity livingEntity) {
                double lerpHeadRot = livingEntity.lerpHeadSteps > 0 ? livingEntity.lerpYHeadRot : livingEntity.getYHeadRot();
                position = new Position(livingEntity.lerpTargetX(), entity.lerpTargetY(), entity.lerpTargetZ(),
                    entity.lerpTargetYRot(), entity.lerpTargetXRot(), (float) lerpHeadRot, entity.onGround());
            } else {
                position = new Position(entity.getX(), entity.getY(), entity.getZ(),
                    entity.getYRot(), entity.getXRot(), entity.getYHeadRot(), entity.onGround());
            }
            Position lastPosition = this.lastPositions.get(entity);

            if (!Objects.equals(position, lastPosition)) {
                this.lastPositions.put(entity, position);
                changedPositions.add(new IdWithPosition(entity.getId(), position));
            }
        }

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

        this.writer.finishAction(ActionMoveEntities.INSTANCE);
    }

    public void flushPackets() {
        PacketWithPhase packet;
        while ((packet = this.pendingPackets.poll()) != null) {
            if (packet.phase == ConnectionProtocol.PLAY) {
                this.writeGamePacket((Packet<? super ClientGamePacketListener>) packet.packet);
                if (packet.packet instanceof ClientboundLoginPacket) {
                    LocalPlayer localPlayer = Minecraft.getInstance().player;
                    if (localPlayer == null) {
                        throw new IllegalStateException("Local player doesn't exist?");
                    }

                    this.writer.startAction(ActionCreateLocalPlayer.INSTANCE);
                    RegistryFriendlyByteBuf registryFriendlyByteBuf = this.writer.friendlyByteBuf();
                    registryFriendlyByteBuf.writeUUID(localPlayer.getUUID());
                    registryFriendlyByteBuf.writeDouble(localPlayer.getX());
                    registryFriendlyByteBuf.writeDouble(localPlayer.getY());
                    registryFriendlyByteBuf.writeDouble(localPlayer.getZ());
                    registryFriendlyByteBuf.writeFloat(localPlayer.getXRot());
                    registryFriendlyByteBuf.writeFloat(localPlayer.getYRot());
                    registryFriendlyByteBuf.writeFloat(localPlayer.getYHeadRot());
                    registryFriendlyByteBuf.writeVec3(localPlayer.getDeltaMovement());

                    ByteBufCodecs.GAME_PROFILE.encode(registryFriendlyByteBuf, localPlayer.getGameProfile());

                    registryFriendlyByteBuf.writeVarInt(Minecraft.getInstance().gameMode.getPlayerMode().getId());

                    this.writer.finishAction(ActionCreateLocalPlayer.INSTANCE);
                }
            } else if (packet.phase == ConnectionProtocol.CONFIGURATION) {
                this.writeConfigurationPacket((Packet<? super ClientConfigurationPacketListener>) packet.packet);
            } else {
                throw new IllegalArgumentException("Unsupported phase: " + packet.phase);
            }
        }
    }

    public void writePacketAsync(Packet<?> packet, ConnectionProtocol phase) {
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

    public void writeGamePacket(Packet<? super ClientGamePacketListener> packet) {
        this.writer.startAction(ActionGamePacket.INSTANCE);
        this.gamePacketCodec.encode(this.writer.friendlyByteBuf(), packet);
        this.writer.finishAction(ActionGamePacket.INSTANCE);
    }

    public void writeConfigurationPacket(Packet<? super ClientConfigurationPacketListener> packet) {
        this.writer.startAction(ActionConfigurationPacket.INSTANCE);
        this.configurationPacketCodec.encode(this.writer.friendlyByteBuf(), packet);
        this.writer.finishAction(ActionConfigurationPacket.INSTANCE);
    }

    public void writeSnapshot() {
        this.writer.startSnapshot();

        ClientLevel level = Minecraft.getInstance().level;
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        ClientChunkCache clientChunkCache = level.getChunkSource();

        AtomicReferenceArray<LevelChunk> chunks = clientChunkCache.storage.chunks;

        // Configuration data

        // Enabled features
        this.writeConfigurationPacket(new ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(level.enabledFeatures())));

        // Registry data
        RegistryOps<Tag> dynamicOps = localPlayer.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        RegistrySynchronization.packRegistries(dynamicOps, localPlayer.registryAccess(), Set.of(), (resourceKey, list) -> {
            this.writeConfigurationPacket(new ClientboundRegistryDataPacket(resourceKey, list));
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

        this.writeConfigurationPacket(new ClientboundUpdateTagsPacket(serializedTags));

        // Resource packs
        this.writeConfigurationPacket(new ClientboundResourcePackPopPacket(Optional.empty()));
        for (ServerPackManager.ServerPackData pack : Minecraft.getInstance().getDownloadedPackSource().manager.packs) {
            this.writeConfigurationPacket(new ClientboundResourcePackPushPacket(pack.id, pack.url.toString(), pack.hash == null ? "" : pack.hash.toString(),
                true, Optional.empty()));
        }

        // Login packet
        CommonPlayerSpawnInfo commonPlayerSpawnInfo = new CommonPlayerSpawnInfo(level.dimensionTypeRegistration(), level.dimension(), 0,
            gameMode.getPlayerMode(), gameMode.getPreviousPlayerMode(), level.isDebug(), false, Optional.empty(), 0);
        var loginPacket = new ClientboundLoginPacket(localPlayer.getId(), level.getLevelData().isHardcore(), connection.levels(),
            1, Minecraft.getInstance().options.getEffectiveRenderDistance(), level.getServerSimulationDistance(),
            localPlayer.isReducedDebugInfo(), localPlayer.shouldShowDeathScreen(), localPlayer.getDoLimitedCrafting(), commonPlayerSpawnInfo, false);
        this.writeGamePacket(loginPacket);

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
        this.writeGamePacket(infoUpdatePacket);

        // Tab list
        PlayerTabOverlay playerTabOverlay = Minecraft.getInstance().gui.getTabList();
        this.writeGamePacket(new ClientboundTabListPacket(
            playerTabOverlay.header != null ? playerTabOverlay.header : Component.empty(),
            playerTabOverlay.footer != null ? playerTabOverlay.footer : Component.empty()
        ));

        // Boss bar
        BossHealthOverlay bossOverlay = Minecraft.getInstance().gui.getBossOverlay();
        for (LerpingBossEvent event : bossOverlay.events.values()) {
            this.writeGamePacket(ClientboundBossEventPacket.createAddPacket(event));
        }

        // Time
        this.writeGamePacket(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));

        // Chunk data
        for (int i = 0; i < chunks.length(); i++) {
            LevelChunk chunk = chunks.get(i);
            if (chunk != null) {
                this.writeGamePacket(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
            }
        }

        // Entity data
        for (Entity entity : level.entitiesForRendering()) {
            this.writeGamePacket(new ClientboundAddEntityPacket(entity));

            List<SynchedEntityData.DataValue<?>> nonDefaultEntityData = entity.getEntityData().getNonDefaultValues();
            if (nonDefaultEntityData != null && !nonDefaultEntityData.isEmpty()) {
                this.writeGamePacket(new ClientboundSetEntityDataPacket(entity.getId(), nonDefaultEntityData));
            }

            if (entity instanceof LivingEntity livingEntity) {
                Collection<AttributeInstance> syncableAttributes = livingEntity.getAttributes().getSyncableAttributes();
                if (!syncableAttributes.isEmpty()) {
                    this.writeGamePacket(new ClientboundUpdateAttributesPacket(entity.getId(), syncableAttributes));
                }

                List<Pair<EquipmentSlot, ItemStack>> changedSlots = new ArrayList<>();
                for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                    ItemStack itemStack = livingEntity.getItemBySlot(equipmentSlot);
                    if (!itemStack.isEmpty()) {
                        changedSlots.add(Pair.of(equipmentSlot, itemStack.copy()));
                    }
                }
                if (!changedSlots.isEmpty()) {
                    this.writeGamePacket(new ClientboundSetEquipmentPacket(entity.getId(), changedSlots));
                }
            }

            if (entity.isVehicle()) {
                this.writeGamePacket(new ClientboundSetPassengersPacket(entity));
            }
            if (entity.isPassenger()) {
                this.writeGamePacket(new ClientboundSetPassengersPacket(entity.getVehicle()));
            }

            if (entity instanceof Mob mob && mob.isLeashed()) {
                this.writeGamePacket(new ClientboundSetEntityLinkPacket(mob, mob.getLeashHolder()));
            }
        }

        this.writer.endSnapshot();
    }

}
