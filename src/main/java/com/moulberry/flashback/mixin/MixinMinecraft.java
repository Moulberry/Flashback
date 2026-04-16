package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FreezeSlowdownFormula;
import com.moulberry.flashback.combo_options.GlowingOverride;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportJobQueue;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.keyframe.handler.TickrateKeyframeCapture;
import com.moulberry.flashback.sound.FlashbackAudioManager;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.exporting.PerfectFrames;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.util.Util;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.Connection;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft extends ReentrantBlockableEventLoop<Runnable> implements MinecraftExt {

    public MixinMinecraft(String string) {
        super(string, true);
    }

    @Shadow
    public abstract void setScreen(@Nullable Screen screen);

    @Shadow
    @Nullable
    public ClientLevel level;

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Final
    public DeltaTracker.Timer deltaTracker;

    @Shadow
    public long clientTickCount;

    @Shadow @Final public Options options;

    @Shadow @Final public LevelRenderer levelRenderer;

    @Shadow
    protected abstract float getTickTargetMillis(float f);

    @Shadow @Final private Services services;

    @Shadow @Nullable public abstract Entity getCameraEntity();

    @Shadow
    public abstract void doWorldLoad(LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, Optional<GameRules> gameRules, boolean bl);

    @Shadow
    @Final
    private Window window;

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

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void shouldEntityAppearGlowing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            GlowingOverride glowingOverride = editorState.glowingOverride.get(entity.getUUID());

            if (glowingOverride == GlowingOverride.FORCE_GLOW) {
                cir.setReturnValue(true);
            } else if (glowingOverride == GlowingOverride.FORCE_NO_GLOW) {
                cir.setReturnValue(false);
            }
        }
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;updateSource(Lnet/minecraft/client/Camera;)V"))
    public void runTick_updateSource(SoundManager instance, Camera camera, Operation<Void> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            Camera audioCamera = editorState.getAudioCamera();
            if (audioCamera != null) {
                original.call(instance, audioCamera);
                return;
            }
        }

        original.call(instance, camera);
    }

    @Inject(method = "renderFrame", at=@At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V", shift = At.Shift.AFTER))
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

        FlashbackConfigV1 config = Flashback.getConfig();
        if (inReplay && !config.advanced.disableThirdPersonCancel) {
            // Force camera type to first person
            if (ReplayUI.isActive() && this.player != null && this.getCameraEntity() == this.player && this.options.getCameraType() != CameraType.FIRST_PERSON) {
                this.options.setCameraType(CameraType.FIRST_PERSON);
                this.levelRenderer.needsUpdate();

                ReplayUI.setInfoOverlay("Forced perspective to First-Person");
            }
        }
    }

    @Unique
    private int serverTickFreezeDelayStart = -1;
    @Unique
    private double clientTickFreezeDelayStart = -1;

    @Inject(method = "getTickTargetMillis", at = @At("HEAD"), cancellable = true)
    public void getTickTargetMillis(float f, CallbackInfoReturnable<Float> cir) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            if (this.level == null) {
                clientTickFreezeDelayStart = -1;
                serverTickFreezeDelayStart = -1;
                return;
            }

            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && !replayServer.replayPaused) {
                double partialReplayTick = replayServer.getPartialReplayTick();

                TickrateKeyframeCapture capture = new TickrateKeyframeCapture();
                editorState.applyKeyframes(capture, (float) partialReplayTick);

                if (capture.frozen && capture.frozenDelay > 0 && this.deltaTracker instanceof DeltaTracker.Timer timer) {
                    if (clientTickFreezeDelayStart < 0) {
                        clientTickFreezeDelayStart = this.clientTickCount + 1;
                        serverTickFreezeDelayStart = (int) partialReplayTick;

                        TickRateManager tickRateManager = this.level.tickRateManager();
                        tickRateManager.setFrozenTicksToRun(capture.frozenDelay <= 5 ? 1 : 2);
                    }

                    double freezeClientTicks = capture.frozenDelay <= 5 ? 0.999 : 1.999;
                    double freezeDerivative = capture.frozenDelay <= 5 ? 1.0 : 0.5;

                    double deltaFromStart = partialReplayTick - serverTickFreezeDelayStart;

                    if (deltaFromStart >= 0 && deltaFromStart <= capture.frozenDelay) {
                        double freezePowerBase = FreezeSlowdownFormula.getFreezePowerBase(capture.frozenDelay, freezeDerivative);
                        double clientTicks = freezeClientTicks * FreezeSlowdownFormula.calculateFreezeClientTick(deltaFromStart,
                            capture.frozenDelay, freezePowerBase);

                        double currentClientTicks = this.clientTickCount + timer.deltaTickResidual - clientTickFreezeDelayStart;
                        double freezeRate = Math.max(0.01f, Math.min(1f, clientTicks - currentClientTicks));

                        float tickrate = Math.max(1f, capture.tickrate) * (float) freezeRate;
                        cir.setReturnValue(1000f / tickrate);
                        return;
                    }
                } else {
                    clientTickFreezeDelayStart = -1;
                    serverTickFreezeDelayStart = -1;
                }

                TickRateManager tickRateManager = this.level.tickRateManager();
                if (tickRateManager.runsNormally()) {
                    float manualMultiplier = replayServer.getDesiredTickRate(true) / 20.0f;
                    cir.setReturnValue(1000f / Math.max(1f, capture.tickrate * manualMultiplier));
                }
            } else {
                TickRateManager tickRateManager = this.level.tickRateManager();
                if (tickRateManager.runsNormally()) {
                    cir.setReturnValue(tickRateManager.millisecondsPerTick());
                }
            }

        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    public void disconnectHead(Screen screen, boolean isTransferring, boolean stopSounds, CallbackInfo ci) {
        try {
            if (Flashback.getConfig().recordingControls.automaticallyFinish && Flashback.RECORDER != null && !isTransferring) {
                Flashback.finishRecordingReplay();
            }
        } catch (Exception e) {
            Flashback.LOGGER.error("Failed to finish replay on disconnect", e);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("RETURN"))
    public void disconnectReturn(Screen screen, boolean bl, boolean bl2, CallbackInfo ci) {
        Flashback.updateIsInReplay();
    }

    @Unique
    private final DeltaTracker.Timer localPlayerTimer = new DeltaTracker.Timer(20.0f, 0, FloatUnaryOperator.identity());

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
            long millis = Util.getMillis();
            this.localPlayerTimer.advanceRealTime(millis);
            int localPlayerTicks = this.localPlayerTimer.advanceGameTime(millis);
            if (this.flashback$overridingLocalPlayerTimer()) {
                localPlayerTicks = Math.min(10, localPlayerTicks);
                for (int i = 0; i < localPlayerTicks; i++) {
                    this.level.guardEntityTick(this.level::tickNonPassenger, this.player);
                }
            }
        }
    }

    @Override
    public boolean flashback$overridingLocalPlayerTimer() {
        return !Flashback.isExporting() && this.level != null && this.player != null && !this.player.isPassenger() && !this.player.isRemoved() && Math.round(this.getTickTargetMillis(50)) != 50;
    }

    @Override
    public float flashback$getLocalPlayerPartialTick(float originalPartialTick) {
        if (this.getCameraEntity() != this.player || !this.flashback$overridingLocalPlayerTimer()) {
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

    @Inject(method = "runTick", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/Window;setErrorSection(Ljava/lang/String;)V",
        ordinal = 1), cancellable = true)
    public void runTick_setErrorSection(boolean bl, CallbackInfo ci) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            FlashbackAudioManager.stopAll();
            return;
        }

        LocalPlayer player = this.player;
        DeltaTracker deltaTracker = this.deltaTracker;

        if (Flashback.RECORDER != null && player != null) {
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
            Flashback.RECORDER.trackPartialPosition(player, partialTick);
        }

        AccurateEntityPositionHandler.apply(this.level, deltaTracker);

        boolean paused = replayServer.replayPaused;
        boolean forceApplyKeyframes = this.applyKeyframes.compareAndSet(true, false);
        if (paused) {
            FlashbackAudioManager.pauseAll();
        }
        if (!paused || forceApplyKeyframes) {
            if (!paused) {
                FlashbackAudioManager.startHandling();
            }

            try {
                EditorState editorState = EditorStateManager.get(replayServer.getMetadata().replayIdentifier);
                editorState.applyKeyframes(new MinecraftKeyframeHandler((Minecraft) (Object) this), (float) replayServer.getPartialReplayTick());
            } finally {
                if (!paused) {
                    FlashbackAudioManager.finishHandling();
                }
            }
        }
        if (!replayServer.doClientRendering()) {
            ci.cancel();
        }
    }

    @Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
    public void pauseIfInactive(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

    @Unique
    private final ThreadLocal<StartReplayServerInfo> info = new ThreadLocal<>();

    @WrapOperation(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;spin(Ljava/util/function/Function;)Lnet/minecraft/server/MinecraftServer;"))
    public MinecraftServer doWorldLoad_spin(Function<Thread, MinecraftServer> function, Operation<MinecraftServer> original,
            @Local(argsOnly = true) LevelStorageSource.LevelStorageAccess levelStorageAccess, @Local(argsOnly = true) PackRepository packRepository,
            @Local(argsOnly = true) WorldStem stem, @Local(argsOnly = true) Optional<GameRules> gameRules,
            @Local LevelLoadListener levelLoadListener) {
        StartReplayServerInfo info = this.info.get();
        if (info != null) {
            function = thread -> new ReplayServer(thread, (Minecraft) (Object) this,
                levelStorageAccess, packRepository, stem, gameRules, this.services, levelLoadListener, info.playbackUUID(), info.path());
        }
        return original.call(function);
    }

    @Override
    public void flashback$startReplayServer(LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem stem, Optional<GameRules> gameRules, StartReplayServerInfo info) {
        this.info.set(info);
        try {
            this.doWorldLoad(levelStorageAccess, packRepository, stem, gameRules, false);
        } finally {
            this.info.remove();
        }
    }

}
