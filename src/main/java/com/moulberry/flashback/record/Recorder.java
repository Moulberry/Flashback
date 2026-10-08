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
import com.moulberry.flashback.compat.create.CreateSupport;
import com.moulberry.flashback.compat.valkyrienskies.ValkyrienSkiesSupport;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.io.ReplayWriter;
import com.moulberry.flashback.mixin.compat.bobby.FakeChunkManagerAccessor;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.*;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.*;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
    private final Int2ObjectMap<Object> lastPlayerEntityMeta = new Int2ObjectOpenHashMap<>();
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

    public Recorder() {
        this.asyncReplaySaver = new AsyncReplaySaver();

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
            try {
                this.metadata.distantHorizonPaths.putAll(DistantHorizonsSupport.getDimensionPaths());
            } catch (Throwable t) {
                Flashback.LOGGER.warn("Failed to retrieve Distant Horizons paths for recording", t);
            }
        }
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
            Minecraft.getInstance().mouseHandler.turnPlayer();
            // TODO astavie: tick manager
            float nextPartialTick = Minecraft.getInstance().timer.partialTick;

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
                    accurateEntityPosition.write(writer.friendlyByteBuf());
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
            return;
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
                gamePackets.add(new ClientboundSetCarriedItemPacket(selectedSlot));
                this.lastSelectedSlot = selectedSlot;
            }
        }

        // Update entity data
        Int2ObjectMap<SynchedEntityData.DataItem<?>> items = Minecraft.getInstance().player.getEntityData().itemsById;
        List<SynchedEntityData.DataValue<?>> changedData = new ArrayList<>();
        for (var entry : items.int2ObjectEntrySet()) {
            int i = entry.getIntKey();
            SynchedEntityData.DataItem<?> dataItem = entry.getValue();
            Object value = dataItem.value().value();

            if (!this.lastPlayerEntityMeta.containsKey(i)) {
                this.lastPlayerEntityMeta.put(i, value);
            } else {
                Object old = this.lastPlayerEntityMeta.get(i);
                if (!Objects.equals(old, value)) {
                    this.lastPlayerEntityMeta.put(i, value);
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

        if (Flashback.getConfig().recordHotbar) {
            for (int i = 0; i < this.lastHotbarItems.length; i++) {
                ItemStack hotbarItem = player.getInventory().getItem(i);

                if (this.lastHotbarItems[i] == null) {
                    this.lastHotbarItems[i] = hotbarItem.copy();
                } else if (!ItemStack.matches(this.lastHotbarItems[i], hotbarItem)) {
                    ItemStack copied = hotbarItem.copy();
                    this.lastHotbarItems[i] = copied;
                    gamePackets.add(new ClientboundContainerSetSlotPacket(-2, 0, i, copied));

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

        this.asyncReplaySaver.writeGamePackets(gamePackets);
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
                double lerpX = livingEntity.lerpSteps > 0 ? livingEntity.lerpX : livingEntity.getX();
                double lerpY = livingEntity.lerpSteps > 0 ? livingEntity.lerpY : livingEntity.getY();
                double lerpZ = livingEntity.lerpSteps > 0 ? livingEntity.lerpZ : livingEntity.getZ();
                double lerpXRot = livingEntity.lerpSteps > 0 ? livingEntity.lerpXRot : livingEntity.getXRot();
                double lerpYRot = livingEntity.lerpSteps > 0 ? livingEntity.lerpYRot : livingEntity.getYRot();
                double lerpHeadRot = livingEntity.lerpHeadSteps > 0 ? livingEntity.lyHeadRot : livingEntity.getYHeadRot();
                position = new Position(lerpX, lerpY, lerpZ, (float) lerpYRot, (float) lerpXRot, (float) lerpHeadRot, entity.onGround());
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
            FriendlyByteBuf friendlyByteBuf = writer.friendlyByteBuf();

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

        PacketWithPhase packet;
        while ((packet = this.pendingPackets.poll()) != null) {
            if (packet.phase == ConnectionProtocol.PLAY) {
                gamePackets.add(((Packet<? super ClientGamePacketListener>) packet.packet));

                if (packet.packet instanceof ClientboundLoginPacket) {
                    this.asyncReplaySaver.writeGamePackets(gamePackets);
                    gamePackets.clear();

                    this.writeCreateLocalPlayer();
                }

                if (this.isConfiguring) {
                    endedConfiguration = true;
                    this.isConfiguring = false;
                }
            } else {
                throw new IllegalArgumentException("Unsupported phase: " + packet.phase);
            }
        }

        if (!gamePackets.isEmpty()) {
            this.asyncReplaySaver.writeGamePackets(gamePackets);

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
            newProfile.getProperties().putAll(Minecraft.getInstance().getUser().getGameProfile().getProperties());
            newProfile.getProperties().putAll(currentProfile.getProperties());
            int gameModeId = Minecraft.getInstance().gameMode.getPlayerMode().getId();

            this.asyncReplaySaver.submit(writer -> {
                writer.startAction(ActionCreateLocalPlayer.INSTANCE);

                FriendlyByteBuf registryFriendlyByteBuf = writer.friendlyByteBuf();
                registryFriendlyByteBuf.writeUUID(uuid);
                registryFriendlyByteBuf.writeDouble(x);
                registryFriendlyByteBuf.writeDouble(y);
                registryFriendlyByteBuf.writeDouble(z);
                registryFriendlyByteBuf.writeFloat(xRot);
                registryFriendlyByteBuf.writeFloat(yRot);
                registryFriendlyByteBuf.writeFloat(yHeadRot);
                registryFriendlyByteBuf.writeDouble(deltaMovement.x);
                registryFriendlyByteBuf.writeDouble(deltaMovement.y);
                registryFriendlyByteBuf.writeDouble(deltaMovement.z);
                registryFriendlyByteBuf.writeGameProfile(newProfile);
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
                Component decorated = playerChatPacket.chatType().resolve(Minecraft.getInstance().getConnection().registryAccess()).get().decorate(content);
                packet = new ClientboundSystemChatPacket(decorated, false);
            } catch (Exception e) {
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

        // Configuration data

        List<Packet<? super ClientGamePacketListener>> gamePackets = new ArrayList<>();

        // Enabled features
        gamePackets.add(new ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(level.enabledFeatures())));

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
        connection.registryAccess().registries().forEach(entry -> {
            if (serializedTags.containsKey(entry.key())) {
                return;
            }
            if (RegistrySynchronization.NETWORKABLE_REGISTRIES.containsKey(entry.key()) && entry.value().size() > 0) {
                var tags = TagNetworkSerialization.serializeToNetwork(entry.value());
                if (!tags.isEmpty()) {
                    serializedTags.put(entry.key(), tags);
                }
            }
        });

        gamePackets.add(new ClientboundUpdateTagsPacket(serializedTags));

        // Resource packs
        // FIXME astavie: unknown how to get the current downloaded resource pack
//        configurationPackets.add(new ClientboundResourcePackPopPacket(Optional.empty()));
//        for (ServerPackManager.ServerPackData pack : Minecraft.getInstance().getDownloadedPackSource().manager.packs) {
//            configurationPackets.add(new ClientboundResourcePackPushPacket(pack.id, pack.url.toString(), pack.hash == null ? "" : pack.hash.toString(),
//                true, Optional.empty()));
//        }

        // Login packet
        long hashedSeed = level.getBiomeManager().biomeZoomSeed;
        var loginPacket = new ClientboundLoginPacket(localPlayer.getId(), level.getLevelData().isHardcore(), gameMode.getPlayerMode(), gameMode.getPreviousPlayerMode(), connection.levels(),
            connection.registryAccess().freeze(), level.dimensionTypeId(), level.dimension(), hashedSeed, 1, Minecraft.getInstance().options.getEffectiveRenderDistance(), level.getServerSimulationDistance(),
            localPlayer.isReducedDebugInfo(), localPlayer.shouldShowDeathScreen(), level.isDebug(), level.getLevelData().isFlat, Optional.empty(), 0);
        gamePackets.add(loginPacket);

        // Write local player
        this.asyncReplaySaver.writeGamePackets(gamePackets);
        gamePackets.clear();
        this.writeCreateLocalPlayer();

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
        for (int displaySlot = 0; displaySlot < Scoreboard.DISPLAY_SLOTS; displaySlot++) {
            Objective objective = scoreboard.getDisplayObjective(displaySlot);
            if (objective != null && handledObjectives.add(objective)) {
                gamePackets.add(new ClientboundSetObjectivePacket(objective, 0));

                for (int displaySlot2 = 0; displaySlot2 < Scoreboard.DISPLAY_SLOTS; displaySlot2++) {
                    if (scoreboard.getDisplayObjective(displaySlot2) == objective) {
                        gamePackets.add(new ClientboundSetDisplayObjectivePacket(displaySlot2, objective));
                    }
                }

                for (Score playerScoreEntry : scoreboard.getPlayerScores(objective)) {
                    gamePackets.add(new ClientboundSetScorePacket(ServerScoreboard.Method.CHANGE, playerScoreEntry.getOwner(), objective.getName(), playerScoreEntry.getScore()));
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

        // Ships
        this.asyncReplaySaver.writeGamePackets(gamePackets);
        gamePackets.clear();
        if (FabricLoader.getInstance().isModLoaded("valkyrienskies")) {
            ValkyrienSkiesSupport.recordShipData(this.asyncReplaySaver);
        }

        // Chunk data
        if (Runtime.getRuntime().availableProcessors() <= 1) {
            List<ClientboundLevelChunkWithLightPacket> levelChunkPackets = new ArrayList<>();

            AtomicReferenceArray<LevelChunk> chunks = clientChunkCache.storage.chunks;
            for (int i = 0; i < chunks.length(); i++) {
                LevelChunk chunk = chunks.get(i);
                if (chunk != null) {
                    levelChunkPackets.add(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
                }
            }
            if (FabricLoader.getInstance().isModLoaded("valkyrienskies")) {
                ValkyrienSkiesSupport.addShipChunks(clientChunkCache, level, levelChunkPackets);
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
            ForkJoinPool pool = new ForkJoinPool();
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

            AtomicReferenceArray<LevelChunk> chunks = clientChunkCache.storage.chunks;
            for (int i = 0; i < chunks.length(); i++) {
                LevelChunk chunk = chunks.get(i);
                if (chunk != null) {
                    var task = pool.submit(() -> new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), new BitSet(), new BitSet()));
                    levelChunkPacketTasks.add(new PositionedTask(chunk.getPos(), task));
                }
            }
            if (FabricLoader.getInstance().isModLoaded("valkyrienskies")) {
                ValkyrienSkiesSupport.addShipChunkTasks(clientChunkCache, level, pool, chunk -> {
                    var task = pool.submit(() -> new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), new BitSet(), new BitSet()));
                    levelChunkPacketTasks.add(new PositionedTask(chunk.getPos(), task));
                });
            }

            int centerX = localPlayer.getBlockX() >> 4;
            int centerZ = localPlayer.getBlockZ() >> 4;
            levelChunkPacketTasks.sort(Comparator.comparingInt(task -> {
                int dx = task.pos.x - centerX;
                int dz = task.pos.z - centerZ;
                return dx*dx + dz*dz;
            }));

            // Ensure light is up-to-date
            for (int i = 0; i < 1000; i++) {
                if (level.isLightUpdateQueueEmpty()) {
                    break;
                }
                level.pollLightUpdates();
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

        if (Flashback.getConfig().recordHotbar) {
            gamePackets.add(new ClientboundSetExperiencePacket(localPlayer.experienceProgress, localPlayer.totalExperience, localPlayer.experienceLevel));

            FoodData foodData = localPlayer.getFoodData();
            gamePackets.add(new ClientboundSetHealthPacket(localPlayer.getHealth(), foodData.getFoodLevel(), foodData.getSaturationLevel()));

            gamePackets.add(new ClientboundSetCarriedItemPacket(localPlayer.getInventory().selected));

            for (int i = 0; i < 9; i++) {
                ItemStack hotbarItem = localPlayer.getInventory().getItem(i);
                gamePackets.add(new ClientboundContainerSetSlotPacket(-2, 0, i, hotbarItem.copy()));
            }
        }

        // Entity data
        if (FabricLoader.getInstance().isModLoaded("create")) {
            CreateSupport.captureRailwaySnapshot(gamePackets::add);
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (PacketHelper.shouldIgnoreEntity(entity)) {
                continue;
            }

            if (!(entity instanceof LocalPlayer)) {
                gamePackets.add(PacketHelper.createAddEntity(entity));
            }

            if (FabricLoader.getInstance().isModLoaded("porting_lib_entity")) {
                CreateSupport.appendExtraSpawnData(entity, gamePackets);
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

            if (entity instanceof Mob leashable && leashable.isLeashed()) {
                gamePackets.add(new ClientboundSetEntityLinkPacket(entity, leashable.getLeashHolder()));
            }
        }

        if (FabricLoader.getInstance().isModLoaded("create")) {
            CreateSupport.captureCarriageDataResend(gamePackets::add);
        }

        // Map data
        for (Map.Entry<String, MapItemSavedData> entry : level.mapData.entrySet()) {
            int key = Integer.parseInt(entry.getKey().substring(4));
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

            var packet = new ClientboundMapItemDataPacket(key, data.scale, data.locked, decorations, patch);
            gamePackets.add(packet);
        }

        this.asyncReplaySaver.writeGamePackets(gamePackets);

        if (asActualSnapshot) {
            this.asyncReplaySaver.submit(ReplayWriter::endSnapshot);
        }
    }

}
