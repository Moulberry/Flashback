package com.moulberry.flashback;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.serialization.Lifecycle;
import com.moulberry.flashback.action.*;
import com.moulberry.flashback.combo_options.MarkerColour;
import com.moulberry.flashback.command.BetterColorArgument;
import com.moulberry.flashback.compat.DistantHorizonsSupport;
import com.moulberry.flashback.compat.simple_voice_chat.SimpleVoiceChatPlayback;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.taskbar.TaskbarManager;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.keyframe.KeyframeRegistry;
import com.moulberry.flashback.keyframe.types.*;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import com.moulberry.flashback.packet.FlashbackClearEntities;
import com.moulberry.flashback.packet.FlashbackClearParticles;
import com.moulberry.flashback.packet.FinishedServerTick;
import com.moulberry.flashback.packet.FlashbackForceClientTick;
import com.moulberry.flashback.packet.FlashbackInstantlyLerp;
import com.moulberry.flashback.packet.FlashbackRemoteExperience;
import com.moulberry.flashback.packet.FlashbackRemoteFoodData;
import com.moulberry.flashback.packet.FlashbackRemoteSelectHotbarSlot;
import com.moulberry.flashback.packet.FlashbackRemoteSetSlot;
import com.moulberry.flashback.packet.FlashbackSetBorderLerpStartTime;
import com.moulberry.flashback.packet.FlashbackVoiceChatSound;
import com.moulberry.flashback.playback.EmptyLevelSource;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.record.Recorder;
import com.moulberry.flashback.record.ReplayExporter;
import com.moulberry.flashback.record.ReplayMarker;
import com.moulberry.flashback.screen.RecoverRecordingsScreen;
import com.moulberry.flashback.screen.SaveReplayScreen;
import com.moulberry.flashback.screen.UnsupportedLoaderScreen;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import com.moulberry.lattice.Lattice;
import com.moulberry.lattice.element.LatticeElements;
import com.seibel.distanthorizons.api.DhApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.*;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class Flashback implements ModInitializer, ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("flashback");

    public static final int MAGIC = 0xD780E884;
    public static volatile Recorder RECORDER = null;
    public static ExportJob EXPORT_JOB = null;
    private static FlashbackConfigV1 config;
    public static LatticeElements configElements = null;
    private static Path configDirectory = null;

    private static int delayedStartRecording = 0;
    private static boolean delayedOpenConfig = false;
    private static volatile boolean isInReplay = false;

    public static boolean supportsDistantHorizons = false;

    private static final List<Path> pendingReplaySave = new ArrayList<>();
    private static final List<Path> pendingReplayRecovery = new ArrayList<>();
    private static List<String> pendingUnsupportedModsForRecording = null;

    private static boolean isOpeningReplay = false;

    public static long worldBorderLerpStartTime = -1L;

    public static final KeyMapping createMarker1KeyBind = KeyBindingHelper.registerKeyBinding(new KeyMapping("flashback.keybind.create_marker_1",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "flashback.keybind"));
    public static final KeyMapping createMarker2KeyBind = KeyBindingHelper.registerKeyBinding(new KeyMapping("flashback.keybind.create_marker_2",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "flashback.keybind"));
    public static final KeyMapping createMarker3KeyBind = KeyBindingHelper.registerKeyBinding(new KeyMapping("flashback.keybind.create_marker_3",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "flashback.keybind"));
    public static final KeyMapping createMarker4KeyBind = KeyBindingHelper.registerKeyBinding(new KeyMapping("flashback.keybind.create_marker_4",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "flashback.keybind"));

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
        PayloadTypeRegistry.playS2C().register(FlashbackClearEntities.TYPE, StreamCodec.unit(FlashbackClearEntities.INSTANCE));
        PayloadTypeRegistry.playS2C().register(FlashbackInstantlyLerp.TYPE, StreamCodec.unit(FlashbackInstantlyLerp.INSTANCE));
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteSelectHotbarSlot.TYPE, FlashbackRemoteSelectHotbarSlot.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteExperience.TYPE, FlashbackRemoteExperience.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteFoodData.TYPE, FlashbackRemoteFoodData.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteSetSlot.TYPE, FlashbackRemoteSetSlot.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackVoiceChatSound.TYPE, FlashbackVoiceChatSound.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackAccurateEntityPosition.TYPE, FlashbackAccurateEntityPosition.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackSetBorderLerpStartTime.TYPE, FlashbackSetBorderLerpStartTime.STREAM_CODEC);
    }

    @Override
	public void onInitializeClient() {
        Path configFolder = FabricLoader.getInstance().getConfigDir().resolve("flashback");

        try {
            Files.createDirectories(configFolder);
        } catch (IOException e) {
            Flashback.LOGGER.error("Failed to create config folder", e);
        }

        config = FlashbackConfigV1.tryLoadFromFolder(configFolder);
        configElements = LatticeElements.fromAnnotations(FlashbackTextComponents.FLASHBACK_OPTIONS, config);

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            Minecraft.getInstance().schedule(() -> Lattice.performTest(configElements));
        }

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
        KeyframeRegistry.register(TrackEntityKeyframeType.INSTANCE);
        KeyframeRegistry.register(CameraShakeKeyframeType.INSTANCE);
        KeyframeRegistry.register(FOVKeyframeType.INSTANCE);
        KeyframeRegistry.register(SpeedKeyframeType.INSTANCE);
        KeyframeRegistry.register(TimelapseKeyframeType.INSTANCE);
        KeyframeRegistry.register(TimeOfDayKeyframeType.INSTANCE);
        KeyframeRegistry.register(FreezeKeyframeType.INSTANCE);
        KeyframeRegistry.register(BlockOverrideKeyframeType.INSTANCE);
        KeyframeRegistry.register(AudioKeyframeType.INSTANCE);

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

        ClientPlayNetworking.registerGlobalReceiver(FlashbackClearEntities.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
                    if (entity != null && !(entity instanceof Player)) {
                        entity.discard();
                    }
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FlashbackInstantlyLerp.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
                    if (entity.isInterpolating()) {
                        var interpolation = entity.getInterpolation();
                        entity.snapTo(interpolation.position(), interpolation.yRot(), interpolation.xRot());
                        interpolation.cancel();
                    } else {
                        entity.setOldPosAndRot();
                    }
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteSelectHotbarSlot.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());
                if (entity instanceof Player player) {
                    player.getInventory().setSelectedSlot(payload.slot());
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

        ClientPlayNetworking.registerGlobalReceiver(FlashbackSetBorderLerpStartTime.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                worldBorderLerpStartTime = payload.time();
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var flashback = ClientCommandManager.literal("flashback");
            flashback.then(ClientCommandManager.literal("start").executes(this::startRecordingReplay));
            flashback.then(ClientCommandManager.literal("finish").executes(this::finishRecordingReplay));
            flashback.then(ClientCommandManager.literal("end").executes(this::finishRecordingReplay));
            flashback.then(ClientCommandManager.literal("pause").executes(ctx -> {
                pauseRecordingReplay(true);
                return 0;
            }));
            flashback.then(ClientCommandManager.literal("unpause").executes(ctx -> {
                pauseRecordingReplay(false);
                return 0;
            }));
            flashback.then(ClientCommandManager.literal("config").executes(this::openFlashbackConfig));
            flashback.then(ClientCommandManager.literal("mark")
                .executes(command -> {
                    this.addMarker(null, null, null);
                    return 0;
                }).then(ClientCommandManager.argument("color", BetterColorArgument.color()).executes(command -> {
                    int colour = command.getArgument("color", Integer.class);
                    this.addMarker(colour, null, null);
                    return 0;
                }).then(ClientCommandManager.argument("savePosition", BoolArgumentType.bool()).executes(command -> {
                    int colour = command.getArgument("color", Integer.class);
                    boolean savePosition = command.getArgument("savePosition", Boolean.class);
                    this.addMarker(colour, savePosition, null);
                    return 0;
                }).then(ClientCommandManager.argument("description", StringArgumentType.greedyString()).executes(command -> {
                    int colour = command.getArgument("color", Integer.class);
                    boolean savePosition = command.getArgument("savePosition", Boolean.class);
                    String description = command.getArgument("description", String.class);
                    this.addMarker(colour, savePosition, description);
                    return 0;
                })))));
            dispatcher.register(flashback);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (!Flashback.isInReplay() && !isOpeningReplay) {
                return;
            }

            String hideName = "hide";
            if (dispatcher.findNode(Collections.singleton("hide")) != null) {
                hideName = "hide_flashback";
            }
            var hideEntity = Commands.literal(hideName).then(Commands.argument("targets", EntityArgument.entities()).executes(command -> {
                EditorState editorState = EditorStateManager.getCurrent();
                if (!Flashback.isInReplay() || editorState == null) {
                    command.getSource().sendFailure(Component.translatable("flashback.command_only_inside_replay", Component.literal("hide")));
                    return 0;
                }
                var entities = EntityArgument.getEntities(command, "targets");

                for (Entity entity : entities) {
                    editorState.hideDuringExport.add(entity.getUUID());
                }

                int count = entities.size();
                command.getSource().sendSuccess(() -> Component.translatable("flashback.hide_command.n_entities_hidden", Component.literal(String.valueOf(count))), false);
                return 0;
            }));
            dispatcher.register(hideEntity);

            String showName = "show";
            if (dispatcher.findNode(Collections.singleton("show")) != null) {
                showName = "show_flashback";
            }
            var showEntity = Commands.literal(showName).then(Commands.argument("targets", EntityArgument.entities()).executes(command -> {
                EditorState editorState = EditorStateManager.getCurrent();
                if (!Flashback.isInReplay() || editorState == null) {
                    command.getSource().sendFailure(Component.translatable("flashback.command_only_inside_replay", Component.literal("show")));
                    return 0;
                }
                var entities = EntityArgument.getEntities(command, "targets");

                for (Entity entity : entities) {
                    editorState.hideDuringExport.remove(entity.getUUID());
                }

                int count = entities.size();
                command.getSource().sendSuccess(() -> Component.translatable("flashback.show_command.n_entities_shown", Component.literal(String.valueOf(count))), false);
                return 0;
            }));
            dispatcher.register(showEntity);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!Flashback.isInReplay() && Flashback.getConfig().recordingControls.automaticallyStart && RECORDER == null) {
                delayedStartRecording = 20;
            }
            if (FabricLoader.getInstance().isModLoaded("voicechat")) {
                SimpleVoiceChatPlayback.cleanUp();
            }
        });

        AtomicReference<String> unsupportedLoader = new AtomicReference<>(findUnsupportedLoaders());

        AtomicBoolean synchronizeTickingCanTickClient = new AtomicBoolean(true);
        AtomicBoolean synchronizeTickingCanTickServer = new AtomicBoolean(true);

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            updateIsInReplay();

            AccurateEntityPositionHandler.tick();

            // Fix for camera entity sometimes being incorrect when respawning
            Entity camera = Minecraft.getInstance().cameraEntity;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && camera != null && camera != player) {
                if (camera.isRemoved()) {
                    Entity other = player.level().getEntity(camera.getId());
                    if (other != null && !other.isRemoved()) {
                        Minecraft.getInstance().setCameraEntity(other);
                    }
                }
            }

            Flashback.getConfig().tickDelayedSave();

            synchronizeTickingCanTickServer.set(true);
        });

        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
            if (RECORDER != null && Flashback.config.advanced.synchronizeTicking && minecraft.hasSingleplayerServer()) {
                boolean isLevelLoaded = !(minecraft.screen instanceof ReceivingLevelScreen);
                boolean willRecord = minecraft.level != null && (minecraft.getOverlay() == null || !minecraft.getOverlay().isPauseScreen()) &&
                    !minecraft.isPaused() && !RECORDER.isPaused() && isLevelLoaded;
                while (willRecord && !synchronizeTickingCanTickClient.compareAndSet(true, false)) {
                    LockSupport.parkNanos("flashback synchronized ticking: waiting for server", 100000L);
                }
            }

            if (canReplaceScreen(minecraft.screen)) {
                openNewScreen(unsupportedLoader, minecraft.screen);
            }

            if (minecraft.level != null && delayedStartRecording > 0) {
                IntegratedServer integratedServer = minecraft.getSingleplayerServer();
                if (integratedServer != null && integratedServer.getClass() != IntegratedServer.class) {
                    delayedStartRecording = 0; // Only allow on actual integrated servers, not replay servers or any other custom server a mod might spin up
                } else if (Flashback.getConfig().recordingControls.automaticallyStart && RECORDER == null) {
                    delayedStartRecording -= 1;
                    if (delayedStartRecording == 0) {
                        startRecordingReplay();
                    }
                } else {
                    delayedStartRecording = 0;
                }
            }

            updateIsInReplay();

            if (createMarker1KeyBind.consumeClick()) {
                addMarker(Flashback.config.marker.markerOptions1);
            }
            if (createMarker2KeyBind.consumeClick()) {
                addMarker(Flashback.config.marker.markerOptions2);
            }
            if (createMarker3KeyBind.consumeClick()) {
                addMarker(Flashback.config.marker.markerOptions3);
            }
            if (createMarker4KeyBind.consumeClick()) {
                addMarker(Flashback.config.marker.markerOptions4);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(minecraftServer -> {
            synchronizeTickingCanTickClient.set(true);
        });

        ServerTickEvents.START_SERVER_TICK.register(minecraftServer -> {
            if (RECORDER != null && Flashback.config.advanced.synchronizeTicking) {
                while (!synchronizeTickingCanTickServer.compareAndSet(true, false)) {
                    LockSupport.parkNanos("flashback synchronized ticking: waiting for client", 100000L);
                }
            }
        });

        if (FabricLoader.getInstance().isModLoaded("distanthorizons")) {
            if (DhApi.getApiMajorVersion() >= 4) {
                Flashback.LOGGER.info("DistantHorizons detected. Enabling Flashback+DistantHorizons integration");
                supportsDistantHorizons = true;
                DistantHorizonsSupport.register();
            } else {
                Flashback.LOGGER.error("DistantHorizons is installed, but API version is too low ({}). Disabling integration.", DhApi.getApiMajorVersion());
            }
        }
	}

    private static void openNewScreen(AtomicReference<String> unsupportedLoader, Screen currentScreen) {
        if (unsupportedLoader.get() != null) {
            String loaderName = unsupportedLoader.get();
            unsupportedLoader.set(null);
            if (System.currentTimeMillis() > Flashback.getConfig().internal.nextUnsupportedModLoaderWarning) {
                Component warning = Component.translatable("flashback.unsupported_loader.message", Component.literal(loaderName));

                Minecraft.getInstance().setScreen(new UnsupportedLoaderScreen(currentScreen,
                        Component.translatable("flashback.screen_unsupported"), warning));
                return;
            }
        }

        if (!pendingReplayRecovery.isEmpty()) {
            Component nl = FlashbackTextComponents.NEWLINE;
            Component title = Component.translatable("flashback.screen_recovery");
            Component description = Component.empty()
                    .append(Component.translatable("flashback.recovery1", Component.translatable("flashback.recovery2").withStyle(ChatFormatting.YELLOW))).append(nl)
                    .append(Component.translatable("flashback.recovery3")).append(nl).append(nl)
                    .append(Component.translatable("flashback.recovery4").withStyle(ChatFormatting.RED)).append(nl).append(nl)
                    .append(Component.translatable("flashback.recovery5").withStyle(ChatFormatting.GREEN));
            Minecraft.getInstance().setScreen(new RecoverRecordingsScreen(currentScreen, title, description, recover -> {
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
            return;
        }

        if (!pendingReplaySave.isEmpty()) {
            Path recordFolder = pendingReplaySave.getFirst();

            LocalDateTime dateTime = LocalDateTime.now();
            dateTime = dateTime.withNano(0);
            Minecraft.getInstance().setScreen(new SaveReplayScreen(currentScreen, recordFolder, dateTime.toString()));
            return;
        }

        if (pendingUnsupportedModsForRecording != null) {
            String mods = StringUtils.join(pendingUnsupportedModsForRecording, ", ");
            Component title = Component.translatable("flashback.incompatible_with_recording");
            Component description = Component.translatable("flashback.incompatible_with_recording_description").append(Component.literal(mods).withStyle(ChatFormatting.RED));
            Minecraft.getInstance().setScreen(new AlertScreen(() -> Minecraft.getInstance().setScreen(currentScreen), title, description));
            pendingUnsupportedModsForRecording = null;
            return;
        }

        if (delayedOpenConfig) {
            openConfigScreen(currentScreen);
            delayedOpenConfig = false;
            return;
        }
    }

    public static Screen createConfigScreen(Screen oldScreen) {
        return Lattice.createConfigScreen(configElements, config::saveToDefaultFolder, oldScreen);
    }

    public static void openConfigScreen(Screen oldScreen) {
        Minecraft.getInstance().setScreen(createConfigScreen(oldScreen));
    }

    public static List<String> getReplayIncompatibleMods() {
        List<String> incompatible = new ArrayList<>();
        if (FabricLoader.getInstance().isModLoaded("vmp")) {
            incompatible.add("VeryManyPlayers (vmp)");
        }
        if (FabricLoader.getInstance().isModLoaded("c2me")) {
            incompatible.add("Concurrent Chunk Management Engine (c2me)");
        }
        if (FabricLoader.getInstance().isModLoaded("viafabricplus")) {
            incompatible.add("ViaFabricPlus");
        }
        return incompatible;
    }

    public static List<String> getRecordingIncompatibleMods() {
        List<String> incompatible = new ArrayList<>();
        if (FabricLoader.getInstance().isModLoaded("farsight")) {
            incompatible.add("Farsight");
        }
        if (FabricLoader.getInstance().isModLoaded("viafabricplus")) {
            incompatible.add("ViaFabricPlus");
        }
        if (incompatible.isEmpty()) {
            return null;
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

    private void addMarker(FlashbackConfigV1.SubcategoryMarker.SubcategoryMarkerOptions options) {
        int colour;
        if (options.color == MarkerColour.CUSTOM_RGB) {
            String custom = options.customRGB.replaceAll("[^0-9a-fA-F]", "");
            if (custom.isEmpty()) {
                colour = 0;
            } else {
                colour = Integer.parseInt(custom, 16);
            }
        } else {
            colour = options.color.colour;
        }

        addMarker(colour, options.savePosition, options.description);
    }

    private void addMarker(@Nullable Integer colour, @Nullable Boolean savePosition, @Nullable String description) {
        Minecraft minecraft = Minecraft.getInstance();

        if (RECORDER == null) {
            minecraft.gui.getChat().addMessage(Component.translatable("flashback.mark_command.not_recording").withStyle(ChatFormatting.RED));
            return;
        }

        ReplayMarker.MarkerPosition position = null;
        if (savePosition == null || savePosition) {
            Entity camera = Minecraft.getInstance().getCameraEntity();
            if (camera != null) {
                position = new ReplayMarker.MarkerPosition(camera.getEyePosition().toVector3f(),
                    camera.level().dimension().toString());
            }
        }

        if (description != null && description.isBlank()) {
            description = null;
        }

        String feedback;
        if (description != null) {
            feedback = I18n.get("flashback.mark.added_with_description", description);
        } else if (colour != null) {
            feedback = I18n.get("flashback.mark.added_with_color", Integer.toHexString(colour));
        } else {
            feedback = I18n.get("flashback.mark.added");
        }

        if (position != null) {
            feedback += I18n.get("flashback.mark.added_at", position.position().x, position.position().y, position.position().z);
        }

        if (colour == null) {
            colour = 0xFF5555;
        }

        if (description != null) {
            description = description.trim();
            if (description.isEmpty()) {
                description = null;
            }
        }

        minecraft.gui.getChat().addMessage(Component.literal(feedback));
        RECORDER.addMarker(new ReplayMarker(colour, position, description));
    }

    private void deleteUnusedReplayStates() {
        Path flashbackDir = Flashback.getDataDirectory();
        Path replayDir = Flashback.getReplayFolder();
        Path replayStatesDir = flashbackDir.resolve("editor_states");

        if (!Files.exists(replayDir) || !Files.isDirectory(replayDir)) {
            return;
        }
        if (!Files.exists(replayStatesDir) || !Files.isDirectory(replayStatesDir)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            long currentTime = System.currentTimeMillis();
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

                    BasicFileAttributeView attributeView = Files.getFileAttributeView(path, BasicFileAttributeView.class);
                    BasicFileAttributes basicFileAttributes = attributeView.readAttributes();

                    long lastModified = Math.max(basicFileAttributes.creationTime().toMillis(), basicFileAttributes.lastModifiedTime().toMillis());
                    long timeDifference = Math.abs(currentTime - lastModified);
                    if (timeDifference < Duration.ofDays(30).toMillis()) {
                        continue;
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

            if (replayStates.isEmpty()) {
                return;
            }

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

    public static FlashbackConfigV1 getConfig() {
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

    public static long getVisualMillis() {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            float tick;

            ExportJob exportJob = Flashback.EXPORT_JOB;
            if (exportJob != null) {
                tick = (float) exportJob.getCurrentTickDouble();
            } else {
                tick = (float) replayServer.getPartialReplayTick();
            }

            return (long)(tick * 50L);
        } else {
            return Util.getMillis();
        }
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
            SystemToast.add(Minecraft.getInstance().getToastManager(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.translatable("flashback.toast.already_recording"), Component.translatable("flashback.toast.already_recording_description"));
            return;
        }

        List<String> unsupported = getRecordingIncompatibleMods();
        if (unsupported != null && !unsupported.isEmpty()) {
            pendingUnsupportedModsForRecording = unsupported;
            return;
        }

        RECORDER = new Recorder(Minecraft.getInstance().player.registryAccess());
        if (Flashback.getConfig().recordingControls.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToastManager(), FlashbackSystemToasts.RECORDING_TOAST,
                    FlashbackTextComponents.FLASHBACK, Component.translatable("flashback.toast.started_recording"));
        }
    }

    public static void pauseRecordingReplay(boolean pause) {
        RECORDER.setPaused(pause);

        if (Flashback.getConfig().recordingControls.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToastManager(), FlashbackSystemToasts.RECORDING_TOAST,
                    FlashbackTextComponents.FLASHBACK, Component.translatable(pause ? "flashback.toast.paused_recording" : "flashback.toast.unpaused_recording"));
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

        if (Flashback.getConfig().recordingControls.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToastManager(), FlashbackSystemToasts.RECORDING_TOAST,
                FlashbackTextComponents.FLASHBACK, Component.translatable("flashback.toast.cancelled_recording"));
        }
    }

    public static void finishRecordingReplay() {
        if (RECORDER == null) {
            SystemToast.add(Minecraft.getInstance().getToastManager(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.translatable("flashback.toast.not_recording"), Component.translatable("flashback.toast.cant_finish_when_not_recording"));
            return;
        }

        Recorder recorder = RECORDER;
        RECORDER = null;
        recorder.endTick(true);

        if (Flashback.getConfig().recordingControls.quicksave) {
            Path replayDir = getReplayFolder();

            if (!Files.exists(replayDir)) {
                try {
                    Files.createDirectories(replayDir);
                } catch (IOException ignored) {}
            }

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

        if (Flashback.getConfig().recordingControls.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToastManager(), FlashbackSystemToasts.RECORDING_TOAST,
                FlashbackTextComponents.FLASHBACK, Component.translatable("flashback.toast.finished_recording"));
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

    public static void openReplayFromFileBrowser() {
        String defaultFolder = Flashback.getReplayFolder().toString();
        AsyncFileDialogs.openFileDialog(defaultFolder, "Zip File", "zip").thenAccept(pathStr -> {
            if (pathStr != null) {
                Path path = Path.of(pathStr);
                Minecraft.getInstance().submit(() -> {
                    Flashback.openReplayWorld(path);
                });
            }
        });
    }

    public static void openReplayWorld(Path path) {
        // Disconnect
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.level.disconnect();
        }
        minecraft.disconnect();
        minecraft.setScreen(new TitleScreen());

        ReplayUI.shownRegistryErrorWarning = false;
        ReplayUI.shownPlayerSpawnErrorWarning = false;

        // Add as recent
        String pathStr = path.toString();
        FlashbackConfigV1 config = Flashback.getConfig();
        config.internal.recentReplays.remove(pathStr);
        config.internal.recentReplays.add(0, pathStr);
        if (config.internal.recentReplays.size() > 32) {
            config.internal.recentReplays.remove(config.internal.recentReplays.size() - 1);
        }
        config.delayedSaveToDefaultFolder();

        // Actually load
        try {
            isOpeningReplay = true;

            UUID replayUuid = UUID.randomUUID();
            Path replayTemp = TempFolderProvider.createTemp(TempFolderProvider.TempFolderType.SERVER, replayUuid);
            FileUtils.deleteDirectory(replayTemp.toFile());

            LevelStorageSource source = new LevelStorageSource(replayTemp.resolve("saves"), replayTemp.resolve("backups"),
                Minecraft.getInstance().directoryValidator(), Minecraft.getInstance().getFixerUpper());
            LevelStorageSource.LevelStorageAccess access = source.createAccess("replay");
            PackRepository packRepository = ServerPacksSource.createPackRepository(access);

            packRepository.reload();

            GameRules gameRules = new GameRules(FeatureFlagSet.of());
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

                Holder.Reference<Biome> plains = dataLoadContext.datapackWorldgen().lookupOrThrow(Registries.BIOME).get(Biomes.PLAINS).get();
                Holder.Reference<DimensionType> overworld = dataLoadContext.datapackWorldgen().lookupOrThrow(Registries.DIMENSION_TYPE).get(BuiltinDimensionTypes.OVERWORLD).get();

                WorldDimensions worldDimensions = new WorldDimensions(Map.of(LevelStem.OVERWORLD, new LevelStem(overworld, new EmptyLevelSource(plains))));
                WorldDimensions.Complete complete = worldDimensions.bake(registry);

                return new WorldLoader.DataLoadOutput<>(new PrimaryLevelData(levelSettings, new WorldOptions(0L, false, false),
                    complete.specialWorldProperty(), complete.lifecycle()), complete.dimensionsRegistryAccess());
            }, WorldStem::new, Util.backgroundExecutor(), executor)).get();

            ((MinecraftExt)Minecraft.getInstance()).flashback$startReplayServer(access, packRepository, worldStem, replayUuid, path);

            TaskbarManager.launchTaskbarManager();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            isOpeningReplay = false;
        }
    }
}
