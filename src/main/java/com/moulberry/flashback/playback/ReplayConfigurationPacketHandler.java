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
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.ResourceProvider;
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
    private Map<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> pendingRegistryMap = null;
    private Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> pendingTags = null;
    private FeatureFlagSet pendingFeatureFlags = null;
    private List<KnownPack> pendingKnownPacks = null;
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
        List<KnownPack> currentKnownPacks = this.replayServer.getResourceManager().listPacks().flatMap(packResources -> packResources.location().knownPackInfo().stream()).toList();
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

        if (this.pendingKnownPacks != null) {
            Set<KnownPack> currentSet = new HashSet<>(currentKnownPacks);
            Set<KnownPack> pendingSet = new HashSet<>(this.pendingKnownPacks);

            if (!currentSet.equals(pendingSet)) {
                currentKnownPacks = this.pendingKnownPacks;
                synchronizeRegistries = true;
            }

            this.pendingKnownPacks = null;
        }

        if (this.pendingRegistryMap != null && !this.pendingRegistryMap.isEmpty()) {
            Map<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> entries = this.pendingRegistryMap;
            this.pendingRegistryMap = null;

            ResourceProvider resourceProvider = this.replayServer.getResourceManager();
            RegistryAccess accessForLoading = this.replayServer.registries().getAccessForLoading(RegistryLayer.WORLDGEN);

            RegistryAccess.Frozen synchronizedRegistries = RegistryDataLoader.load(entries, resourceProvider, accessForLoading,
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
                        var overriden = synchronizedRegistries.registry(registryEntry.key());
                        if (changedRegistries.contains(registryEntry.key()) && overriden.isPresent()) {
                            registries.add(overriden.get());
                            replacedPartOfLayer = true;
                        } else {
                            if (registryEntry.key() == Registries.LEVEL_STEM) {
                                try {
                                    var dimensionsOpt = synchronizedRegistries.registry(Registries.DIMENSION_TYPE);
                                    var biomesOpt = synchronizedRegistries.registry(Registries.BIOME);
                                    if (dimensionsOpt.isPresent()) {
                                        var dimensions = dimensionsOpt.get();

                                        Holder.Reference<Biome> plains;
                                        if (biomesOpt.isPresent()) {
                                            plains = biomesOpt.get().getHolderOrThrow(Biomes.PLAINS);
                                        } else {
                                            plains = this.replayServer.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
                                        }

                                        var mapped = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable());
                                        for (var key : dimensions.registryKeySet()) {
                                            var stemKey = ResourceKey.create(Registries.LEVEL_STEM, key.location());
                                            var stem = new LevelStem(dimensions.getHolderOrThrow(key), new EmptyLevelSource(plains));
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

        if (this.pendingTags != null && !this.pendingTags.isEmpty()) {
            this.pendingTags.forEach((resourceKey, networkPayload) -> {
                networkPayload.applyToRegistry(this.replayServer.registryAccess().registryOrThrow(resourceKey));
            });
            sendTags = true;
            this.pendingTags = null;
        }

        if (synchronizeRegistries) {
            configurationTasks.add(new SynchronizeRegistriesTask(currentKnownPacks, this.replayServer.registries));
        } else if (sendTags) {
            this.replayServer.getPlayerList().broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.replayServer.registries)));
        }

        if (initialPackets.isEmpty() && configurationTasks.isEmpty()) {
            return;
        }

        configurationTasks.add(new JoinWorldTask());

        Collection<String> selectedPacks = knownPacksToIds(this.replayServer.getPackRepository(), currentKnownPacks);
        this.replayServer.updateRegistry(currentFeatureFlags, selectedPacks, initialPackets, configurationTasks);

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

    private static Collection<String> knownPacksToIds(PackRepository packRepository, Collection<KnownPack> knownPacks) {
        Collection<String> selectedPacks = new ArrayList<>();
        ImmutableMap.Builder<KnownPack, String> builder = ImmutableMap.builder();
        packRepository.getAvailablePacks().forEach(pack -> {
            PackLocationInfo packLocationInfo = pack.location();
            packLocationInfo.knownPackInfo().ifPresent(knownPack -> builder.put(knownPack, packLocationInfo.id()));
        });
        Map<KnownPack, String> knownPackToId = builder.build();
        for (KnownPack knownPack : knownPacks) {
            String selectedId = knownPackToId.get(knownPack);
            if (selectedId != null) {
                selectedPacks.add(selectedId);
            }
        }
        return selectedPacks;
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
        this.pendingRegistryMap.put(clientboundRegistryDataPacket.registry(), clientboundRegistryDataPacket.entries());
    }

    @Override
    public void handleEnabledFeatures(ClientboundUpdateEnabledFeaturesPacket clientboundUpdateEnabledFeaturesPacket) {
        this.dirty = true;
        this.pendingFeatureFlags = FeatureFlags.REGISTRY.fromNames(clientboundUpdateEnabledFeaturesPacket.features());
    }

    @Override
    public void handleSelectKnownPacks(ClientboundSelectKnownPacks clientboundSelectKnownPacks) {
        this.dirty = true;
        this.pendingKnownPacks = clientboundSelectKnownPacks.knownPacks();
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
