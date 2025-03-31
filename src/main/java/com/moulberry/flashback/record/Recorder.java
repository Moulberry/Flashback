package com.moulberry.flashback.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.PacketHelper;
import com.moulberry.flashback.action.*;
import com.moulberry.flashback.compat.DistantHorizonsSupport;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.io.ReplayWriter;
import com.moulberry.flashback.mixin.compat.bobby.FakeChunkManagerAccessor;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import io.netty.buffer.ByteBuf;
import net.fabricmc.loader.api.FabricLoader;
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
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.*;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

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
    private WeakReference<LocalPlayer> lastLocalPlayer = null;
    private final List<Object> lastPlayerEntityMeta = new ArrayList<>();
    private final Map<EquipmentSlot, ItemStack> lastPlayerEquipment = new EnumMap<>(EquipmentSlot.class);
    private final ItemStack[] lastHotbarItems = new ItemStack[9];
    private BlockPos lastDestroyPos = null;
    private int lastDestroyProgress = -1;
    private int lastSelectedSlot = -1;
    private float lastExperienceProgress = -1;
    private int lastTotalExperience = -1;
    private int lastExperienceLevel = -1;
    private int lastFoodLevel = -1;
    private float lastSaturationLevel = -1;
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

        if (FabricLoader.getInstance().isModLoaded("bobby")) {
            try {
                String bobbyWorldName = FakeChunkManagerAccessor.getCurrentWorldOrServerName(Minecraft.getInstance().getConnection());
                this.metadata.bobbyWorldName = bobbyWorldName;
            } catch (Throwable t) {}
        }

        if (Flashback.supportsDistantHorizons) {
            this.metadata.distantHorizonPaths.putAll(DistantHorizonsSupport.getDimensionPaths());
        }

        String worldName = null;
        ServerData serverData = Minecraft.getInstance().getCurrentServer();
        if (serverData != null) {
            worldName = serverData.name;
            if (worldName.equalsIgnoreCase(I18n.get("selectServer.defaultName"))) {
                worldName = null;
            }
        } else {
            IntegratedServer integratedServer = Minecraft.getInstance().getSingleplayerServer();
            if (integratedServer != null) {
                worldName = integratedServer.worldData.getLevelName();
                if (worldName.equalsIgnoreCase(I18n.get("selectWorld.newWorld"))) {
                    worldName = null;
                }
            }
        }
        this.metadata.worldName = worldName;
    }

    public boolean readyToWrite() {
        return !this.closeForWriting && !this.needsInitialSnapshot && !this.wasPaused;
    }

    public void putDistantHorizonsPaths(Map<String, File> paths) {
        this.metadata.distantHorizonPaths.putAll(paths);
    }

    public void addMarker(ReplayMarker marker) {
        this.metadata.replayMarkers.put(this.writtenTicks, marker);
    }

    public void submitCustomTask(Consumer<ReplayWriter> consumer) {
        if (!this.readyToWrite()) {
            return;
        }

        this.asyncReplaySaver.submit(consumer);
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

    private PositionAndAngle lastPlayerPositionAndAngle = null;
    private float lastPlayerPositionAndAnglePartialTick;
    private final TreeMap<Float, PositionAndAngle> partialPositions = new TreeMap<>();
    private int trackAccuratePositionCounter = 10;

    public void trackPartialPosition(Entity entity, float partialTick) {
        int localPlayerUpdatesPerSecond = Flashback.getConfig().localPlayerUpdatesPerSecond;
        if (localPlayerUpdatesPerSecond <= 20) {
            return;
        }

        double x = Mth.lerp(partialTick, entity.xo, entity.getX());
        double y = Mth.lerp(partialTick, entity.yo, entity.getY());
        double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
        float yaw = entity.getViewYRot(partialTick);
        float pitch = entity.getViewXRot(partialTick);
        this.partialPositions.put(partialTick, new PositionAndAngle(x, y, z, yaw, pitch));
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

        int localPlayerUpdatesPerSecond = Flashback.getConfig().localPlayerUpdatesPerSecond;
        boolean trackAccurateFirstPersonPosition = localPlayerUpdatesPerSecond > 20;
        boolean wroteNewTick = false;

        if (minecraft.level != null && (minecraft.getOverlay() == null || !minecraft.getOverlay().isPauseScreen()) &&
                !minecraft.isPaused() && !this.isPaused && isLevelLoaded) {
            this.writeEntityPositions();
            this.writeLocalData();

            if (trackAccurateFirstPersonPosition) {
                this.writeAccurateFirstPersonPosition(localPlayerUpdatesPerSecond);
            }

            wroteNewTick = true;
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
        } else if (trackAccurateFirstPersonPosition) {
            this.updateLastPlayerPositionAndAngle(Minecraft.getInstance().player);
            this.partialPositions.clear();
        }

        this.finishedPausing |= this.wasPaused && !this.isPaused;

        boolean writeChunk = close;
        if (minecraft.level != null) {
            boolean finished = this.finishedConfiguration || this.finishedPausing;
            writeChunk |= this.writtenTicksInChunk >= CHUNK_LENGTH_SECONDS*20 || finished || changedDimensions;
        }

        if (writeChunk) {
            // Add an extra tick to avoid edge-cases with 0-length replay chunks
            if (this.writtenTicksInChunk == 0 || !wroteNewTick) {
                this.asyncReplaySaver.submit(writer -> writer.startAndFinishAction(ActionNextTick.INSTANCE));
                this.writtenTicksInChunk += 1;
                this.writtenTicks += 1;
            }

            int chunkId = this.metadata.chunks.size();
            String chunkName = "c" + chunkId + ".flashback";

            if (changedDimensions && Flashback.getConfig().markDimensionChanges) {
                this.addMarker(new ReplayMarker(0xAA00AA, null, "Changed Dimension"));
            }

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

    private void writeAccurateFirstPersonPosition(int localPlayerUpdatesPerSecond) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            this.lastPlayerPositionAndAngle = null;
            this.partialPositions.clear();
            return;
        }

        if (this.lastPlayerPositionAndAngle != null) {
            int divisions = localPlayerUpdatesPerSecond / 20;
            Minecraft.getInstance().mouseHandler.handleAccumulatedMovement();
            float nextPartialTick = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(true);

            double nextX = Mth.lerp(nextPartialTick, player.xo, player.getX());
            double nextY = Mth.lerp(nextPartialTick, player.yo, player.getY());
            double nextZ = Mth.lerp(nextPartialTick, player.zo, player.getZ());
            float nextYaw = player.getViewYRot(nextPartialTick);
            float nextPitch = player.getViewXRot(nextPartialTick);
            PositionAndAngle nextPosition = new PositionAndAngle(nextX, nextY, nextZ, nextYaw, nextPitch);

            if (!this.lastPlayerPositionAndAngle.equals(nextPosition)) {
                this.trackAccuratePositionCounter = 10;
            } else if (this.trackAccuratePositionCounter > 0) {
                this.trackAccuratePositionCounter -= 1;
            }

            if (this.trackAccuratePositionCounter > 0) {
                List<PositionAndAngle> interpolatedPositions = new ArrayList<>();

                for (int i = 0; i <= divisions; i++) {
                    float amount = (float) i / divisions;

                    float floorPartial = -1.0f + this.lastPlayerPositionAndAnglePartialTick;
                    PositionAndAngle floorPosition = this.lastPlayerPositionAndAngle;
                    float ceilPartial = 1.0f + nextPartialTick;
                    PositionAndAngle ceilPosition = nextPosition;

                    Map.Entry<Float, PositionAndAngle> floorEntry = this.partialPositions.floorEntry(amount);
                    Map.Entry<Float, PositionAndAngle> ceilEntry = this.partialPositions.ceilingEntry(Math.nextUp(amount));

                    if (floorEntry != null) {
                        floorPartial = floorEntry.getKey();
                        floorPosition = floorEntry.getValue();
                    }
                    if (ceilEntry != null) {
                        ceilPartial = ceilEntry.getKey();
                        ceilPosition = ceilEntry.getValue();
                    }

                    double lerpAmount = 0.5;
                    if (!Objects.equals(floorPartial, ceilPartial)) {
                        lerpAmount = (amount - floorPartial) / (ceilPartial - floorPartial);
                    }

                    PositionAndAngle interpolatedPosition = floorPosition.lerp(ceilPosition, lerpAmount);
                    interpolatedPositions.add(interpolatedPosition);
                }

                FlashbackAccurateEntityPosition accurateEntityPosition = new FlashbackAccurateEntityPosition(player.getId(), interpolatedPositions);
                this.asyncReplaySaver.submit(writer -> {
                    writer.startAction(ActionAccuratePlayerPosition.INSTANCE);
                    FlashbackAccurateEntityPosition.STREAM_CODEC.encode(writer.friendlyByteBuf(), accurateEntityPosition);
                    writer.finishAction(ActionAccuratePlayerPosition.INSTANCE);
                });
            }
        }

        this.updateLastPlayerPositionAndAngle(player);
        this.partialPositions.clear();
    }

    private void updateLastPlayerPositionAndAngle(@Nullable LocalPlayer player) {
        Map.Entry<Float, PositionAndAngle> floorEntry = this.partialPositions.floorEntry(1.0f);
        if (floorEntry != null) {
            this.lastPlayerPositionAndAngle = floorEntry.getValue();
            this.lastPlayerPositionAndAnglePartialTick = floorEntry.getKey();
        } else if (player != null) {
            double x = player.xo;
            double y = player.yo;
            double z = player.zo;
            float yaw = player.getViewYRot(0.0f);
            float pitch = player.getViewXRot(0.0f);

            this.lastPlayerPositionAndAngle = new PositionAndAngle(x, y, z, yaw, pitch);
            this.lastPlayerPositionAndAnglePartialTick = 1.0f;
        } else {
            this.lastPlayerPositionAndAngle = null;
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
            return;
        }
        // Reset local player data if player object changes
        if (this.lastLocalPlayer == null || this.lastLocalPlayer.get() != player) {
            this.resetLastLocalData();
            this.lastLocalPlayer = new WeakReference<>(player);
        }

        List<Packet<? super ClientGamePacketListener>> gamePackets = new ArrayList<>();

        if (Flashback.getConfig().recordHotbar) {
            if (player.experienceProgress != this.lastExperienceProgress || player.totalExperience != this.lastTotalExperience ||
                    player.experienceLevel != this.lastExperienceLevel) {
                this.lastExperienceProgress = player.experienceProgress;
                this.lastTotalExperience = player.totalExperience;
                this.lastExperienceLevel = player.experienceLevel;
                gamePackets.add(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
            }

            FoodData foodData = player.getFoodData();
            if (foodData.getFoodLevel() != this.lastFoodLevel || foodData.getSaturationLevel() != this.lastSaturationLevel) {
                this.lastFoodLevel = foodData.getFoodLevel();
                this.lastSaturationLevel = foodData.getSaturationLevel();
                gamePackets.add(new ClientboundSetHealthPacket(player.getHealth(), foodData.getFoodLevel(), foodData.getSaturationLevel()));
            }

            int selectedSlot = player.getInventory().selected;
            if (selectedSlot != this.lastSelectedSlot) {
                gamePackets.add(new ClientboundSetHeldSlotPacket(selectedSlot));
                this.lastSelectedSlot = selectedSlot;
            }
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
            gamePackets.add(new ClientboundSetEntityDataPacket(player.getId(), changedData));
        }

        // Update equipment
        List<Pair<EquipmentSlot, ItemStack>> changedSlots = new ArrayList<>();
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            ItemStack itemStack = player.getItemBySlot(equipmentSlot);

            if (!this.lastPlayerEquipment.containsKey(equipmentSlot) || !ItemStack.matches(this.lastPlayerEquipment.get(equipmentSlot), itemStack)) {
                ItemStack copied = itemStack.copy();
                this.lastPlayerEquipment.put(equipmentSlot, copied);
                changedSlots.add(Pair.of(equipmentSlot, copied));
            }
        }
        if (!changedSlots.isEmpty()) {
            gamePackets.add(new ClientboundSetEquipmentPacket(player.getId(), changedSlots));
        }

        if (Flashback.getConfig().recordHotbar) {
            for (int i = 0; i < this.lastHotbarItems.length; i++) {
                ItemStack hotbarItem = player.getInventory().getItem(i);

                if (this.lastHotbarItems[i] == null || !ItemStack.matches(this.lastHotbarItems[i], hotbarItem)) {
                    ItemStack copied = hotbarItem.copy();
                    this.lastHotbarItems[i] = copied;
                    gamePackets.add(new ClientboundContainerSetSlotPacket(0, 0, i, copied));
                }
            }
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

        gamePackets.add(new ClientboundSetEntityMotionPacket(player.getId(), player.getDeltaMovement()));

        this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, gamePackets);
    }

    private void resetLastLocalData() {
        this.lastPlayerEntityMeta.clear();
        this.lastPlayerEquipment.clear();
        this.lastDestroyPos = null;
        this.lastDestroyProgress = -1;
        this.lastSelectedSlot = -1;
        this.lastExperienceProgress = -1;
        this.lastTotalExperience = -1;
        this.lastExperienceLevel = -1;
        this.lastFoodLevel = -1;
        this.lastSaturationLevel = -1;
        Arrays.fill(this.lastHotbarItems, null);
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
            } else if (entity instanceof Display display) {
                position = new Position(display.lerpTargetX(), display.lerpTargetY(), display.lerpTargetZ(),
                    display.lerpTargetYRot(), display.lerpTargetXRot(), display.getYHeadRot(), entity.onGround());
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

        if (changedPositions.isEmpty()) {
            return;
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

                if (packet.packet instanceof ClientboundLoginPacket) {
                    this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, gamePackets);
                    gamePackets.clear();

                    this.writeCreateLocalPlayer();
                }

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

    private void writeCreateLocalPlayer() {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            UUID uuid = localPlayer.getUUID();
            double x = localPlayer.getX();
            double y = localPlayer.getY();
            double z = localPlayer.getZ();
            float xRot = localPlayer.getXRot();
            float yRot = localPlayer.getYRot();
            float yHeadRot = localPlayer.getYHeadRot();
            Vec3 deltaMovement = localPlayer.getDeltaMovement();

            GameProfile currentProfile = localPlayer.getGameProfile();
            GameProfile newProfile = new GameProfile(currentProfile.getId(), currentProfile.getName());
            newProfile.getProperties().putAll(Minecraft.getInstance().getGameProfile().getProperties());
            newProfile.getProperties().putAll(currentProfile.getProperties());
            int gameModeId = Minecraft.getInstance().gameMode.getPlayerMode().getId();

            this.asyncReplaySaver.submit(writer -> {
                writer.startAction(ActionCreateLocalPlayer.INSTANCE);

                RegistryFriendlyByteBuf registryFriendlyByteBuf = writer.friendlyByteBuf();
                registryFriendlyByteBuf.writeUUID(uuid);
                registryFriendlyByteBuf.writeDouble(x);
                registryFriendlyByteBuf.writeDouble(y);
                registryFriendlyByteBuf.writeDouble(z);
                registryFriendlyByteBuf.writeFloat(xRot);
                registryFriendlyByteBuf.writeFloat(yRot);
                registryFriendlyByteBuf.writeFloat(yHeadRot);
                registryFriendlyByteBuf.writeVec3(deltaMovement);
                ByteBufCodecs.GAME_PROFILE.encode(registryFriendlyByteBuf, newProfile);
                registryFriendlyByteBuf.writeVarInt(gameModeId);

                writer.finishAction(ActionCreateLocalPlayer.INSTANCE);
            });
        }
    }

    public void writeLevelEvent(int type, BlockPos blockPos, int data, boolean globalEvent) {
        if (!this.readyToWrite()) {
            return;
        }

        this.pendingPackets.add(new PacketWithPhase(new ClientboundLevelEventPacket(type, blockPos, data, globalEvent), ConnectionProtocol.PLAY));
    }

    public void writeSound(Holder<SoundEvent> holder, SoundSource soundSource, double x, double y, double z, float volume, float pitch, long seed) {
        if (!this.readyToWrite()) {
            return;
        }

        this.pendingPackets.add(new PacketWithPhase(new ClientboundSoundPacket(holder, soundSource, x, y, z, volume, pitch, seed), ConnectionProtocol.PLAY));
    }

    public void writeEntitySound(Holder<SoundEvent> holder, SoundSource soundSource, Entity entity, float volume, float pitch, long seed) {
        if (!this.readyToWrite()) {
            return;
        }

        this.pendingPackets.add(new PacketWithPhase(new ClientboundSoundEntityPacket(holder, soundSource, entity, volume, pitch, seed), ConnectionProtocol.PLAY));
    }

    public void writePacketAsync(Packet<?> packet, ConnectionProtocol phase) {
        if (!this.readyToWrite()) {
            return;
        }

        if (packet instanceof ClientboundBundlePacket bundlePacket) {
            for (Packet<? super ClientGamePacketListener> subPacket : bundlePacket.subPackets()) {
                this.writePacketAsync(subPacket, phase);
            }
            return;
        }

        // Convert player chat packets into system chat packets
        if (packet instanceof ClientboundPlayerChatPacket playerChatPacket) {
            try {
                Component content = playerChatPacket.unsignedContent() != null ? playerChatPacket.unsignedContent() : Component.literal(playerChatPacket.body().content());
                Component decorated = playerChatPacket.chatType().decorate(content);
                packet = new ClientboundSystemChatPacket(decorated, false);
            } catch (Exception e) {
                return;
            }
        }

        // Don't save fabric-screen-handler-api packets
        if (packet instanceof ClientboundCustomPayloadPacket customPayloadPacket) {
            if (customPayloadPacket.type().id().getNamespace().startsWith("fabric-screen-handler-api")) {
                return;
            }
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
                if (!tags.isEmpty()) {
                    serializedTags.put(entry.key(), tags);
                }
            }
        });
        localPlayer.registryAccess().registries().forEach(entry -> {
            if (serializedTags.containsKey(entry.key())) {
                return;
            }
            if (RegistrySynchronization.isNetworkable(entry.key()) && entry.value().size() > 0) {
                var tags = TagNetworkSerialization.serializeToNetwork(entry.value());
                if (!tags.isEmpty()) {
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
        long hashedSeed = level.getBiomeManager().biomeZoomSeed;
        CommonPlayerSpawnInfo commonPlayerSpawnInfo = new CommonPlayerSpawnInfo(level.dimensionTypeRegistration(), level.dimension(), hashedSeed,
            gameMode.getPlayerMode(), gameMode.getPreviousPlayerMode(), level.isDebug(), level.getLevelData().isFlat, Optional.empty(), 0,
                level.getSeaLevel());
        var loginPacket = new ClientboundLoginPacket(localPlayer.getId(), level.getLevelData().isHardcore(), connection.levels(),
            1, Minecraft.getInstance().options.getEffectiveRenderDistance(), level.getServerSimulationDistance(),
            localPlayer.isReducedDebugInfo(), localPlayer.shouldShowDeathScreen(), localPlayer.getDoLimitedCrafting(), commonPlayerSpawnInfo, false);
        gamePackets.add(loginPacket);

        // Write local player
        this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, gamePackets);
        gamePackets.clear();
        this.writeCreateLocalPlayer();

        // Create player info update packet
        var infoUpdatePacket = new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of());
        infoUpdatePacket.entries = new ArrayList<>();
        Set<UUID> addedEntries = new HashSet<>();
        Set<UUID> addedWithValidProperties = new HashSet<>();
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            boolean isValidProperties = !info.getProfile().getProperties().isEmpty();
            if (addedEntries.add(info.getProfile().getId())) {
                infoUpdatePacket.entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(info.getProfile().getId(),
                    info.getProfile(), true, info.getLatency(), info.getGameMode(), info.getTabListDisplayName(), info.showHat(), info.getTabListOrder(), null));
                if (isValidProperties) {
                    addedWithValidProperties.add(info.getProfile().getId());
                }
            }
        }
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            boolean isValidProperties = !info.getProfile().getProperties().isEmpty();
            boolean add = false;
            if (addedEntries.add(info.getProfile().getId())) {
                add = true;
            } else if (isValidProperties && !addedWithValidProperties.contains(info.getProfile().getId())) {
                infoUpdatePacket.entries.removeIf(entry -> entry.profileId().equals(info.getProfile().getId()));
                add = true;
            }
            if (add) {
                infoUpdatePacket.entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(info.getProfile().getId(),
                    info.getProfile(), false, info.getLatency(), info.getGameMode(), info.getTabListDisplayName(), info.showHat(), info.getTabListOrder(), null));
                if (isValidProperties) {
                    addedWithValidProperties.add(info.getProfile().getId());
                }
            }
        }
        for (AbstractClientPlayer player : level.players()) {
            PlayerInfo info = player.getPlayerInfo();
            if (info != null) {
                boolean isValidProperties = !info.getProfile().getProperties().isEmpty();
                boolean add = false;
                if (addedEntries.add(info.getProfile().getId())) {
                    add = true;
                } else if (isValidProperties && !addedWithValidProperties.contains(info.getProfile().getId())) {
                    infoUpdatePacket.entries.removeIf(entry -> entry.profileId().equals(info.getProfile().getId()));
                    add = true;
                }
                if (add) {
                    infoUpdatePacket.entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(player.getUUID(),
                        player.getGameProfile(), false, info.getLatency(), info.getGameMode(), info.getTabListDisplayName(), info.showHat(), info.getTabListOrder(), null));
                    if (isValidProperties) {
                        addedWithValidProperties.add(info.getProfile().getId());
                    }
                }
            } else if (addedEntries.add(player.getUUID())) {
                infoUpdatePacket.entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(player.getUUID(),
                    player.getGameProfile(), false, 0, GameType.DEFAULT_MODE, player.getDisplayName(), true, 0, null));
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
        gamePackets.add(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), level.tickDayTime));
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

                // Ensure light is up-to-date
                while (true) {
                    Runnable runnable = level.lightUpdateQueue.poll();
                    if (runnable == null) {
                        break;
                    } else {
                        runnable.run();
                    }
                }

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

        if (Flashback.getConfig().recordHotbar) {
            this.lastExperienceProgress = localPlayer.experienceProgress;
            this.lastTotalExperience = localPlayer.totalExperience;
            this.lastExperienceLevel = localPlayer.experienceLevel;
            gamePackets.add(new ClientboundSetExperiencePacket(localPlayer.experienceProgress, localPlayer.totalExperience, localPlayer.experienceLevel));

            FoodData foodData = localPlayer.getFoodData();
            this.lastFoodLevel = foodData.getFoodLevel();
            this.lastSaturationLevel = foodData.getSaturationLevel();
            gamePackets.add(new ClientboundSetHealthPacket(localPlayer.getHealth(), foodData.getFoodLevel(), foodData.getSaturationLevel()));

            int selectedSlot = localPlayer.getInventory().selected;
            this.lastSelectedSlot = selectedSlot;
            gamePackets.add(new ClientboundSetHeldSlotPacket(selectedSlot));

            for (int i = 0; i < 9; i++) {
                ItemStack hotbarItem = localPlayer.getInventory().getItem(i);
                this.lastHotbarItems[i] = hotbarItem.copy();
                gamePackets.add(new ClientboundContainerSetSlotPacket(0, 0, i, hotbarItem.copy()));
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

            if (entity instanceof Leashable leashable && leashable.isLeashed()) {
                gamePackets.add(new ClientboundSetEntityLinkPacket(entity, leashable.getLeashHolder()));
            }
        }

        // Map data
        for (Map.Entry<MapId, MapItemSavedData> entry : level.mapData.entrySet()) {
            MapItemSavedData data = entry.getValue();

            int offsetX = 0;
            int offsetY = 0;
            int sizeX = 128;
            int sizeY = 128;

            if (data.colors.length != sizeX * sizeY) {
                Flashback.LOGGER.error("Unable to save snapshot of map data, expected colour array to be size {}, got {} instead", sizeX * sizeY, data.colors.length);
                continue;
            }

            byte[] colorsCopy = new byte[sizeX * sizeY];
            System.arraycopy(data.colors, 0, colorsCopy, 0, sizeX * sizeY);
            MapItemSavedData.MapPatch patch = new MapItemSavedData.MapPatch(offsetX, offsetY, sizeX, sizeY, colorsCopy);

            ArrayList<MapDecoration> decorations = new ArrayList<>();
            for (MapDecoration decoration : data.getDecorations()) {
                decorations.add(decoration);
            }

            var packet = new ClientboundMapItemDataPacket(entry.getKey(), data.scale, data.locked, decorations, patch);
            gamePackets.add(packet);
        }

        this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, gamePackets);

        if (asActualSnapshot) {
            this.asyncReplaySaver.submit(ReplayWriter::endSnapshot);
        }
    }

}
