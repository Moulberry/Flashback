package com.moulberry.flashback.playback;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.PacketHelper;
import com.moulberry.flashback.exception.UnsupportedPacketException;
import com.moulberry.flashback.ext.ThreadedLevelLightEngineExt;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ReplayGamePacketHandler implements ClientGamePacketListener {

    private final ReplayServer replayServer;
    private final Map<UUID, PlayerInfo> playerInfoMap = new HashMap<>();
    private Int2ObjectMap<Entity> pendingEntities = new Int2ObjectOpenHashMap<>();
    private ResourceKey<Level> currentDimension = null;
    public int localPlayerId = -1;

    public ReplayGamePacketHandler(ReplayServer replayServer) {
        this.replayServer = replayServer;
    }

    private void forward(Packet<?> packet) {
        for (ServerPlayer replayViewer : this.replayServer.getReplayViewers()) {
            replayViewer.connection.send(packet);
        }
    }

    private void forward(Entity entity, Packet<?> packet) {
        if (entity == null) {
            return;
        }

        ServerChunkCache serverChunkCache = this.level().getChunkSource();
        serverChunkCache.broadcast(entity, packet);
    }

    public void flushPendingEntities() {
        ServerLevel level = this.level();
        for (Entity pendingEntity : this.pendingEntities.values()) {
            Entity existingEntity = level.getEntity(pendingEntity.getId());
            if (existingEntity != null) {
                if (existingEntity instanceof ServerPlayer existingPlayer) {
                    existingPlayer.connection.disconnect(Component.empty());
                } else if (existingEntity.getType().equals(pendingEntity.getType())) {
                    existingEntity.restoreFrom(pendingEntity);
                    existingEntity.setPos(pendingEntity.getX(), pendingEntity.getY(), pendingEntity.getZ());
                    existingEntity.setXRot(pendingEntity.getXRot());
                    existingEntity.setYRot(pendingEntity.getYRot());
                    existingEntity.setYHeadRot(pendingEntity.getYHeadRot());
                    if (pendingEntity instanceof LivingEntity pendingLiving) {
                        existingEntity.setYBodyRot(pendingLiving.yBodyRot);
                    }
                    for (SynchedEntityData.DataItem<?> dataItem : pendingEntity.getEntityData().itemsById) {
                        existingEntity.getEntityData().set((EntityDataAccessor) dataItem.getAccessor(), dataItem.getValue());
                    }
                    continue;
                } else {
                    existingEntity.discard();
                }
            }

            level.addFreshEntity(pendingEntity);
            ChunkPos chunkPos = new ChunkPos(pendingEntity.blockPosition());
            level.getChunkSource().addRegionTicket(ReplayServer.ENTITY_LOAD_TICKET, chunkPos, 3, chunkPos);
        }
        this.pendingEntities.clear();
    }

    private Entity getEntityOrPending(int entityId) {
        if (this.pendingEntities.containsKey(entityId)) {
            return this.pendingEntities.get(entityId);
        }
        this.flushPendingEntities();
        return this.level().getEntity(entityId);
    }

    public ServerLevel level() {
        return this.replayServer.getLevel(this.currentDimension);
    }

    @Override
    public void handleAddEntity(ClientboundAddEntityPacket clientboundAddEntityPacket) {
        if (Entity.ENTITY_COUNTER.get() <= clientboundAddEntityPacket.getId()) {
            Entity.ENTITY_COUNTER.set(clientboundAddEntityPacket.getId() + 1);
        }

        Entity entity = this.createEntityFromPacket(clientboundAddEntityPacket);
        if (entity != null) {
            if (entity instanceof Painting) {
                Direction direction = Direction.from3DDataValue(clientboundAddEntityPacket.getData());
                if (!direction.getAxis().isHorizontal()) {
                    clientboundAddEntityPacket.data = Direction.NORTH.get3DDataValue();
                }
            }
            entity.recreateFromPacket(clientboundAddEntityPacket);
            this.pendingEntities.put(clientboundAddEntityPacket.getId(), entity);
        }
    }

    private void spawnPlayer(ClientboundAddEntityPacket addEntityPacket, GameProfile gameProfile, GameType gameType) {
        this.flushPendingEntities();

        CommonListenerCookie commonListenerCookie = CommonListenerCookie.createInitial(gameProfile, false);
        ServerPlayer serverPlayer = new ServerPlayer(this.replayServer, this.level(), commonListenerCookie.gameProfile(), commonListenerCookie.clientInformation()){
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };

        ServerPlayer existingPlayer = this.replayServer.getPlayerList().getPlayer(serverPlayer.getUUID());
        if (existingPlayer != null) {
            existingPlayer.connection.disconnect(Component.empty());
        }

        Entity existingEntity = this.level().getEntity(serverPlayer.getId());
        if (existingEntity != null) {
            if (existingEntity instanceof ServerPlayer existingPlayer2) {
                existingPlayer2.connection.disconnect(Component.empty());
            } else {
                existingEntity.discard();
            }
        }

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(connection);
        serverPlayer.recreateFromPacket(addEntityPacket);
        this.replayServer.getPlayerList().placeNewPlayer(connection, serverPlayer, commonListenerCookie);
        serverPlayer.setGameMode(gameType);
    }

    @Nullable
    private Entity createEntityFromPacket(ClientboundAddEntityPacket packet) {
        EntityType<?> entityType = packet.getType();
        if (entityType == EntityType.PLAYER) {
            PlayerInfo playerInfo = this.playerInfoMap.get(packet.getUUID());
            if (playerInfo == null) {
                return null;
            }

            this.spawnPlayer(packet, playerInfo.getProfile(), playerInfo.getGameMode());
            return null;
        } else {
            return entityType.create(this.level());
        }
    }

    @Override
    public void handleAddExperienceOrb(ClientboundAddExperienceOrbPacket clientboundAddExperienceOrbPacket) {
        double x = clientboundAddExperienceOrbPacket.getX();
        double y = clientboundAddExperienceOrbPacket.getY();
        double z = clientboundAddExperienceOrbPacket.getZ();
        ExperienceOrb entity = new ExperienceOrb(this.level(), x, y, z, clientboundAddExperienceOrbPacket.getValue());
        entity.syncPacketPositionCodec(x, y, z);
        entity.setYRot(0.0f);
        entity.setXRot(0.0f);
        entity.setId(clientboundAddExperienceOrbPacket.getId());

        Entity existingEntity = this.level().getEntity(clientboundAddExperienceOrbPacket.getId());
        if (existingEntity instanceof ExperienceOrb) {
            existingEntity.restoreFrom(entity);
            return;
        } else if (existingEntity != null) {
            existingEntity.discard();
        }

        this.level().addFreshEntity(entity);
    }

    @Override
    public void handleAddObjective(ClientboundSetObjectivePacket clientboundSetObjectivePacket) {
        forward(clientboundSetObjectivePacket);
    }

    @Override
    public void handleAnimate(ClientboundAnimatePacket clientboundAnimatePacket) {
        Entity entity = this.level().getEntity(clientboundAnimatePacket.getId());
        forward(entity, clientboundAnimatePacket);
    }

    @Override
    public void handleHurtAnimation(ClientboundHurtAnimationPacket clientboundHurtAnimationPacket) {
        Entity entity = this.level().getEntity(clientboundHurtAnimationPacket.id());
        forward(entity, clientboundHurtAnimationPacket);
    }

    @Override
    public void handleAwardStats(ClientboundAwardStatsPacket clientboundAwardStatsPacket) {
        throw new UnsupportedPacketException(clientboundAwardStatsPacket);
    }

    @Override
    public void handleAddOrRemoveRecipes(ClientboundRecipePacket clientboundRecipePacket) {
        throw new UnsupportedPacketException(clientboundRecipePacket);
    }

    @Override
    public void handleBlockDestruction(ClientboundBlockDestructionPacket clientboundBlockDestructionPacket) {
        forward(clientboundBlockDestructionPacket);
    }

    @Override
    public void handleOpenSignEditor(ClientboundOpenSignEditorPacket clientboundOpenSignEditorPacket) {
        throw new UnsupportedPacketException(clientboundOpenSignEditorPacket);
    }

    @Override
    public void handleBlockEntityData(ClientboundBlockEntityDataPacket clientboundBlockEntityDataPacket) {
        BlockPos blockPos = clientboundBlockEntityDataPacket.getPos();
        this.level().getBlockEntity(blockPos, clientboundBlockEntityDataPacket.getType()).ifPresent(blockEntity -> {
            // Update data
            blockEntity.loadWithComponents(clientboundBlockEntityDataPacket.getTag(), this.replayServer.registryAccess());

            // Sync
            blockEntity.setChanged();
            BlockState blockState = this.level().getBlockState(blockPos);
            Level level = blockEntity.getLevel();
            if (level != null) {
                level.sendBlockUpdated(blockPos, blockState, blockState, 3);
            }
        });
    }

    @Override
    public void handleBlockEvent(ClientboundBlockEventPacket clientboundBlockEventPacket) {
        BlockPos pos = clientboundBlockEventPacket.getPos();
        this.replayServer.getPlayerList().broadcast(null, pos.getX(), pos.getY(), pos.getZ(),
            64.0, this.level().dimension(), clientboundBlockEventPacket);
    }

    @Override
    public void handleBlockUpdate(ClientboundBlockUpdatePacket clientboundBlockUpdatePacket) {
        this.level().setBlock(clientboundBlockUpdatePacket.getPos(),
            clientboundBlockUpdatePacket.getBlockState(), 18);
    }

    @Override
    public void handleSystemChat(ClientboundSystemChatPacket clientboundSystemChatPacket) {
        forward(clientboundSystemChatPacket);
    }

    @Override
    public void handlePlayerChat(ClientboundPlayerChatPacket clientboundPlayerChatPacket) {
        throw new UnsupportedPacketException(clientboundPlayerChatPacket);
    }

    @Override
    public void handleDisguisedChat(ClientboundDisguisedChatPacket clientboundDisguisedChatPacket) {
        forward(clientboundDisguisedChatPacket);
    }

    @Override
    public void handleDeleteChat(ClientboundDeleteChatPacket clientboundDeleteChatPacket) {
        throw new UnsupportedPacketException(clientboundDeleteChatPacket);
    }

    @Override
    public void handleChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket clientboundSectionBlocksUpdatePacket) {
        clientboundSectionBlocksUpdatePacket.runUpdates((blockPos, blockState) -> {
            this.level().setBlock(blockPos, blockState, 18);
        });
    }

    @Override
    public void handleMapItemData(ClientboundMapItemDataPacket clientboundMapItemDataPacket) {
        forward(clientboundMapItemDataPacket);
    }

    @Override
    public void handleContainerClose(ClientboundContainerClosePacket clientboundContainerClosePacket) {
        throw new UnsupportedPacketException(clientboundContainerClosePacket);
    }

    @Override
    public void handleContainerContent(ClientboundContainerSetContentPacket clientboundContainerSetContentPacket) {
        throw new UnsupportedPacketException(clientboundContainerSetContentPacket);
    }

    @Override
    public void handleHorseScreenOpen(ClientboundHorseScreenOpenPacket clientboundHorseScreenOpenPacket) {
        throw new UnsupportedPacketException(clientboundHorseScreenOpenPacket);
    }

    @Override
    public void handleContainerSetData(ClientboundContainerSetDataPacket clientboundContainerSetDataPacket) {
        throw new UnsupportedPacketException(clientboundContainerSetDataPacket);
    }

    @Override
    public void handleContainerSetSlot(ClientboundContainerSetSlotPacket clientboundContainerSetSlotPacket) {
        throw new UnsupportedPacketException(clientboundContainerSetSlotPacket);
    }

    @Override
    public void handleEntityEvent(ClientboundEntityEventPacket clientboundEntityEventPacket) {
        Entity entity = clientboundEntityEventPacket.getEntity(this.level());
        forward(entity, clientboundEntityEventPacket);
    }

    @Override
    public void handleEntityLinkPacket(ClientboundSetEntityLinkPacket clientboundSetEntityLinkPacket) {
        Entity source = this.level().getEntity(clientboundSetEntityLinkPacket.getSourceId());
        if (source instanceof Mob mob) {
            Entity dest = this.level().getEntity(clientboundSetEntityLinkPacket.getDestId());
            mob.setLeashedTo(dest, true);
        }
    }

    @Override
    public void handleSetEntityPassengersPacket(ClientboundSetPassengersPacket clientboundSetPassengersPacket) {
        Entity vehicle = this.level().getEntity(clientboundSetPassengersPacket.getVehicle());
        if (vehicle == null) {
            return;
        }
        vehicle.ejectPassengers();
        for (int passengerId : clientboundSetPassengersPacket.getPassengers()) {
            Entity passenger = this.level().getEntity(passengerId);
            if (passenger == null) {
                continue;
            }

            passenger.startRiding(vehicle, true);
        }
    }

    @Override
    public void handleExplosion(ClientboundExplodePacket e) {
        ClientboundExplodePacket withoutKnockback = new ClientboundExplodePacket(e.getX(), e.getY(), e.getZ(),
            e.getPower(), e.getToBlow(), null, e.getBlockInteraction(), e.getSmallExplosionParticles(), e.getLargeExplosionParticles(),
            e.getExplosionSound());
        forward(withoutKnockback);
    }

    @Override
    public void handleGameEvent(ClientboundGameEventPacket clientboundGameEventPacket) {
        ClientboundGameEventPacket.Type type = clientboundGameEventPacket.getEvent();
        ServerLevel level = this.level();
        float floatValue = clientboundGameEventPacket.getParam();
        if (type == ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE) {
            return;
        } else if (type == ClientboundGameEventPacket.START_RAINING) {
            level.getLevelData().setRaining(true);
            level.setRainLevel(0.0f);
        } else if (type == ClientboundGameEventPacket.STOP_RAINING) {
            level.getLevelData().setRaining(false);
            level.setRainLevel(1.0f);
        } else if (type == ClientboundGameEventPacket.CHANGE_GAME_MODE) {
            return;
        } else if (type == ClientboundGameEventPacket.WIN_GAME) {
            return;
        } else if (type == ClientboundGameEventPacket.DEMO_EVENT) {
            return;
        } else if (type == ClientboundGameEventPacket.ARROW_HIT_PLAYER) {
            return;
        } else if (type == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE) {
            level.setRainLevel(floatValue);
        } else if (type == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE) {
            level.setThunderLevel(floatValue);
        } else if (type == ClientboundGameEventPacket.PUFFER_FISH_STING) {
            return;
        } else if (type == ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT) {
            return;
        } else if (type == ClientboundGameEventPacket.IMMEDIATE_RESPAWN) {
            return;
        } else if (type == ClientboundGameEventPacket.LIMITED_CRAFTING) {
            return;
        } else if (type == ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START) {
            return;
        }

        forward(clientboundGameEventPacket);
    }

    @Override
    public void handleLevelChunkWithLight(ClientboundLevelChunkWithLightPacket levelChunkWithLightPacket) {
        int x = levelChunkWithLightPacket.getX();
        int z = levelChunkWithLightPacket.getZ();

        LevelLightEngine levelLightEngine = this.level().getChunkSource().getLightEngine();
        LevelChunk chunk = this.level().getChunk(x, z);

        var chunkData = levelChunkWithLightPacket.getChunkData();

        chunk.replaceWithPacketData(chunkData.getReadBuffer(), chunkData.getHeightmaps(), chunkData.getBlockEntitiesTagsConsumer(x, z));

        var lightData = levelChunkWithLightPacket.getLightData();
        this.applyLightData(levelLightEngine, x, z, lightData);

        ChunkPos chunkPos = chunk.getPos();
        for (ServerPlayer serverPlayer : this.replayServer.getReplayViewers()) {
            if (serverPlayer.getChunkTrackingView().contains(chunkPos)) {
                serverPlayer.connection.chunkSender.markChunkPendingToSend(chunk);
            }
        }

        chunk.setUnsaved(true);
    }

    private void applyLightData(LevelLightEngine levelLightEngine, int x, int z, ClientboundLightUpdatePacketData clientboundLightUpdatePacketData) {
        levelLightEngine.retainData(new ChunkPos(x, z), true);

        BitSet skyYMask = clientboundLightUpdatePacketData.getSkyYMask();
        BitSet emptySkyYMask = clientboundLightUpdatePacketData.getEmptySkyYMask();
        Iterator<byte[]> iterator = clientboundLightUpdatePacketData.getSkyUpdates().iterator();
        this.readSectionList(x, z, levelLightEngine, LightLayer.SKY, skyYMask, emptySkyYMask, iterator);

        BitSet blockYMask = clientboundLightUpdatePacketData.getBlockYMask();
        BitSet emptyBlockYMask = clientboundLightUpdatePacketData.getEmptyBlockYMask();
        Iterator<byte[]> iterator2 = clientboundLightUpdatePacketData.getBlockUpdates().iterator();
        this.readSectionList(x, z, levelLightEngine, LightLayer.BLOCK, blockYMask, emptyBlockYMask, iterator2);

        ((ThreadedLevelLightEngineExt)levelLightEngine).flashback$submitPost(x, z, () -> {
            // Initialize light
            LevelChunk chunkAccess = this.level().getChunk(x, z);
            // todo: do we need to do this if replaceWithPacketData already does it?
            chunkAccess.initializeLightSources();
            boolean isLightCorrect = chunkAccess.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT) && chunkAccess.isLightCorrect();
            ((ThreadedLevelLightEngine)levelLightEngine).initializeLight(chunkAccess, isLightCorrect).thenRun(() -> {
                // Send light data
                LevelChunk chunkAccess2 = this.level().getChunk(x, z);
                chunkAccess2.setLightCorrect(true);

                ChunkPos chunkPos = new ChunkPos(x, z);
                var lightPacket = new ClientboundLightUpdatePacket(chunkPos, levelLightEngine, null, null);
                for (ServerPlayer serverPlayer : this.replayServer.getReplayViewers()) {
                    if (serverPlayer.getChunkTrackingView().contains(chunkPos)) {
                        serverPlayer.connection.send(lightPacket);
                    }
                }
            });
        });
    }

    private void readSectionList(int x, int z, LevelLightEngine levelLightEngine, LightLayer lightLayer, BitSet yMask, BitSet emptyYMask, Iterator<byte[]> iterator) {
        for(int index = 0; index < levelLightEngine.getLightSectionCount(); ++index) {
            int y = levelLightEngine.getMinLightSection() + index;
            boolean hasData = yMask.get(index);
            boolean isEmpty = emptyYMask.get(index);
            if (hasData || isEmpty) {
                levelLightEngine.queueSectionData(lightLayer, SectionPos.of(x, y, z), hasData ? new DataLayer(iterator.next().clone()) : new DataLayer());
            }
        }
    }

    @Override
    public void handleChunksBiomes(ClientboundChunksBiomesPacket clientboundChunksBiomesPacket) {
        List<ChunkAccess> chunks = new ArrayList<>(clientboundChunksBiomesPacket.chunkBiomeData().size());
        for (ClientboundChunksBiomesPacket.ChunkBiomeData chunkBiomeData : clientboundChunksBiomesPacket.chunkBiomeData()) {
            LevelChunk chunk = this.level().getChunk(chunkBiomeData.pos().x, chunkBiomeData.pos().z);
            chunk.replaceBiomes(chunkBiomeData.getReadBuffer());
            chunks.add(chunk);
        }
        this.level().getChunkSource().chunkMap.resendBiomesForChunks(chunks);
    }

    @Override
    public void handleForgetLevelChunk(ClientboundForgetLevelChunkPacket clientboundForgetLevelChunkPacket) {
        throw new UnsupportedPacketException(clientboundForgetLevelChunkPacket);
    }

    @Override
    public void handleLevelEvent(ClientboundLevelEventPacket clientboundLevelEventPacket) {
        if (clientboundLevelEventPacket.isGlobalEvent()) {
            this.replayServer.getPlayerList().broadcastAll(clientboundLevelEventPacket);
        } else {
            BlockPos blockPos = clientboundLevelEventPacket.getPos();
            ResourceKey<Level> dimension = this.level().dimension();
            this.replayServer.getPlayerList().broadcast(null, blockPos.getX(), blockPos.getY(), blockPos.getZ(), 64.0, dimension, clientboundLevelEventPacket);
        }
    }

    @Override
    public void handleLogin(ClientboundLoginPacket clientboundLoginPacket) {
        ServerPlayer localPlayer = null;
        boolean recreatePlayer = false;

        ServerLevel level = this.level();
        if (level != null && level.getEntity(this.localPlayerId) instanceof ServerPlayer serverPlayer) {
            localPlayer = serverPlayer;
            if (this.localPlayerId != clientboundLoginPacket.playerId()) {
                localPlayer.connection.disconnect(Component.empty());
                recreatePlayer = true;
            }
        }

        this.localPlayerId = clientboundLoginPacket.playerId();
        this.ensureWorldCreated(clientboundLoginPacket.commonPlayerSpawnInfo(), recreatePlayer ? null : localPlayer, true);

        if (recreatePlayer) {
            ClientboundAddEntityPacket addEntityPacket = PacketHelper.createAddEntity(localPlayer);
            addEntityPacket.id = clientboundLoginPacket.playerId();
            this.spawnPlayer(addEntityPacket, localPlayer.getGameProfile(), localPlayer.gameMode.getGameModeForPlayer());
        }
    }

    public void handleCreateLocalPlayer(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
        UUID uuid = registryFriendlyByteBuf.readUUID();
        double x = registryFriendlyByteBuf.readDouble();
        double y = registryFriendlyByteBuf.readDouble();
        double z = registryFriendlyByteBuf.readDouble();
        float xRot = registryFriendlyByteBuf.readFloat();
        float yRot = registryFriendlyByteBuf.readFloat();
        float yHeadRot = registryFriendlyByteBuf.readFloat();
        Vec3 velocity = registryFriendlyByteBuf.readVec3();

        ClientboundAddEntityPacket addEntityPacket = new ClientboundAddEntityPacket(this.localPlayerId, uuid, x, y, z, xRot, yRot, EntityType.PLAYER, 0, velocity, yHeadRot);
        GameProfile gameProfile = ByteBufCodecs.GAME_PROFILE.decode(registryFriendlyByteBuf);
        GameType gameType = GameType.byId(registryFriendlyByteBuf.readVarInt());

        if (this.getEntityOrPending(this.localPlayerId) == null) {
            this.spawnPlayer(addEntityPacket, gameProfile, gameType);
        }
    }

    private void ensureWorldCreated(CommonPlayerSpawnInfo commonPlayerSpawnInfo, Entity localPlayer, boolean forceReset) {
        ResourceKey<Level> dimension = commonPlayerSpawnInfo.dimension();

        if (forceReset) {
            this.replayServer.clearReplayTempFolder();
            if (this.currentDimension == dimension) {
                this.replayServer.clearLevel(this.level());
            }
        }

        if (!this.replayServer.levels.containsKey(dimension)) {
            ServerLevelData serverLevelData = this.replayServer.worldData.overworldData();
            if (dimension != Level.OVERWORLD) {
                serverLevelData = new DerivedLevelData(this.replayServer.worldData, serverLevelData);
            }
            Holder.Reference<Biome> plains = this.replayServer.registryAccess().registryOrThrow(Registries.BIOME).getHolder(Biomes.PLAINS).get();
            LevelStem levelStem = new LevelStem(commonPlayerSpawnInfo.dimensionType(), new EmptyLevelSource(plains));
            var progressListener = this.replayServer.progressListenerFactory.create(this.replayServer.worldData.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS));
            ServerLevel serverLevel = new ServerLevel(this.replayServer, this.replayServer.executor, this.replayServer.storageSource,
                serverLevelData, dimension, levelStem, progressListener,
                false, commonPlayerSpawnInfo.seed(), List.of(), false, null);
            serverLevel.noSave = true;
            this.replayServer.levels.put(dimension, serverLevel);
            this.replayServer.followLocalPlayerNextTick = true;
        }

        if (this.currentDimension == dimension) {
            this.replayServer.followLocalPlayerNextTickIfFar = true;
            return;
        }

        // Move local player and replay viewer
        if (localPlayer != null) {
            localPlayer.teleportTo(this.replayServer.getLevel(dimension), 0.0, 0.0, 0.0, Set.of(), 0.0f, 0.0f);
        }
        for (ReplayPlayer replayViewer : this.replayServer.getReplayViewers()) {
            replayViewer.teleportTo(this.replayServer.getLevel(dimension), 0.0, 0.0, 0.0, Set.of(), 0.0f, 0.0f);
        }
        this.replayServer.followLocalPlayerNextTick = true;

        // Delete current dimension
        if (this.currentDimension == Level.OVERWORLD) {
            this.replayServer.clearLevel(this.level());
        } else if (this.currentDimension != null) {
            ServerLevel level = this.replayServer.levels.remove(this.currentDimension);
            if (level != null) {
                this.replayServer.closeLevel(level);
            }
        }

        // Change to new dimension
        this.currentDimension = commonPlayerSpawnInfo.dimension();
        this.replayServer.spawnLevel = this.currentDimension;
    }

    @Override
    public void handleMoveEntity(ClientboundMoveEntityPacket clientboundMoveEntityPacket) {
        throw new UnsupportedPacketException(clientboundMoveEntityPacket);
//        Entity entity = clientboundMoveEntityPacket.getEntity(this.level());
//        if (entity == null) {
//            return;
//        }
//        if (entity.getId() == this.localPlayerId) {
//            return;
//        }
//
//        if (clientboundMoveEntityPacket.hasPosition()) {
//            VecDeltaCodec vecDeltaCodec = entity.getPositionCodec();
//            Vec3 position = vecDeltaCodec.decode(clientboundMoveEntityPacket.getXa(), clientboundMoveEntityPacket.getYa(), clientboundMoveEntityPacket.getZa());
//            vecDeltaCodec.setBase(position);
//            float yaw = clientboundMoveEntityPacket.hasRotation() ? (float)(clientboundMoveEntityPacket.getyRot() * 360) / 256.0f : entity.lerpTargetYRot();
//            float pitch = clientboundMoveEntityPacket.hasRotation() ? (float)(clientboundMoveEntityPacket.getxRot() * 360) / 256.0f : entity.lerpTargetXRot();
//            entity.moveTo(position.x(), position.y(), position.z(), yaw, pitch);
//        } else if (clientboundMoveEntityPacket.hasRotation()) {
//            float yaw = (float)(clientboundMoveEntityPacket.getyRot() * 360) / 256.0f;
//            float pitch = (float)(clientboundMoveEntityPacket.getxRot() * 360) / 256.0f;
//            entity.setYRot(yaw);
//            entity.setXRot(pitch);
//        }
//
//        entity.setOnGround(clientboundMoveEntityPacket.isOnGround());
    }

    @Override
    public void handleMovePlayer(ClientboundPlayerPositionPacket clientboundPlayerPositionPacket) {
        throw new UnsupportedPacketException(clientboundPlayerPositionPacket);

//        Entity entity = this.level().getEntity(this.localPlayerId);
//        if (entity == null) {
//            return;
//        }
//
//        Set<RelativeMovement> flags = clientboundPlayerPositionPacket.getRelativeArguments();
//
//        double x = entity.getX();
//        double y = entity.getY();
//        double z = entity.getZ();
//        float yaw = entity.getYRot();
//        float pitch = entity.getXRot();
//
//        if (flags.contains(RelativeMovement.X)) {
//            x += clientboundPlayerPositionPacket.getX();
//        } else {
//            x = clientboundPlayerPositionPacket.getX();
//        }
//        if (flags.contains(RelativeMovement.Y)) {
//            y += clientboundPlayerPositionPacket.getY();
//        } else {
//            y = clientboundPlayerPositionPacket.getY();
//        }
//        if (flags.contains(RelativeMovement.Z)) {
//            z += clientboundPlayerPositionPacket.getZ();
//        } else {
//            z = clientboundPlayerPositionPacket.getZ();
//        }
//        if (flags.contains(RelativeMovement.Y_ROT)) {
//            yaw += clientboundPlayerPositionPacket.getYRot();
//        } else {
//            yaw = clientboundPlayerPositionPacket.getYRot();
//        }
//        if (flags.contains(RelativeMovement.X_ROT)) {
//            pitch += clientboundPlayerPositionPacket.getXRot();
//        } else {
//            pitch = clientboundPlayerPositionPacket.getXRot();
//        }
//
//        entity.moveTo(x, y, z, Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch));
//        entity.setYHeadRot(entity.getYRot());
    }

    @Override
    public void handleParticleEvent(ClientboundLevelParticlesPacket clientboundLevelParticlesPacket) {
        for (ServerPlayer viewer : this.replayServer.getReplayViewers()) {
            boolean overrideLimiter = clientboundLevelParticlesPacket.isOverrideLimiter();
            double x = clientboundLevelParticlesPacket.getX();
            double y = clientboundLevelParticlesPacket.getY();
            double z = clientboundLevelParticlesPacket.getZ();
            this.level().sendParticles(viewer, overrideLimiter, x, y, z, clientboundLevelParticlesPacket);
        }
    }

    @Override
    public void handlePlayerAbilities(ClientboundPlayerAbilitiesPacket clientboundPlayerAbilitiesPacket) {
        throw new UnsupportedPacketException(clientboundPlayerAbilitiesPacket);
    }

    @Override
    public void handlePlayerInfoRemove(ClientboundPlayerInfoRemovePacket clientboundPlayerInfoRemovePacket) {
        for (UUID uuid : clientboundPlayerInfoRemovePacket.profileIds()) {
            this.playerInfoMap.remove(uuid);
        }
        forward(clientboundPlayerInfoRemovePacket);
    }

    @Override
    public void handlePlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket clientboundPlayerInfoUpdatePacket) {
        for (ClientboundPlayerInfoUpdatePacket.Entry entry : clientboundPlayerInfoUpdatePacket.newEntries()) {
            PlayerInfo playerInfo = new PlayerInfo(Objects.requireNonNull(entry.profile()), false);
            this.playerInfoMap.putIfAbsent(entry.profileId(), playerInfo);
        }
        forward(clientboundPlayerInfoUpdatePacket);
    }

    @Override
    public void handleRemoveEntities(ClientboundRemoveEntitiesPacket clientboundRemoveEntitiesPacket) {
        clientboundRemoveEntitiesPacket.getEntityIds().forEach(i -> {
            Entity entity = this.level().getEntity(i);
            if (entity != null) {
                if (entity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.connection.disconnect(Component.empty());
                } else {
                    entity.discard();
                }
            }
        });
    }

    @Override
    public void handleRemoveMobEffect(ClientboundRemoveMobEffectPacket clientboundRemoveMobEffectPacket) {
        Entity entity = clientboundRemoveMobEffectPacket.getEntity(this.level());
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.removeEffect(clientboundRemoveMobEffectPacket.effect());
        }
    }

    @Override
    public void handleRespawn(ClientboundRespawnPacket clientboundRespawnPacket) {
        Entity localPlayer = null;
        ServerLevel level = this.level();
        if (level != null) {
            localPlayer = level.getEntity(this.localPlayerId);
        }

        this.ensureWorldCreated(clientboundRespawnPacket.commonPlayerSpawnInfo(), localPlayer, false);
    }

    @Override
    public void handleRotateMob(ClientboundRotateHeadPacket clientboundRotateHeadPacket) {
        Entity entity = this.getEntityOrPending(clientboundRotateHeadPacket.entityId);
        if (entity == null || entity.getId() == this.localPlayerId) {
            return;
        }

        entity.setYHeadRot(clientboundRotateHeadPacket.getYHeadRot());
    }

    @Override
    public void handleSetCarriedItem(ClientboundSetCarriedItemPacket clientboundSetCarriedItemPacket) {
        throw new UnsupportedPacketException(clientboundSetCarriedItemPacket);
    }

    @Override
    public void handleSetDisplayObjective(ClientboundSetDisplayObjectivePacket clientboundSetDisplayObjectivePacket) {
        forward(clientboundSetDisplayObjectivePacket);
    }

    @Override
    public void handleSetEntityData(ClientboundSetEntityDataPacket clientboundSetEntityDataPacket) {
        Entity entity = this.getEntityOrPending(clientboundSetEntityDataPacket.id());
        if (entity == null) {
            return;
        }

        SynchedEntityData entityData = entity.getEntityData();
        for (SynchedEntityData.DataValue<?> dataValue : clientboundSetEntityDataPacket.packedItems()) {
            SynchedEntityData.DataItem<?> dataItem = entityData.itemsById[dataValue.id()];
            entityData.set((EntityDataAccessor) dataItem.getAccessor(), dataValue.value());
        }
    }

    @Override
    public void handleSetEntityMotion(ClientboundSetEntityMotionPacket clientboundSetEntityMotionPacket) {
        Entity entity = this.getEntityOrPending(clientboundSetEntityMotionPacket.getId());
        if (entity == null) {
            return;
        }

        double motionX = clientboundSetEntityMotionPacket.getXa() / 8000.0;
        double motionY = clientboundSetEntityMotionPacket.getYa() / 8000.0;
        double motionZ = clientboundSetEntityMotionPacket.getZa() / 8000.0;
        entity.setDeltaMovement(motionX, motionY, motionZ);
    }

    @Override
    public void handleSetEquipment(ClientboundSetEquipmentPacket clientboundSetEquipmentPacket) {
        Entity entity = this.getEntityOrPending(clientboundSetEquipmentPacket.getEntity());
        if (entity instanceof LivingEntity livingEntity) {
            clientboundSetEquipmentPacket.getSlots().forEach((pair) -> {
                livingEntity.setItemSlot(pair.getFirst(), pair.getSecond());
            });
            forward(entity, clientboundSetEquipmentPacket);
        }
    }

    @Override
    public void handleSetExperience(ClientboundSetExperiencePacket clientboundSetExperiencePacket) {
        Entity entity = this.level().getEntity(this.localPlayerId);
        if (entity instanceof Player player) {
            player.experienceProgress = clientboundSetExperiencePacket.getExperienceProgress();
            player.totalExperience = clientboundSetExperiencePacket.getTotalExperience();
            player.experienceLevel = clientboundSetExperiencePacket.getExperienceLevel();
        }
    }

    @Override
    public void handleSetHealth(ClientboundSetHealthPacket clientboundSetHealthPacket) {
        Entity entity = this.level().getEntity(this.localPlayerId);
        if (entity instanceof Player player) {
            player.setHealth(clientboundSetHealthPacket.getHealth());
            player.getFoodData().setFoodLevel(clientboundSetHealthPacket.getFood());
            player.getFoodData().setSaturation(clientboundSetHealthPacket.getSaturation());
        }
    }

    @Override
    public void handleSetPlayerTeamPacket(ClientboundSetPlayerTeamPacket clientboundSetPlayerTeamPacket) {
        forward(clientboundSetPlayerTeamPacket);
    }

    @Override
    public void handleSetScore(ClientboundSetScorePacket clientboundSetScorePacket) {
        forward(clientboundSetScorePacket);
    }

    @Override
    public void handleResetScore(ClientboundResetScorePacket clientboundResetScorePacket) {
        forward(clientboundResetScorePacket);
    }

    @Override
    public void handleSetSpawn(ClientboundSetDefaultSpawnPositionPacket clientboundSetDefaultSpawnPositionPacket) {
        this.level().setDefaultSpawnPos(clientboundSetDefaultSpawnPositionPacket.getPos(), clientboundSetDefaultSpawnPositionPacket.getAngle());
    }

    @Override
    public void handleSetTime(ClientboundSetTimePacket clientboundSetTimePacket) {
        boolean updateTime = true;

        long dayTime = clientboundSetTimePacket.getDayTime();
        if (dayTime < 0) {
            dayTime = -dayTime;
            updateTime = false;
        }

        for (ServerLevel level : this.replayServer.getAllLevels()) {
            if (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) != updateTime) {
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(updateTime, this.replayServer);
            }

            level.setDayTime(dayTime);

            if (level.getLevelData() instanceof ServerLevelData serverLevelData) {
                serverLevelData.setGameTime(clientboundSetTimePacket.getGameTime());
            }
        }

        forward(clientboundSetTimePacket);
    }

    @Override
    public void handleSoundEvent(ClientboundSoundPacket clientboundSoundPacket) {
        Holder<SoundEvent> sound = clientboundSoundPacket.getSound();
        double x = clientboundSoundPacket.getX();
        double y = clientboundSoundPacket.getY();
        double z = clientboundSoundPacket.getZ();
        float volume = clientboundSoundPacket.getVolume();
        ResourceKey<Level> dimension = this.level().dimension();
        this.replayServer.getPlayerList().broadcast(null, x, y, z, sound.value().getRange(volume), dimension, clientboundSoundPacket);
    }

    @Override
    public void handleSoundEntityEvent(ClientboundSoundEntityPacket clientboundSoundEntityPacket) {
        Entity entity = this.level().getEntity(clientboundSoundEntityPacket.getId());
        if (entity == null) {
            return;
        }

        Holder<SoundEvent> sound = clientboundSoundEntityPacket.getSound();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        float volume = clientboundSoundEntityPacket.getVolume();
        ResourceKey<Level> dimension = this.level().dimension();
        this.replayServer.getPlayerList().broadcast(null, x, y, z, sound.value().getRange(volume), dimension, clientboundSoundEntityPacket);
    }

    @Override
    public void handleTakeItemEntity(ClientboundTakeItemEntityPacket clientboundTakeItemEntityPacket) {
        Entity taker = this.level().getEntity(clientboundTakeItemEntityPacket.getPlayerId());
        forward(taker, clientboundTakeItemEntityPacket);

        Entity taken = this.level().getEntity(clientboundTakeItemEntityPacket.getItemId());
        if (taken != null) {
            taken.discard();
        }
    }

    @Override
    public void handleTeleportEntity(ClientboundTeleportEntityPacket clientboundTeleportEntityPacket) {
        Entity entity = this.getEntityOrPending(clientboundTeleportEntityPacket.getId());
        if (entity == null) {
            return;
        }

        double x = clientboundTeleportEntityPacket.getX();
        double y = clientboundTeleportEntityPacket.getY();
        double z = clientboundTeleportEntityPacket.getZ();
        float yaw = (float)(clientboundTeleportEntityPacket.getyRot() * 360) / 256.0F;
        float pitch = (float)(clientboundTeleportEntityPacket.getxRot() * 360) / 256.0F;
        entity.moveTo(x, y, z, yaw, pitch);
        entity.setOnGround(clientboundTeleportEntityPacket.isOnGround());
        entity.setYHeadRot(entity.getYRot());
    }

    @Override
    public void handleTickingState(ClientboundTickingStatePacket clientboundTickingStatePacket) {
        throw new UnsupportedPacketException(clientboundTickingStatePacket);
    }

    @Override
    public void handleTickingStep(ClientboundTickingStepPacket clientboundTickingStepPacket) {
        throw new UnsupportedPacketException(clientboundTickingStepPacket);
    }

    @Override
    public void handleUpdateAttributes(ClientboundUpdateAttributesPacket clientboundUpdateAttributesPacket) {
        Entity entity = this.getEntityOrPending(clientboundUpdateAttributesPacket.getEntityId());
        if (entity instanceof LivingEntity livingEntity) {
            AttributeMap attributeMap = livingEntity.getAttributes();
            for (ClientboundUpdateAttributesPacket.AttributeSnapshot snapshot : clientboundUpdateAttributesPacket.getValues()) {
                AttributeInstance attributeInstance = attributeMap.getInstance(snapshot.attribute());
                if (attributeInstance == null) {
                    continue;
                }

                attributeInstance.setBaseValue(snapshot.base());
                attributeInstance.removeModifiers();
                for (AttributeModifier modifier : snapshot.modifiers()) {
                    attributeInstance.addTransientModifier(modifier);
                }
            }
        }
    }

    @Override
    public void handleUpdateMobEffect(ClientboundUpdateMobEffectPacket clientboundUpdateMobEffectPacket) {
        Entity entity = this.getEntityOrPending(clientboundUpdateMobEffectPacket.getEntityId());
        if (entity instanceof LivingEntity livingEntity) {
            Holder<MobEffect> holder = clientboundUpdateMobEffectPacket.getEffect();
            MobEffectInstance mobEffectInstance = new MobEffectInstance(holder, clientboundUpdateMobEffectPacket.getEffectDurationTicks(), clientboundUpdateMobEffectPacket.getEffectAmplifier(),
                clientboundUpdateMobEffectPacket.isEffectAmbient(), clientboundUpdateMobEffectPacket.isEffectVisible(), clientboundUpdateMobEffectPacket.effectShowsIcon(), null);
            if (!clientboundUpdateMobEffectPacket.shouldBlend()) {
                mobEffectInstance.skipBlending();
            }

            livingEntity.forceAddEffect(mobEffectInstance, null);
        }
    }

    @Override
    public void handlePlayerCombatEnd(ClientboundPlayerCombatEndPacket clientboundPlayerCombatEndPacket) {
        throw new UnsupportedPacketException(clientboundPlayerCombatEndPacket);
    }

    @Override
    public void handlePlayerCombatEnter(ClientboundPlayerCombatEnterPacket clientboundPlayerCombatEnterPacket) {
        throw new UnsupportedPacketException(clientboundPlayerCombatEnterPacket);
    }

    @Override
    public void handlePlayerCombatKill(ClientboundPlayerCombatKillPacket clientboundPlayerCombatKillPacket) {
        throw new UnsupportedPacketException(clientboundPlayerCombatKillPacket);
    }

    @Override
    public void handleChangeDifficulty(ClientboundChangeDifficultyPacket clientboundChangeDifficultyPacket) {
        this.replayServer.setDifficultyLocked(clientboundChangeDifficultyPacket.isLocked());
        this.replayServer.setDifficulty(clientboundChangeDifficultyPacket.getDifficulty(), true);
    }

    @Override
    public void handleSetCamera(ClientboundSetCameraPacket clientboundSetCameraPacket) {
        throw new UnsupportedPacketException(clientboundSetCameraPacket);
    }

    @Override
    public void handleInitializeBorder(ClientboundInitializeBorderPacket clientboundInitializeBorderPacket) {
        WorldBorder worldBorder = this.level().getWorldBorder();

        worldBorder.setCenter(clientboundInitializeBorderPacket.getNewCenterX(), clientboundInitializeBorderPacket.getNewCenterZ());
        long lerpTime = clientboundInitializeBorderPacket.getLerpTime();
        if (lerpTime > 0L) {
            worldBorder.lerpSizeBetween(clientboundInitializeBorderPacket.getOldSize(), clientboundInitializeBorderPacket.getNewSize(), lerpTime);
        } else {
            worldBorder.setSize(clientboundInitializeBorderPacket.getNewSize());
        }
        worldBorder.setAbsoluteMaxSize(clientboundInitializeBorderPacket.getNewAbsoluteMaxSize());
        worldBorder.setWarningBlocks(clientboundInitializeBorderPacket.getWarningBlocks());
        worldBorder.setWarningTime(clientboundInitializeBorderPacket.getWarningTime());
    }

    @Override
    public void handleSetBorderLerpSize(ClientboundSetBorderLerpSizePacket clientboundSetBorderLerpSizePacket) {
        WorldBorder worldBorder = this.level().getWorldBorder();

        long lerpTime = clientboundSetBorderLerpSizePacket.getLerpTime();
        if (lerpTime > 0L) {
            worldBorder.lerpSizeBetween(clientboundSetBorderLerpSizePacket.getOldSize(), clientboundSetBorderLerpSizePacket.getNewSize(), lerpTime);
        } else {
            worldBorder.setSize(clientboundSetBorderLerpSizePacket.getNewSize());
        }
    }

    @Override
    public void handleSetBorderSize(ClientboundSetBorderSizePacket clientboundSetBorderSizePacket) {
        WorldBorder worldBorder = this.level().getWorldBorder();
        worldBorder.setSize(clientboundSetBorderSizePacket.getSize());
    }

    @Override
    public void handleSetBorderWarningDelay(ClientboundSetBorderWarningDelayPacket clientboundSetBorderWarningDelayPacket) {
        WorldBorder worldBorder = this.level().getWorldBorder();
        worldBorder.setWarningTime(clientboundSetBorderWarningDelayPacket.getWarningDelay());
    }

    @Override
    public void handleSetBorderWarningDistance(ClientboundSetBorderWarningDistancePacket clientboundSetBorderWarningDistancePacket) {
        WorldBorder worldBorder = this.level().getWorldBorder();
        worldBorder.setWarningBlocks(clientboundSetBorderWarningDistancePacket.getWarningBlocks());
    }

    @Override
    public void handleSetBorderCenter(ClientboundSetBorderCenterPacket clientboundSetBorderCenterPacket) {
        WorldBorder worldBorder = this.level().getWorldBorder();
        worldBorder.setCenter(clientboundSetBorderCenterPacket.getNewCenterX(), clientboundSetBorderCenterPacket.getNewCenterZ());
    }

    @Override
    public void handleTabListCustomisation(ClientboundTabListPacket clientboundTabListPacket) {
        this.replayServer.setTabListCustomization(clientboundTabListPacket.header(),
            clientboundTabListPacket.footer());
    }

    @Override
    public void handleBossUpdate(ClientboundBossEventPacket clientboundBossEventPacket) {
        this.replayServer.updateBossBar(clientboundBossEventPacket);
    }

    @Override
    public void handleItemCooldown(ClientboundCooldownPacket clientboundCooldownPacket) {
        throw new UnsupportedPacketException(clientboundCooldownPacket);
    }

    @Override
    public void handleMoveVehicle(ClientboundMoveVehiclePacket clientboundMoveVehiclePacket) {
        Entity player = this.level().getEntity(this.localPlayerId);
        if (player instanceof Player) {
            Entity vehicle = player.getRootVehicle();
            if (vehicle != player) {
                double x = clientboundMoveVehiclePacket.getX();
                double y = clientboundMoveVehiclePacket.getY();
                double z = clientboundMoveVehiclePacket.getZ();
                float yaw = clientboundMoveVehiclePacket.getYRot();
                float pitch = clientboundMoveVehiclePacket.getXRot();
                vehicle.moveTo(x, y, z, yaw, pitch);
            }
        }
    }

    @Override
    public void handleUpdateAdvancementsPacket(ClientboundUpdateAdvancementsPacket clientboundUpdateAdvancementsPacket) {
        throw new UnsupportedPacketException(clientboundUpdateAdvancementsPacket);
    }

    @Override
    public void handleSelectAdvancementsTab(ClientboundSelectAdvancementsTabPacket clientboundSelectAdvancementsTabPacket) {
        throw new UnsupportedPacketException(clientboundSelectAdvancementsTabPacket);
    }

    @Override
    public void handlePlaceRecipe(ClientboundPlaceGhostRecipePacket clientboundPlaceGhostRecipePacket) {
        throw new UnsupportedPacketException(clientboundPlaceGhostRecipePacket);
    }

    @Override
    public void handleCommands(ClientboundCommandsPacket clientboundCommandsPacket) {
        throw new UnsupportedPacketException(clientboundCommandsPacket);
    }

    @Override
    public void handleStopSoundEvent(ClientboundStopSoundPacket clientboundStopSoundPacket) {
        forward(clientboundStopSoundPacket);
    }

    @Override
    public void handleCommandSuggestions(ClientboundCommandSuggestionsPacket clientboundCommandSuggestionsPacket) {
        throw new UnsupportedPacketException(clientboundCommandSuggestionsPacket);
    }

    @Override
    public void handleUpdateRecipes(ClientboundUpdateRecipesPacket clientboundUpdateRecipesPacket) {
        throw new UnsupportedPacketException(clientboundUpdateRecipesPacket);
    }

    @Override
    public void handleLookAt(ClientboundPlayerLookAtPacket clientboundPlayerLookAtPacket) {
        Vec3 position = clientboundPlayerLookAtPacket.getPosition(this.level());
        if (position != null) {
            Entity player = this.level().getEntity(this.localPlayerId);
            if (player instanceof Player) {
                player.lookAt(clientboundPlayerLookAtPacket.getFromAnchor(), position);
            }
        }
    }

    @Override
    public void handleTagQueryPacket(ClientboundTagQueryPacket clientboundTagQueryPacket) {
        throw new UnsupportedPacketException(clientboundTagQueryPacket);
    }

    @Override
    public void handleLightUpdatePacket(ClientboundLightUpdatePacket clientboundLightUpdatePacket) {
        int x = clientboundLightUpdatePacket.getX();
        int z = clientboundLightUpdatePacket.getZ();

        LevelLightEngine levelLightEngine = this.level().getChunkSource().getLightEngine();
        LevelChunk chunk = this.level().getChunk(x, z);

        var lightData = clientboundLightUpdatePacket.getLightData();
        this.applyLightData(levelLightEngine, x, z, lightData);

        chunk.setUnsaved(true);
    }

    @Override
    public void handleOpenBook(ClientboundOpenBookPacket clientboundOpenBookPacket) {
        throw new UnsupportedPacketException(clientboundOpenBookPacket);
    }

    @Override
    public void handleOpenScreen(ClientboundOpenScreenPacket clientboundOpenScreenPacket) {
        throw new UnsupportedPacketException(clientboundOpenScreenPacket);
    }

    @Override
    public void handleMerchantOffers(ClientboundMerchantOffersPacket clientboundMerchantOffersPacket) {
        throw new UnsupportedPacketException(clientboundMerchantOffersPacket);
    }

    @Override
    public void handleSetChunkCacheRadius(ClientboundSetChunkCacheRadiusPacket clientboundSetChunkCacheRadiusPacket) {
        throw new UnsupportedPacketException(clientboundSetChunkCacheRadiusPacket);
    }

    @Override
    public void handleSetSimulationDistance(ClientboundSetSimulationDistancePacket clientboundSetSimulationDistancePacket) {
        throw new UnsupportedPacketException(clientboundSetSimulationDistancePacket);
    }

    @Override
    public void handleSetChunkCacheCenter(ClientboundSetChunkCacheCenterPacket clientboundSetChunkCacheCenterPacket) {
        throw new UnsupportedPacketException(clientboundSetChunkCacheCenterPacket);
    }

    @Override
    public void handleBlockChangedAck(ClientboundBlockChangedAckPacket clientboundBlockChangedAckPacket) {
        throw new UnsupportedPacketException(clientboundBlockChangedAckPacket);
    }

    @Override
    public void setActionBarText(ClientboundSetActionBarTextPacket clientboundSetActionBarTextPacket) {
        forward(clientboundSetActionBarTextPacket);
    }

    @Override
    public void setSubtitleText(ClientboundSetSubtitleTextPacket clientboundSetSubtitleTextPacket) {
        forward(clientboundSetSubtitleTextPacket);
    }

    @Override
    public void setTitleText(ClientboundSetTitleTextPacket clientboundSetTitleTextPacket) {
        forward(clientboundSetTitleTextPacket);
    }

    @Override
    public void setTitlesAnimation(ClientboundSetTitlesAnimationPacket clientboundSetTitlesAnimationPacket) {
        forward(clientboundSetTitlesAnimationPacket);
    }

    @Override
    public void handleTitlesClear(ClientboundClearTitlesPacket clientboundClearTitlesPacket) {
        forward(clientboundClearTitlesPacket);
    }

    @Override
    public void handleServerData(ClientboundServerDataPacket clientboundServerDataPacket) {
        forward(clientboundServerDataPacket);
    }

    @Override
    public void handleCustomChatCompletions(ClientboundCustomChatCompletionsPacket clientboundCustomChatCompletionsPacket) {
        throw new UnsupportedPacketException(clientboundCustomChatCompletionsPacket);
    }

    @Override
    public void handleBundlePacket(ClientboundBundlePacket clientboundBundlePacket) {
        for (Packet<? super ClientGamePacketListener> subPacket : clientboundBundlePacket.subPackets()) {
            subPacket.handle(this);
        }
    }

    @Override
    public void handleDamageEvent(ClientboundDamageEventPacket clientboundDamageEventPacket) {
        Entity entity = this.level().getEntity(clientboundDamageEventPacket.entityId());
        forward(entity, clientboundDamageEventPacket);
    }

    @Override
    public void handleConfigurationStart(ClientboundStartConfigurationPacket clientboundStartConfigurationPacket) {
        throw new UnsupportedPacketException(clientboundStartConfigurationPacket);
    }

    @Override
    public void handleChunkBatchStart(ClientboundChunkBatchStartPacket clientboundChunkBatchStartPacket) {
        throw new UnsupportedPacketException(clientboundChunkBatchStartPacket);
    }

    @Override
    public void handleChunkBatchFinished(ClientboundChunkBatchFinishedPacket clientboundChunkBatchFinishedPacket) {
        throw new UnsupportedPacketException(clientboundChunkBatchFinishedPacket);
    }

    @Override
    public void handleDebugSample(ClientboundDebugSamplePacket clientboundDebugSamplePacket) {
        throw new UnsupportedPacketException(clientboundDebugSamplePacket);
    }

    @Override
    public void handleProjectilePowerPacket(ClientboundProjectilePowerPacket clientboundProjectilePowerPacket) {
        Entity entity = this.level().getEntity(clientboundProjectilePowerPacket.getId());
        forward(entity, clientboundProjectilePowerPacket);
    }

    @Override
    public void handleKeepAlive(ClientboundKeepAlivePacket clientboundKeepAlivePacket) {
        throw new UnsupportedPacketException(clientboundKeepAlivePacket);
    }

    @Override
    public void handlePing(ClientboundPingPacket clientboundPingPacket) {
        throw new UnsupportedPacketException(clientboundPingPacket);
    }

    @Override
    public void handleCustomPayload(ClientboundCustomPayloadPacket clientboundCustomPayloadPacket) {
        // todo: handle some custom packets here?
        forward(clientboundCustomPayloadPacket);
    }

    @Override
    public void handleDisconnect(ClientboundDisconnectPacket clientboundDisconnectPacket) {
        throw new UnsupportedPacketException(clientboundDisconnectPacket);
    }

    @Override
    public void handleResourcePackPush(ClientboundResourcePackPushPacket clientboundResourcePackPushPacket) {
        this.replayServer.pushRemotePack(clientboundResourcePackPushPacket.id(),
            clientboundResourcePackPushPacket.url(), clientboundResourcePackPushPacket.hash());
    }

    @Override
    public void handleResourcePackPop(ClientboundResourcePackPopPacket clientboundResourcePackPopPacket) {
        if (clientboundResourcePackPopPacket.id().isEmpty()) {
            this.replayServer.popAllRemotePacks();
        } else {
            this.replayServer.popRemotePack(clientboundResourcePackPopPacket.id().get());
        }
    }

    @Override
    public void handleUpdateTags(ClientboundUpdateTagsPacket clientboundUpdateTagsPacket) {
        clientboundUpdateTagsPacket.getTags().forEach((resourceKey, networkPayload) -> {
            networkPayload.applyToRegistry(this.replayServer.registryAccess().registryOrThrow(resourceKey));
        });
        forward(clientboundUpdateTagsPacket);
    }

    @Override
    public void handleStoreCookie(ClientboundStoreCookiePacket clientboundStoreCookiePacket) {
        throw new UnsupportedPacketException(clientboundStoreCookiePacket);
    }

    @Override
    public void handleTransfer(ClientboundTransferPacket clientboundTransferPacket) {
        throw new UnsupportedPacketException(clientboundTransferPacket);
    }

    @Override
    public void handleCustomReportDetails(ClientboundCustomReportDetailsPacket clientboundCustomReportDetailsPacket) {
        throw new UnsupportedPacketException(clientboundCustomReportDetailsPacket);
    }

    @Override
    public void handleServerLinks(ClientboundServerLinksPacket clientboundServerLinksPacket) {
        throw new UnsupportedPacketException(clientboundServerLinksPacket);
    }

    @Override
    public void handleRequestCookie(ClientboundCookieRequestPacket clientboundCookieRequestPacket) {
        throw new UnsupportedPacketException(clientboundCookieRequestPacket);
    }

    @Override
    public void handlePongResponse(ClientboundPongResponsePacket clientboundPongResponsePacket) {
        throw new UnsupportedPacketException(clientboundPongResponsePacket);
    }

    @Override
    public void onDisconnect(DisconnectionDetails disconnectionDetails) {
    }

    @Override
    public boolean isAcceptingMessages() {
        return false;
    }

}
