package com.moulberry.flashback;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Lifecycle;
import com.moulberry.flashback.action.*;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.packet.FinishedServerTick;
import com.moulberry.flashback.playback.EmptyLevelSource;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.Recorder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.*;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Flashback implements ModInitializer, ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("flashback");

    public static final int MAGIC = 0xD780E884;
    public static Recorder RECORDER = null;
    public static ExportJob EXPORT_JOB = null;
    private static Path configDirectory = null;

    public static ResourceLocation createResourceLocation(String value) {
        return ResourceLocation.fromNamespaceAndPath("flashback", value);
    }

    public static Path getConfigDirectory() {
        if (configDirectory == null) {
            configDirectory = FabricLoader.getInstance().getConfigDir().resolve("flashback");
            try {
                Files.createDirectories(configDirectory);
            } catch (Exception e) {
                LOGGER.error("Unable to create directories for config folder", e);
            }
        }
        return configDirectory;
    }

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(FinishedServerTick.TYPE,
                StreamCodec.unit(FinishedServerTick.INSTANCE));
    }

    @Override
	public void onInitializeClient() {
        // todo: try recover unfinished recordings

        TempFolderProvider.tryDeleteStaleFolders();
        try {
            FileUtils.deleteDirectory(Path.of("replay_export_temp").toFile());
        } catch (Exception ignored) {}

        ActionRegistry.register(ActionNextTick.INSTANCE);
        ActionRegistry.register(ActionGamePacket.INSTANCE);
        ActionRegistry.register(ActionConfigurationPacket.INSTANCE);
        ActionRegistry.register(ActionCreateLocalPlayer.INSTANCE);
        ActionRegistry.register(ActionMoveEntities.INSTANCE);
        ActionRegistry.register(ActionLevelChunkCached.INSTANCE);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var flashback = ClientCommandManager.literal("flashback");
            flashback.then(ClientCommandManager.literal("start").executes(this::startRecordingReplay));
            flashback.then(ClientCommandManager.literal("end").executes(this::finishRecordingReplay));
            dispatcher.register(flashback);
        });
	}

    @Nullable
    public static ReplayServer getReplayServer() {
        if (Minecraft.getInstance().getSingleplayerServer() instanceof ReplayServer replayServer) {
            return replayServer;
        }
        return null;
    }

    public static boolean isInReplay() {
        return Minecraft.getInstance().getSingleplayerServer() instanceof ReplayServer;
    }

    private int startRecordingReplay(CommandContext<FabricClientCommandSource> command) {
        startRecordingReplay();
        return 0;
    }

    private int finishRecordingReplay(CommandContext<FabricClientCommandSource> command) {
        finishRecordingReplay();
        return 0;
    }

    public static void startRecordingReplay() {
        if (RECORDER != null) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.literal("Already Recording"), Component.literal("Cannot start new recording when already recording"));
            return;
        }
        RECORDER = new Recorder(Minecraft.getInstance().player.registryAccess());
        if (FlashbackConfig.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.literal("Started Recording"), Component.literal("Started recording"));
        }
    }

    public static void pauseRecordingReplay(boolean pause) {
        RECORDER.setPaused(pause);

        if (FlashbackConfig.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.literal("Paused Recording"), Component.literal("Paused recording"));
        }
    }

    public static void cancelRecordingReplay() {
        Recorder recorder = RECORDER;
        RECORDER = null;
        recorder.finish(null);

        if (FlashbackConfig.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.literal("Cancelled Recording"), Component.literal("Cancelled recording"));
        }
    }

    public static void finishRecordingReplay() {
        if (RECORDER == null) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.literal("Not Recording"), Component.literal("Cannot finish recording when not recording"));
            return;
        }

        Recorder recorder = RECORDER;
        RECORDER = null;
        recorder.endTick(true);

        Path flashbackDir = FabricLoader.getInstance().getGameDir().resolve("flashback");
        Path replayDir = flashbackDir.resolve("replays");

        try {
            Files.createDirectories(replayDir);
        } catch (IOException e) {
            Flashback.LOGGER.error("Error while trying to create replay directory", e);
        }

        String name;

        try {
            LocalDateTime dateTime = LocalDateTime.now();
            dateTime = dateTime.withNano(0);
            name = FileUtil.findAvailableName(replayDir, dateTime.toString(), ".zip");

            Path outputFile = replayDir.resolve(name);
            recorder.finish(outputFile);
        } catch (IOException e) {
            throw SneakyThrow.sneakyThrow(e);
        }

        if (FlashbackConfig.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.literal("Finished Recording"), Component.literal(name));
        }
    }

    @Nullable
    public static AbstractClientPlayer getSpectatingPlayer() {
        if (!isInReplay()) {
            return null;
        }
        if (Minecraft.getInstance().getCameraEntity() instanceof AbstractClientPlayer clientPlayer) {
            if (clientPlayer != Minecraft.getInstance().player) {
                return clientPlayer;
            }
        }
        return null;
    }

    public static void openReplayWorld(Path path) {
        try {
            UUID replayUuid = UUID.randomUUID();
            Path replayTemp = TempFolderProvider.createTemp(TempFolderProvider.TempFolderType.SERVER, replayUuid);
            FileUtils.deleteDirectory(replayTemp.toFile());

            LevelStorageSource source = new LevelStorageSource(replayTemp.resolve("saves"), replayTemp.resolve("backups"),
                Minecraft.getInstance().directoryValidator(), Minecraft.getInstance().getFixerUpper());
            LevelStorageSource.LevelStorageAccess access = source.createAccess("replay");
            PackRepository packRepository = ServerPacksSource.createVanillaTrustedRepository();

            packRepository.reload();

            GameRules gameRules = new GameRules();
            gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            gameRules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);

            WorldDataConfiguration worldDataConfiguration = new WorldDataConfiguration(new DataPackConfig(List.of(), List.of()), FeatureFlags.DEFAULT_FLAGS);
            LevelSettings levelSettings = new LevelSettings("Replay", GameType.SPECTATOR, false, Difficulty.NORMAL, true, gameRules, worldDataConfiguration);
            WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, worldDataConfiguration, false, true);
            WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.DEDICATED, 4);

            WorldStem worldStem = Util.blockUntilDone(executor -> WorldLoader.load(initConfig, dataLoadContext -> {
                Registry<LevelStem> registry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();

                Holder.Reference<Biome> plains = dataLoadContext.datapackWorldgen().registryOrThrow(Registries.BIOME).getHolder(Biomes.PLAINS).get();
                Holder.Reference<DimensionType> overworld = dataLoadContext.datapackWorldgen().registryOrThrow(Registries.DIMENSION_TYPE).getHolder(BuiltinDimensionTypes.OVERWORLD).get();

                WorldDimensions worldDimensions = new WorldDimensions(Map.of(LevelStem.OVERWORLD, new LevelStem(overworld, new EmptyLevelSource(plains))));
                WorldDimensions.Complete complete = worldDimensions.bake(registry);

                return new WorldLoader.DataLoadOutput<>(new PrimaryLevelData(levelSettings, new WorldOptions(0L, false, false),
                    complete.specialWorldProperty(), complete.lifecycle()), complete.dimensionsRegistryAccess());
            }, WorldStem::new, Util.backgroundExecutor(), executor)).get();

            ((MinecraftExt)Minecraft.getInstance()).flashback$startReplayServer(access, packRepository, worldStem, replayUuid, path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
