package com.moulberry.flashback.playback;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Lifecycle;
import com.moulberry.flashback.exception.UnsupportedPacketException;
import com.moulberry.flashback.registry.RegistryHelper;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundResetChatPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.config.JoinWorldTask;
import net.minecraft.server.network.config.SynchronizeRegistriesTask;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.*;

public class ReplayConfigurationPacketHandler implements ClientConfigurationPacketListener {

    private final ReplayServer replayServer;
    private Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> pendingRegistryMap = null;
    private Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> pendingTags = null;
    private FeatureFlagSet pendingFeatureFlags = null;
    private boolean pendingResetChat = false;
    private boolean dirty = false;

    public ReplayConfigurationPacketHandler(ReplayServer replayServer) {
        this.replayServer = replayServer;
    }

    public void flushPendingConfiguration() {
        if (!this.dirty) {
            return;
        }
        this.dirty = false;

        boolean sendTags = false;

        List<ConfigurationTask> configurationTasks = new ArrayList<>();
        FeatureFlagSet currentFeatureFlags = this.replayServer.worldData.enabledFeatures();
        boolean synchronizeRegistries = false;

        List<Packet<? super ClientConfigurationPacketListener>> initialPackets = new ArrayList<>();

        // Handle enabled features
        if (this.pendingFeatureFlags != null) {
            if (!currentFeatureFlags.equals(this.pendingFeatureFlags)) {
                currentFeatureFlags = this.pendingFeatureFlags;
                initialPackets.add(new ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(this.pendingFeatureFlags)));
            }
            this.pendingFeatureFlags = null;
        }

        if (this.pendingResetChat) {
            initialPackets.add(ClientboundResetChatPacket.INSTANCE);
            this.pendingResetChat = false;
        }

        List<Registry.PendingTags<?>> pendingTags = new ArrayList<>();

        if (this.pendingTags != null && !this.pendingTags.isEmpty()) {
            this.pendingTags.forEach((resourceKey, networkPayload) -> {
                var registry = this.replayServer.registryAccess().lookupOrThrow(resourceKey);
                var loadResult = networkPayload.resolve(registry);
                if (this.pendingRegistryMap != null) {
                    var entry = this.pendingRegistryMap.get(resourceKey);
                    if (entry != null) {
                        this.pendingRegistryMap.put(resourceKey, new RegistryDataLoader.NetworkedRegistryData(entry.elements(), networkPayload));
                    }
                }
                pendingTags.add(registry.prepareTagReload(loadResult));
            });
            pendingTags.forEach(Registry.PendingTags::apply);
            sendTags = true;
            this.pendingTags = null;
        }

