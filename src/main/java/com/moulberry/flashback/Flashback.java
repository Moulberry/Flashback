package com.moulberry.flashback;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.serialization.Lifecycle;
import com.moulberry.flashback.action.*;
import com.moulberry.flashback.command.BetterColorArgument;
import com.moulberry.flashback.compat.simple_voice_chat.SimpleVoiceChatPlayback;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.keyframe.KeyframeRegistry;
import com.moulberry.flashback.keyframe.types.CameraKeyframeType;
import com.moulberry.flashback.keyframe.types.CameraOrbitKeyframeType;
import com.moulberry.flashback.keyframe.types.CameraShakeKeyframeType;
import com.moulberry.flashback.keyframe.types.FOVKeyframeType;
import com.moulberry.flashback.keyframe.types.SpeedKeyframeType;
import com.moulberry.flashback.keyframe.types.TimeOfDayKeyframeType;
import com.moulberry.flashback.keyframe.types.TimelapseKeyframeType;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import com.moulberry.flashback.packet.FlashbackClearParticles;
import com.moulberry.flashback.packet.FinishedServerTick;
import com.moulberry.flashback.packet.FlashbackForceClientTick;
import com.moulberry.flashback.packet.FlashbackInstantlyLerp;
import com.moulberry.flashback.packet.FlashbackRemoteExperience;
import com.moulberry.flashback.packet.FlashbackRemoteFoodData;
import com.moulberry.flashback.packet.FlashbackRemoteSelectHotbarSlot;
import com.moulberry.flashback.packet.FlashbackRemoteSetSlot;
import com.moulberry.flashback.packet.FlashbackVoiceChatSound;
import com.moulberry.flashback.playback.EmptyLevelSource;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.record.Recorder;
import com.moulberry.flashback.record.ReplayExporter;
import com.moulberry.flashback.record.ReplayMarker;
import com.moulberry.flashback.screen.ConfigScreen;
import com.moulberry.flashback.screen.RecoverRecordingsScreen;
import com.moulberry.flashback.screen.SaveReplayScreen;
import com.moulberry.flashback.screen.UnsupportedLoaderScreen;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import com.moulberry.flashback.visuals.ShaderManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.*;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Flashback implements ModInitializer, ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("flashback");

    public static final int MAGIC = 0xD780E884;
    public static Recorder RECORDER = null;
    public static ExportJob EXPORT_JOB = null;
    private static FlashbackConfig config;
    private static Path configDirectory = null;

    private static int delayedStartRecording = 0;
    private static boolean delayedOpenConfig = false;
    private static volatile boolean isInReplay = false;

    private static final List<Path> pendingReplaySave = new ArrayList<>();
    private static final List<Path> pendingReplayRecovery = new ArrayList<>();

    public static ResourceLocation createResourceLocation(String value) {
        return ResourceLocation.fromNamespaceAndPath("flashback", value);
    }

    public static Path getDataDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("flashback");
    }

    public static Path getReplayFolder() {
        return Flashback.getDataDirectory().resolve("replays");
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
        PayloadTypeRegistry.playS2C().register(FinishedServerTick.TYPE,
                StreamCodec.unit(FinishedServerTick.INSTANCE));

        PayloadTypeRegistry.playS2C().register(FlashbackForceClientTick.TYPE, StreamCodec.unit(FlashbackForceClientTick.INSTANCE));
        PayloadTypeRegistry.playS2C().register(FlashbackClearParticles.TYPE, StreamCodec.unit(FlashbackClearParticles.INSTANCE));
        PayloadTypeRegistry.playS2C().register(FlashbackInstantlyLerp.TYPE, StreamCodec.unit(FlashbackInstantlyLerp.INSTANCE));
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteSelectHotbarSlot.TYPE, FlashbackRemoteSelectHotbarSlot.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteExperience.TYPE, FlashbackRemoteExperience.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteFoodData.TYPE, FlashbackRemoteFoodData.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteSetSlot.TYPE, FlashbackRemoteSetSlot.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackVoiceChatSound.TYPE, FlashbackVoiceChatSound.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackAccurateEntityPosition.TYPE, FlashbackAccurateEntityPosition.STREAM_CODEC);
    }

    @Override
	public void onInitializeClient() {
        Path configFolder = FabricLoader.getInstance().getConfigDir().resolve("flashback");

        try {
            Files.createDirectories(configFolder);
        } catch (IOException e) {
            Flashback.LOGGER.error("Failed to create config folder", e);
        }

        config = FlashbackConfig.tryLoadFromFolder(configFolder);

        TempFolderProvider.tryDeleteStaleFolders(TempFolderProvider.TempFolderType.SERVER);

        Path recordingFolder = TempFolderProvider.getTypedTempFolder(TempFolderProvider.TempFolderType.RECORDING);
        if (Files.exists(recordingFolder)) {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(recordingFolder)) {
                Iterator<Path> iterator = directoryStream.iterator();
                while (iterator.hasNext()) {
                    Path folder = iterator.next();

                    if (Files.exists(folder.resolve("metadata.json"))) {
                        pendingReplayRecovery.add(folder);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (pendingReplayRecovery.isEmpty()) {
            TempFolderProvider.tryDeleteStaleFolders(TempFolderProvider.TempFolderType.RECORDING);
        }

        // Delete partial exports
        try {
            FileUtils.deleteDirectory(Path.of("replay_export_temp").toFile());
        } catch (Exception ignored) {}

        this.deleteUnusedReplayStates();

        ActionRegistry.register(ActionNextTick.INSTANCE);
        ActionRegistry.register(ActionGamePacket.INSTANCE);
        ActionRegistry.register(ActionConfigurationPacket.INSTANCE);
        ActionRegistry.register(ActionCreateLocalPlayer.INSTANCE);
        ActionRegistry.register(ActionMoveEntities.INSTANCE);
        ActionRegistry.register(ActionLevelChunkCached.INSTANCE);
        ActionRegistry.register(ActionAccuratePlayerPosition.INSTANCE);

        KeyframeRegistry.register(CameraKeyframeType.INSTANCE);
        KeyframeRegistry.register(CameraOrbitKeyframeType.INSTANCE);
        KeyframeRegistry.register(CameraShakeKeyframeType.INSTANCE);
        KeyframeRegistry.register(FOVKeyframeType.INSTANCE);
        KeyframeRegistry.register(SpeedKeyframeType.INSTANCE);
        KeyframeRegistry.register(TimelapseKeyframeType.INSTANCE);
        KeyframeRegistry.register(TimeOfDayKeyframeType.INSTANCE);

        ClientPlayNetworking.registerGlobalReceiver(FlashbackForceClientTick.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                Minecraft.getInstance().tick();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FlashbackClearParticles.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                Minecraft.getInstance().particleEngine.clearParticles();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FlashbackInstantlyLerp.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
                    if (entity instanceof LivingEntity && !entity.isRemoved() && !(entity instanceof LocalPlayer)) {
                        entity.moveTo(entity.lerpTargetX(), entity.lerpTargetY(), entity.lerpTargetZ(),
                            entity.lerpTargetYRot(), entity.lerpTargetXRot());
                    }
                    entity.setOldPosAndRot();
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteSelectHotbarSlot.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());
                if (entity instanceof Player player) {
                    player.getInventory().selected = payload.slot();
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteExperience.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());
                if (entity instanceof Player player) {
                    player.experienceProgress = payload.experienceProgress();
                    player.totalExperience = payload.totalExperience();
                    player.experienceLevel = payload.experienceLevel();
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteFoodData.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());
                if (entity instanceof Player player) {
                    player.getFoodData().setFoodLevel(payload.foodLevel());
                    player.getFoodData().setSaturation(payload.saturationLevel());
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteSetSlot.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());
                if (entity instanceof Player player) {
                    player.getInventory().setItem(payload.slot(), payload.itemStack());
                }
            }
        });

        if (FabricLoader.getInstance().isModLoaded("voicechat")) {
            ClientPlayNetworking.registerGlobalReceiver(FlashbackVoiceChatSound.TYPE, (payload, context) -> {
                if (Flashback.isInReplay()) {
                    SimpleVoiceChatPlayback.play(payload);
                }
            });
        }

        ClientPlayNetworking.registerGlobalReceiver(FlashbackAccurateEntityPosition.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                AccurateEntityPositionHandler.update(payload);
            }
        });

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(ShaderManager.INSTANCE);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var flashback = ClientCommandManager.literal("flashback");
            flashback.then(ClientCommandManager.literal("start").executes(this::startRecordingReplay));
            flashback.then(ClientCommandManager.literal("finish").executes(this::finishRecordingReplay));
            flashback.then(ClientCommandManager.literal("end").executes(this::finishRecordingReplay));
            flashback.then(ClientCommandManager.literal("config").executes(this::openFlashbackConfig));
            flashback.then(ClientCommandManager.literal("mark")
                .executes(command -> {
                    this.addMarker(command, null, null, null);
                    return 0;
                }).then(ClientCommandManager.argument("color", BetterColorArgument.color()).executes(command -> {
                    int colour = command.getArgument("color", Integer.class);
                    this.addMarker(command, colour, null, null);
                    return 0;
                }).then(ClientCommandManager.argument("savePosition", BoolArgumentType.bool()).executes(command -> {
                    int colour = command.getArgument("color", Integer.class);
                    boolean savePosition = command.getArgument("savePosition", Boolean.class);
                    this.addMarker(command, colour, savePosition, null);
                    return 0;
                }).then(ClientCommandManager.argument("description", StringArgumentType.greedyString()).executes(command -> {
                    int colour = command.getArgument("color", Integer.class);
                    boolean savePosition = command.getArgument("savePosition", Boolean.class);
                    String description = command.getArgument("description", String.class);
                    this.addMarker(command, colour, savePosition, description);
                    return 0;
                })))));
            dispatcher.register(flashback);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!Flashback.isInReplay() && Flashback.getConfig().automaticallyStart && RECORDER == null) {
                delayedStartRecording = 20;
            }
            if (FabricLoader.getInstance().isModLoaded("voicechat")) {
                SimpleVoiceChatPlayback.cleanUp();
            }
        });

        String unsupportedLoader = findUnsupportedLoaders();

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            updateIsInReplay();

            AccurateEntityPositionHandler.tick();

            if (Flashback.isInReplay() && !Flashback.isExporting()) {
                EditorState editorState = EditorStateManager.getCurrent();
                if (editorState != null) {
                    editorState.recordCameraMovement();
                }
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
            if (unsupportedLoader != null &&
                    System.currentTimeMillis() > Flashback.getConfig().nextUnsupportedModLoaderWarning &&
                    canReplaceScreen(Minecraft.getInstance().screen)) {
                String warning = String.format("""
                    You are using an unsupported modloader: %s

                    Do not report crashes, bugs or other issues to Flashback

                    You will not receive support from Flashback

                    If you need assistance, please contact %s
                    """, unsupportedLoader, unsupportedLoader);

                Minecraft.getInstance().setScreen(new UnsupportedLoaderScreen(Minecraft.getInstance().screen,
                    Component.literal("Flashback: Unsupported"), Component.literal(warning)));
            }

            if (!pendingReplayRecovery.isEmpty() && canReplaceScreen(Minecraft.getInstance().screen)) {
                Component title = Component.literal("Flashback: Recovery");
                Component description = Component.empty()
                     .append(Component.literal("Flashback has detected ").append(Component.literal("unfinished recordings\n").withStyle(ChatFormatting.YELLOW)))
                     .append(Component.literal("This is usually because the game closed unexpectedly while recording\n\n"))
                     .append(Component.literal("Unfortunately, up to 5 minutes of gameplay from the end of the recording may be lost\n\n").withStyle(ChatFormatting.RED)
                     .append(Component.literal("Would you like to try to recover the recording?").withStyle(ChatFormatting.GREEN)));
                Minecraft.getInstance().setScreen(new RecoverRecordingsScreen(Minecraft.getInstance().screen, title, description, recover -> {
                    switch (recover) {
                        case RECOVER -> {
                            pendingReplaySave.addAll(pendingReplayRecovery);
                            pendingReplayRecovery.clear();
                        }
                        case SKIP -> {
                            pendingReplayRecovery.clear();
                        }
                        case DELETE -> {
                            TempFolderProvider.tryDeleteStaleFolders(TempFolderProvider.TempFolderType.RECORDING);
                            pendingReplayRecovery.clear();
                        }
                    }
                }));
            }

            if (!pendingReplaySave.isEmpty()) {
                Screen currentScreen = Minecraft.getInstance().screen;
                if (canReplaceScreen(currentScreen)) {
                    Path recordFolder = pendingReplaySave.getFirst();

                    LocalDateTime dateTime = LocalDateTime.now();
                    dateTime = dateTime.withNano(0);
                    Minecraft.getInstance().setScreen(new SaveReplayScreen(Minecraft.getInstance().screen,
                        recordFolder, dateTime.toString()));
                }
            }

            if (Minecraft.getInstance().level != null && delayedStartRecording > 0) {
                IntegratedServer integratedServer = Minecraft.getInstance().getSingleplayerServer();
                if (integratedServer != null && integratedServer.getClass() != IntegratedServer.class) {
                    delayedStartRecording = 0; // Only allow on actual integrated servers, not replay servers or any other custom server a mod might spin up
                } else if (Flashback.getConfig().automaticallyStart && RECORDER == null) {
                    delayedStartRecording -= 1;
                    if (delayedStartRecording == 0) {
                        startRecordingReplay();
                    }
                } else {
                    delayedStartRecording = 0;
                }
            }

            if (delayedOpenConfig) {
                if (Minecraft.getInstance().screen == null) {
                    Minecraft.getInstance().setScreen(new ConfigScreen(null));
                }
                delayedOpenConfig = false;
            }

            updateIsInReplay();
        });
	}

    public static List<String> getReplayIncompatibleMods() {
        List<String> incompatible = new ArrayList<>();
        if (FabricLoader.getInstance().isModLoaded("vmp")) {
            incompatible.add("VeryManyPlayers (vmp)");
        }
        return incompatible;
    }

    private static @Nullable String findUnsupportedLoaders() {
        if (FabricLoader.getInstance().isModLoaded("feather")) {
            return "Feather Client";
        } else {
            return null;
        }
    }

    private static boolean canReplaceScreen(Screen screen) {
        return screen == null || screen instanceof PauseScreen || screen instanceof TitleScreen
            || screen instanceof RealmsMainScreen || screen instanceof JoinMultiplayerScreen;
    }

    private void addMarker(CommandContext<FabricClientCommandSource> command, @Nullable Integer colour, @Nullable Boolean savePosition, @Nullable String description) {
        if (RECORDER == null) {
            command.getSource().sendError(Component.literal("Not recording"));
            return;
        }

        if (colour == null) {
            colour = 0xFF5555;
        }

        ReplayMarker.MarkerPosition position = null;
        if (savePosition == null || savePosition) {
            Entity camera = Minecraft.getInstance().getCameraEntity();
            if (camera != null) {
                position = new ReplayMarker.MarkerPosition(camera.getEyePosition().toVector3f(),
                    camera.level().dimension().toString());
            }
        }

        if (description != null) {
            description = description.trim();
            if (description.isEmpty()) {
                description = null;
            }
        }

        RECORDER.addMarker(new ReplayMarker(colour, position, description));
    }

    private void deleteUnusedReplayStates() {
        Path flashbackDir = Flashback.getDataDirectory();
        Path replayDir = flashbackDir.resolve("replays");
        Path replayStatesDir = flashbackDir.resolve("editor_states");

        if (!Files.exists(replayDir) || !Files.isDirectory(replayDir)) {
            return;
        }
        if (!Files.exists(replayStatesDir) || !Files.isDirectory(replayStatesDir)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            Map<UUID, Path> replayStates = new HashMap<>();

            // Find existing replay states
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(replayStatesDir)) {
                for (Path path : directoryStream) {
                    String filename = path.getFileName().toString();

                    String withoutExtension = null;

                    if (filename.endsWith(".json")) {
                        withoutExtension = filename.substring(0, filename.length() - 5);
                    } else if (filename.endsWith(".json.old")) {
                        withoutExtension = filename.substring(0, filename.length() - 9);
                    }

                    if (withoutExtension != null) {
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(withoutExtension);
                        } catch (Exception ignored) {
                            continue;
                        }

                        replayStates.put(uuid, path);
                    }
                }
            } catch (IOException ignored) {}

            // Find which uuids are still valid because they have replays
            Set<UUID> replayUuids = new HashSet<>();
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(replayDir)) {
                for (Path path : directoryStream) {
                    if (!path.toString().endsWith(".zip")) {
                        continue;
                    }

                    String metadataString = null;

                    try (FileSystem fs = FileSystems.newFileSystem(path)) {
                        Path metadataPath = fs.getPath("/metadata.json");
                        if (Files.exists(metadataPath)) {
                            metadataString = Files.readString(metadataPath);
                        }
                    } catch (IOException ignored) {
                        continue;
                    }

                    if (metadataString != null) {
                        JsonObject metadataJson = new Gson().fromJson(metadataString, JsonObject.class);
                        FlashbackMeta metadata = FlashbackMeta.fromJson(metadataJson);
                        if (metadata != null) {
                            replayUuids.add(metadata.replayIdentifier);
                        }
                    }
                }
            } catch (IOException e) {
                Flashback.LOGGER.error("Unable to iterate replay directory", e);
                return;
            }

            for (Map.Entry<UUID, Path> entry : replayStates.entrySet()) {
                if (!replayUuids.contains(entry.getKey())) {
                    try {
                        Files.deleteIfExists(entry.getValue());
                    } catch (IOException ignored) {}
                }
            }
        }, Util.backgroundExecutor());
    }

    public static FlashbackConfig getConfig() {
        return config;
    }

    @Nullable
    public static ReplayServer getReplayServer() {
        if (Minecraft.getInstance().getSingleplayerServer() instanceof ReplayServer replayServer) {
            return replayServer;
        }
        return null;
    }

    public static void removePendingReplaySave(Path recordFolder) {
        pendingReplaySave.remove(recordFolder);
    }

    public static boolean isExporting() {
        return EXPORT_JOB != null && EXPORT_JOB.isRunning();
    }

    public static void updateIsInReplay() {
        isInReplay = Minecraft.getInstance().getSingleplayerServer() instanceof ReplayServer;
    }

    public static boolean isInReplay() {
        return isInReplay;
    }

    private int startRecordingReplay(CommandContext<FabricClientCommandSource> command) {
        startRecordingReplay();
        return 0;
    }

    private int finishRecordingReplay(CommandContext<FabricClientCommandSource> command) {
        finishRecordingReplay();
        return 0;
    }

    private int openFlashbackConfig(CommandContext<FabricClientCommandSource> command) {
        delayedOpenConfig = true;
        return 0;
    }

    public static void startRecordingReplay() {
        if (RECORDER != null) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.literal("Already Recording"), Component.literal("Cannot start new recording when already recording"));
            return;
        }
        RECORDER = new Recorder(Minecraft.getInstance().player.registryAccess());
        if (Flashback.getConfig().showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.literal("Flashback"), Component.literal("Started recording"));
        }
    }

    public static void pauseRecordingReplay(boolean pause) {
        RECORDER.setPaused(pause);

        if (Flashback.getConfig().showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.literal("Flashback"), Component.literal(pause ? "Paused recording" : "Unpaused recording"));
        }
    }

    public static void cancelRecordingReplay() {
        Recorder recorder = RECORDER;
        RECORDER = null;

        Path recordFolder = recorder.finish();
        try {
            FileUtils.deleteDirectory(recordFolder.toFile());
        } catch (Exception e) {
            Flashback.LOGGER.error("Exception deleting record folder", e);
        }

        if (Flashback.getConfig().showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.literal("Flashback"), Component.literal("Cancelled recording"));
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

        if (Flashback.getConfig().quicksave) {
            Path replayDir = getReplayFolder();

            String filename;
            try {
                LocalDateTime dateTime = LocalDateTime.now();
                dateTime = dateTime.withNano(0);
                filename = FileUtil.findAvailableName(replayDir, dateTime.toString(), ".zip");
            } catch (IOException e) {
                Flashback.LOGGER.error("Error while trying to determine filename", e);
                filename = UUID.randomUUID() + ".zip";
            }

            Path outputFile = replayDir.resolve(filename);
            ReplayExporter.export(recorder.finish(), outputFile, null);
        } else {
            pendingReplaySave.add(recorder.finish());
        }

        if (Flashback.getConfig().showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.literal("Flashback"), Component.literal("Finished recording"));
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
            PackRepository packRepository = ServerPacksSource.createPackRepository(access);

            packRepository.reload();

            GameRules gameRules = new GameRules();
            gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            gameRules.getRule(GameRules.RULE_DOENTITYDROPS).set(false, null);
            gameRules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
            gameRules.getRule(GameRules.RULE_DISABLE_RAIDS).set(true, null);
            gameRules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
            gameRules.getRule(GameRules.RULE_DO_WARDEN_SPAWNING).set(false, null);
            gameRules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);
            gameRules.getRule(GameRules.RULE_DO_VINES_SPREAD).set(false, null);
            gameRules.getRule(GameRules.RULE_DOFIRETICK).set(false, null);
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
