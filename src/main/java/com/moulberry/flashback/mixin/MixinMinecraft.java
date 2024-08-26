package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportJobQueue;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.exporting.PerfectFrames;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.editor.ui.ReplayUI;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ProcessorChunkProgressListener;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft implements MinecraftExt {

    @Shadow
    public abstract void disconnect();

    @Shadow
    @Final
    private AtomicReference<StoringChunkProgressListener> progressListener;

    @Shadow
    @Final
    private YggdrasilAuthenticationService authenticationService;

    @Shadow
    @Final
    public File gameDirectory;

    @Shadow
    private @Nullable IntegratedServer singleplayerServer;

    @Shadow
    private boolean isLocalServer;

    @Shadow
    public abstract void updateReportEnvironment(ReportEnvironment reportEnvironment);

    @Shadow
    public abstract void setScreen(@Nullable Screen screen);

    @Shadow
    private ProfilerFiller profiler;

    @Shadow
    private @Nullable Overlay overlay;

    @Shadow
    protected abstract void runTick(boolean bl);

    @Shadow
    protected abstract void handleDelayedCrash();

    @Shadow
    public abstract User getUser();

    @Shadow
    private @Nullable Connection pendingConnection;

    @Shadow
    @Final
    private Queue<Runnable> progressTasks;

    @Shadow
    @Nullable
    public ClientLevel level;

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Final
    public ParticleEngine particleEngine;

    @Shadow
    @Nullable
    public Entity cameraEntity;

    @Inject(method="<init>", at=@At("RETURN"))
    public void init(GameConfig gameConfig, CallbackInfo ci) {
        ReplayUI.init();
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    public void pauseGame(boolean bl, CallbackInfo ci) {
        if (Flashback.EXPORT_JOB != null) {
            ci.cancel();
        }
    }

    @Inject(method = "renderNames", at = @At("HEAD"), cancellable = true)
    private static void renderNames(CallbackInfoReturnable<Boolean> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderNametags) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;updateSource(Lnet/minecraft/client/Camera;)V"))
    public void runTick_updateSource(SoundManager instance, Camera camera, Operation<Void> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.audioSourceEntity != null && this.level != null) {
            Entity sourceEntity = this.level.getEntities().get(editorState.audioSourceEntity);
            if (sourceEntity != null) {
                Camera dummyCamera = new Camera();
                dummyCamera.eyeHeight = sourceEntity.getEyeHeight();
                dummyCamera.setup(this.level, sourceEntity, false, false, 1.0f);
                instance.updateSource(dummyCamera);
                return;
            }
        }

        original.call(instance, camera);
    }

    @Inject(method = "runTick", at=@At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V", shift = At.Shift.AFTER))
    public void afterMainBlit(boolean bl, CallbackInfo ci) {
        if (!RenderSystem.isOnRenderThread()) return;
        ReplayUI.drawOverlay();
    }

    @Unique
    private boolean inReplayLast = false;

    @Inject(method = "tick", at = @At("RETURN"))
    public void tick(CallbackInfo ci) {
        if (Flashback.RECORDER != null) {
            Flashback.RECORDER.endTick(false);
        }

        EditorStateManager.saveIfNeeded();

        ReplayServer replayServer = Flashback.getReplayServer();

        boolean inReplay = replayServer != null;
        if (inReplay != inReplayLast) {
            inReplayLast = inReplay;
            if (inReplay) {
                Minecraft.getInstance().options.hideGui = false;
            } else {
                EditorStateManager.reset();
            }
        }
    }

    @Inject(method = "getTickTargetMillis", at = @At("HEAD"), cancellable = true)
    public void getTickTargetMillis(float f, CallbackInfoReturnable<Float> cir) {
        if (Flashback.isInReplay()) {
            if (this.level == null) {
                return;
            }

            TickRateManager tickRateManager = this.level.tickRateManager();
            if (tickRateManager.runsNormally()) {
                cir.setReturnValue(tickRateManager.millisecondsPerTick());
            }
        }
    }

    @Unique
    private final DeltaTracker.Timer localPlayerTimer = new DeltaTracker.Timer(20.0f, 0, FloatUnaryOperator.identity());

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    public void disconnect(Screen screen, boolean isTransferring, CallbackInfo ci) {
        try {
            if (Flashback.getConfig().automaticallyFinish && Flashback.RECORDER != null && !isTransferring) {
                Flashback.finishRecordingReplay();
            }
        } catch (Exception e) {
            Flashback.LOGGER.error("Failed to finish replay on disconnect", e);
        }
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V", shift = At.Shift.AFTER))
    public void runTick_runAllTasks(boolean runTick, CallbackInfo ci) {
        if (ExportJobQueue.drainingQueue) {
            if (ExportJobQueue.queuedJobs.isEmpty()) {
                ExportJobQueue.drainingQueue = false;
            } else if (Flashback.EXPORT_JOB == null) {
                Flashback.EXPORT_JOB = new ExportJob(ExportJobQueue.queuedJobs.removeFirst());
            }
        }

        if (Flashback.EXPORT_JOB != null && !ReplayUI.isActive()) {
            try {
                PerfectFrames.enable();
                Flashback.EXPORT_JOB.run();
            } finally {
                PerfectFrames.disable();
                Flashback.EXPORT_JOB = null;
            }
        }

        if (Flashback.isInReplay()) {
            int localPlayerTicks = localPlayerTimer.advanceTime(Util.getMillis(), runTick);
            if (this.level != null && this.player != null && !this.player.isPassenger() && !this.player.isRemoved()) {
                localPlayerTicks = Math.min(10, localPlayerTicks);
                for (int i = 0; i < localPlayerTicks; i++) {
                    this.level.guardEntityTick(this.level::tickNonPassenger, this.player);
                }
            }
        }
    }

    @Override
    public float flashback$getLocalPlayerPartialTick(float originalPartialTick) {
        if (Flashback.isExporting() || this.cameraEntity != this.player) {
            return originalPartialTick;
        }
        return this.localPlayerTimer.getGameTimeDeltaPartialTick(true);
    }

    @Unique
    private final AtomicBoolean applyKeyframes = new AtomicBoolean(false);

    @Override
    public void flashback$applyKeyframes() {
        this.applyKeyframes.set(true);
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;setErrorSection(Ljava/lang/String;)V", ordinal = 1), cancellable = true)
    public void runTick_setErrorSection(boolean bl, CallbackInfo ci) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            return;
        }

        boolean forceApplyKeyframes = this.applyKeyframes.compareAndSet(true, false);
        if (!replayServer.replayPaused || forceApplyKeyframes) {
            EditorState editorState = EditorStateManager.get(replayServer.getMetadata().replayIdentifier);
            editorState.applyKeyframes(new MinecraftKeyframeHandler((Minecraft) (Object) this), replayServer.getPartialReplayTick());
        }
        if (!replayServer.doClientRendering()) {
            ci.cancel();
        }
    }

    @Override
    public void flashback$startReplayServer(LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem stem,
                                            UUID playbackUUID, Path path) {
        this.disconnect();
        this.progressListener.set(null);
        Instant instant = Instant.now();
        try {
//            levelStorageAccess.saveDataTag((RegistryAccess)worldStem.registries().compositeAccess(), worldStem.worldData());
            Services services = Services.create(this.authenticationService, this.gameDirectory);
            services.profileCache().setExecutor((Executor)this);
            SkullBlockEntity.setup(services, (Executor)this);
            GameProfileCache.setUsesAuthentication(false);
            this.singleplayerServer = MinecraftServer.spin(thread -> new ReplayServer(thread, (Minecraft) (Object) this,
                levelStorageAccess, packRepository, stem, services, i -> {
                StoringChunkProgressListener storingChunkProgressListener = StoringChunkProgressListener.createFromGameruleRadius(i);
                this.progressListener.set(storingChunkProgressListener);
                return ProcessorChunkProgressListener.createStarted(storingChunkProgressListener, this.progressTasks::add);
            }, playbackUUID, path));
            this.isLocalServer = true;
            this.updateReportEnvironment(ReportEnvironment.local());
//            this.quickPlayLog.setWorldData(QuickPlayLog.Type.SINGLEPLAYER, levelStorageAccess.getLevelId(), worldStem.worldData().getLevelName());
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Starting replay server");
//            CrashReportCategory crashReportCategory = crashReport.addCategory("Starting integrated server");
//            crashReportCategory.setDetail("Level ID", (Object)levelStorageAccess.getLevelId());
//            crashReportCategory.setDetail("Level Name", () -> worldStem.worldData().getLevelName());
            throw new ReportedException(crashReport);
        }
        while (this.progressListener.get() == null) {
            Thread.yield();
        }
        LevelLoadingScreen levelLoadingScreen = new LevelLoadingScreen(this.progressListener.get());
        this.setScreen(levelLoadingScreen);
        this.profiler.push("waitForServer");
        while (!this.singleplayerServer.isReady() || this.overlay != null) {
            levelLoadingScreen.tick();
            this.runTick(false);
            try {
                Thread.sleep(16L);
            } catch (InterruptedException crashReport) {
                // empty catch block
            }
            this.handleDelayedCrash();
        }
        this.profiler.pop();
        Duration duration = Duration.between(instant, Instant.now());
        SocketAddress socketAddress = this.singleplayerServer.getConnection().startMemoryChannel();
        Connection connection = Connection.connectToLocalServer(socketAddress);
        connection.initiateServerboundPlayConnection(socketAddress.toString(), 0, new ClientHandshakePacketListenerImpl(connection,
            (Minecraft) (Object) this, null, null, false, duration, component -> {}, null));
        connection.send(new ServerboundHelloPacket(this.getUser().getName(), this.getUser().getProfileId()));
        this.pendingConnection = connection;
    }

}