        if (this.pendingRegistryMap != null && !this.pendingRegistryMap.isEmpty()) {
            Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> entries = this.pendingRegistryMap;
            this.pendingRegistryMap = null;

            ResourceManager resourceManager = this.replayServer.getResourceManager();

            List<HolderLookup.RegistryLookup<?>> updatedLookups = TagLoader.buildUpdatedLookups(this.replayServer.registryAccess(), pendingTags);

            RegistryAccess.Frozen synchronizedRegistries = RegistryDataLoader.load(entries, resourceManager, updatedLookups,
                RegistryDataLoader.SYNCHRONIZED_REGISTRIES);

            boolean hasRegistriesChanged = !RegistryHelper.equals(this.replayServer.registryAccess(), synchronizedRegistries, RegistryDataLoader.SYNCHRONIZED_REGISTRIES);

            if (hasRegistriesChanged) {
                var newRegistries = this.replayServer.registries;
                boolean replacedPreviousLayer = false;

                HashSet<ResourceKey<? extends Registry<?>>> changedRegistries = new HashSet<>();
                for (RegistryDataLoader.RegistryData<?> synchronizedRegistry : RegistryDataLoader.SYNCHRONIZED_REGISTRIES) {
                    changedRegistries.add(synchronizedRegistry.key());
                }

                for (RegistryLayer layer : RegistryLayer.values()) {
                    RegistryAccess.Frozen registriesForLayer = this.replayServer.registries.getLayer(layer);

                    List<Registry<?>> registries = new ArrayList<>();

                    boolean replacedPartOfLayer = false;

                    for (RegistryAccess.RegistryEntry<?> registryEntry : registriesForLayer.registries().toList()) {
                        var overriden = synchronizedRegistries.lookup(registryEntry.key());
                        if (changedRegistries.contains(registryEntry.key()) && overriden.isPresent()) {
                            registries.add(overriden.get());
                            replacedPartOfLayer = true;
                        } else {
                            if (registryEntry.key() == Registries.LEVEL_STEM) {
                                try {
                                    var dimensionsOpt = synchronizedRegistries.lookup(Registries.DIMENSION_TYPE);
                                    var biomesOpt = synchronizedRegistries.lookup(Registries.BIOME);
                                    if (dimensionsOpt.isPresent()) {
                                        var dimensions = dimensionsOpt.get();

                                        Holder.Reference<Biome> plains;
                                        if (biomesOpt.isPresent()) {
                                            plains = biomesOpt.get().getOrThrow(Biomes.PLAINS);
                                        } else {
                                            plains = this.replayServer.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
                                        }

                                        var mapped = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable());
                                        for (var key : dimensions.registryKeySet()) {
                                            var stemKey = ResourceKey.create(Registries.LEVEL_STEM, key.location());
                                            var stem = new LevelStem(dimensions.getOrThrow(key), new EmptyLevelSource(plains));
                                            mapped.register(stemKey, stem, RegistrationInfo.BUILT_IN);
                                        }

                                        replacedPartOfLayer = true;
                                        registries.add(mapped.freeze());
                                        continue;
                                    }
                                } catch (Exception ignored) {
                                    registries.add(registryEntry.value());
                                }
                            }

                            registries.add(registryEntry.value());
                        }
                    }

                    if (replacedPartOfLayer) {
                        replacedPreviousLayer = true;
                        var finalRegistries = new RegistryAccess.ImmutableRegistryAccess(registries);
                        newRegistries = newRegistries.replaceFrom(layer, finalRegistries.freeze());
                    } else if (replacedPreviousLayer) {
                        newRegistries = newRegistries.replaceFrom(layer, registriesForLayer);
                    }
                }

                if (newRegistries != this.replayServer.registries) {
                    this.replayServer.registries.keys = newRegistries.keys;
                    this.replayServer.registries.values = newRegistries.values;
                    ((RegistryAccess.ImmutableRegistryAccess)this.replayServer.registries.composite).registries =
                        ((RegistryAccess.ImmutableRegistryAccess)newRegistries.composite).registries;

                    synchronizeRegistries = true;
                }
            }
        }

        if (synchronizeRegistries) {
            configurationTasks.add(new SynchronizeRegistriesTask(List.of(), this.replayServer.registries));
        } else if (sendTags) {
            this.replayServer.getPlayerList().broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.replayServer.registries)));
        }

        if (initialPackets.isEmpty() && configurationTasks.isEmpty()) {
            return;
        }

        configurationTasks.add(new JoinWorldTask());

        this.replayServer.updateRegistry(currentFeatureFlags, pendingTags, initialPackets, configurationTasks);

        // Remove all players
        for (ServerPlayer player : new ArrayList<>(this.replayServer.getPlayerList().getPlayers())) {
            player.discard();
        }

        // Remove all levels
        for (ServerLevel value : this.replayServer.levels.values()) {
            this.replayServer.closeLevel(value);
        }
        this.replayServer.levels.clear();

        // Recreate levels
        this.replayServer.loadLevel();
    }

    @Override
    public void handleConfigurationFinished(ClientboundFinishConfigurationPacket clientboundFinishConfigurationPacket) {
        throw new UnsupportedPacketException(clientboundFinishConfigurationPacket);
    }

    @Override
    public void handleRegistryData(ClientboundRegistryDataPacket clientboundRegistryDataPacket) {
        this.dirty = true;
        if (this.pendingRegistryMap == null) {
            this.pendingRegistryMap = new HashMap<>();
        }
        this.pendingRegistryMap.put(clientboundRegistryDataPacket.registry(),
                new RegistryDataLoader.NetworkedRegistryData(clientboundRegistryDataPacket.entries(), TagNetworkSerialization.NetworkPayload.EMPTY));
    }

    @Override
    public void handleEnabledFeatures(ClientboundUpdateEnabledFeaturesPacket clientboundUpdateEnabledFeaturesPacket) {
        this.dirty = true;
        this.pendingFeatureFlags = FeatureFlags.REGISTRY.fromNames(clientboundUpdateEnabledFeaturesPacket.features());
    }

    @Override
    public void handleSelectKnownPacks(ClientboundSelectKnownPacks clientboundSelectKnownPacks) {
    }

    @Override
    public void handleResetChat(ClientboundResetChatPacket clientboundResetChatPacket) {
        this.dirty = true;
        this.pendingResetChat = true;
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
        this.dirty = true;
        if (this.pendingTags == null) {
            this.pendingTags = new HashMap<>();
        }
        this.pendingTags.putAll(clientboundUpdateTagsPacket.getTags());
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
    public void onDisconnect(DisconnectionDetails disconnectionDetails) {

    }

    @Override
    public boolean isAcceptingMessages() {
        return false;
    }
}
