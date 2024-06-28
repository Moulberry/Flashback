package com.moulberry.flashback;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Lifecycle;
import com.moulberry.flashback.action.ActionConfigurationPacket;
import com.moulberry.flashback.action.ActionCreateLocalPlayer;
import com.moulberry.flashback.action.ActionGamePacket;
import com.moulberry.flashback.action.ActionMoveEntities;
import com.moulberry.flashback.action.ActionNextTick;
import com.moulberry.flashback.action.ActionRegistry;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.playback.EmptyLevelSource;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.Recorder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlashbackClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("flashback");

    public static final int MAGIC = 0xD780E884;
    public static Recorder RECORDER = null;
    public static ExportJob EXPORT_JOB = null;

    public static ResourceLocation createResourceLocation(String value) {
        return new ResourceLocation("flashback", value);
    }

	@Override
	public void onInitializeClient() {
        ActionRegistry.register(ActionNextTick.INSTANCE);
        ActionRegistry.register(ActionGamePacket.INSTANCE);
        ActionRegistry.register(ActionConfigurationPacket.INSTANCE);
        ActionRegistry.register(ActionCreateLocalPlayer.INSTANCE);
        ActionRegistry.register(ActionMoveEntities.INSTANCE);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var flashback = ClientCommandManager.literal("flashback");
            flashback.then(ClientCommandManager.literal("start").executes(this::startReplay));
            flashback.then(ClientCommandManager.literal("end").executes(this::endReplay));
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

    private int startReplay(CommandContext<FabricClientCommandSource> command) {
        Recorder recorder = new Recorder(Minecraft.getInstance().player.registryAccess());
        recorder.writeInitial();
        RECORDER = recorder;
        return 0;
    }

    private int endReplay(CommandContext<FabricClientCommandSource> command) {
        Recorder recorder = RECORDER;
        RECORDER = null;
        recorder.endTick(true);
        recorder.export(FabricLoader.getInstance().getGameDir().resolve("debug_recording.zip"));
        return 0;
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

    // todo: delete on start
    public static Path getReplayServerTemp() {
        return FabricLoader.getInstance().getGameDir().resolve("replay_server_temp");
    }

    // todo: export any partial recordings + delete on start
    public static Path getReplayRecordTemp(UUID uuid) {
        return FabricLoader.getInstance().getGameDir().resolve("replay_record_temp")
            .resolve(uuid.toString());
    }

    // todo: delete on start
    public static Path getReplayPlaybackTemp(UUID uuid) {
        return FabricLoader.getInstance().getGameDir().resolve("replay_playback_temp")
            .resolve(uuid.toString());
    }

    public static void testReplayWorld() {
        try {
            Path replayTemp = getReplayServerTemp();
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

            ((MinecraftExt)Minecraft.getInstance()).flashback$startReplayServer(access, packRepository, worldStem);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
