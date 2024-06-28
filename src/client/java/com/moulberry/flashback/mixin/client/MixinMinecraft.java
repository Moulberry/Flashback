package com.moulberry.flashback.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.FlashbackClient;
import com.moulberry.flashback.ReplayVisuals;
import com.moulberry.flashback.keyframe.Keyframes;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.ui.ReplayUI;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Timer;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.Executor;
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
    public abstract float getFrameTime();

    @Inject(method="<init>", at=@At("RETURN"))
    public void init(GameConfig gameConfig, CallbackInfo ci) {
        ReplayUI.init();
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
        if (FlashbackClient.RECORDER != null) {
            FlashbackClient.RECORDER.endTick(false);
        }

        ReplayServer replayServer = FlashbackClient.getReplayServer();

        boolean inReplay = replayServer != null;
        if (inReplay != inReplayLast) {
            inReplayLast = inReplay;
            if (inReplay) {
                ReplayVisuals.setupReplay();
            } else {
                ReplayVisuals.disable();
            }
        }

        if (inReplay && ReplayVisuals.forceEntityLerpTicks > 0) {
            ReplayVisuals.forceEntityLerpTicks -= 1;
            if (replayServer.replayPaused && this.level != null && !this.level.tickRateManager().runsNormally()) {
                for (Entity entity : this.level.entitiesForRendering()) {
                    if (entity == this.player) {
                        continue;
                    }
                    if (entity instanceof LivingEntity livingEntity) {
                        if (livingEntity.lerpSteps > 0) {
                            livingEntity.lerpPositionAndRotationStep(livingEntity.lerpSteps, livingEntity.lerpTargetX(),
                                livingEntity.lerpTargetY(), livingEntity.lerpTargetZ(), livingEntity.lerpTargetYRot(), livingEntity.lerpTargetXRot());
                            --livingEntity.lerpSteps;
                        }
                        if (livingEntity.lerpHeadSteps > 0) {
                            livingEntity.lerpHeadRotationStep(livingEntity.lerpHeadSteps, livingEntity.lerpYHeadRot);
                            --livingEntity.lerpHeadSteps;
                        }
                        livingEntity.setOldPosAndRot();
                        livingEntity.tickHeadTurn(livingEntity.yBodyRot, 0.0f);
                    } else {
                        entity.moveTo(entity.lerpTargetX(), entity.lerpTargetY(), entity.lerpTargetZ(),
                            entity.lerpTargetYRot(), entity.lerpTargetXRot());
                    }
                }
            }
        }
    }

    @Unique
    private volatile boolean forceClientTick = false;

    @Override
    public void flashback$forceClientTick() {
        this.forceClientTick = true;
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Timer;advanceTime(J)I"))
    public int runTick_advanceTime(Timer instance, long l, Operation<Integer> original) {
        int ticks = original.call(instance, l);
        if (this.forceClientTick) {
            this.forceClientTick = false;
            this.particleEngine.clearParticles();
            return Math.max(1, ticks);
        }
        return ticks;
    }

    @Inject(method = "getTickTargetMillis", at = @At("HEAD"), cancellable = true)
    public void getTickTargetMillis(float f, CallbackInfoReturnable<Float> cir) {
        if (FlashbackClient.isInReplay()) {
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
    private final Timer localPlayerTimer = new Timer(20.0f, 0, FloatUnaryOperator.identity());

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V", shift = At.Shift.AFTER))
    public void runTick_runAllTasks(boolean bl, CallbackInfo ci) {
        if (FlashbackClient.EXPORT_JOB != null && !ReplayUI.isActive()) {
            FlashbackClient.EXPORT_JOB.run();
            FlashbackClient.EXPORT_JOB = null;
        }

        if (FlashbackClient.isInReplay()) {
            int localPlayerTicks = localPlayerTimer.advanceTime(Util.getMillis());
            if (this.level != null && this.player != null && !this.player.isPassenger() && !this.player.isRemoved()) {
                localPlayerTicks = Math.min(10, localPlayerTicks);
                for (int i = 0; i < localPlayerTicks; i++) {
                    this.level.tickNonPassenger(this.player);
                }
            }
        }
    }

    @Override
    public float flashback$getLocalPlayerPartialTick() {
        return localPlayerTimer.partialTick;
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;setErrorSection(Ljava/lang/String;)V", ordinal = 1), cancellable = true)
    public void runTick_setErrorSection(boolean bl, CallbackInfo ci) {
        ReplayServer replayServer = FlashbackClient.getReplayServer();
        if (replayServer == null) {
            return;
        }

        if (!replayServer.replayPaused) {
            Keyframes.applyKeyframes((Minecraft) (Object) this, replayServer.getPartialReplayTick());
        }
        if (!replayServer.doClientRendering()) {
            ci.cancel();
        }
    }

    @Override
    public void flashback$startReplayServer(LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem stem) {
        this.disconnect();
        this.progressListener.set(null);
        Instant instant = Instant.now();
        try {
//            levelStorageAccess.saveDataTag((RegistryAccess)worldStem.registries().compositeAccess(), worldStem.worldData());
            Services services = Services.create((YggdrasilAuthenticationService)this.authenticationService, (File)this.gameDirectory);
            services.profileCache().setExecutor((Executor)this);
            SkullBlockEntity.setup(services, (Executor)this);
            GameProfileCache.setUsesAuthentication(false);
            this.singleplayerServer = MinecraftServer.spin(thread -> new ReplayServer((Thread)thread, (Minecraft) (Object) this,
                levelStorageAccess, packRepository, stem, services, i -> {
                StoringChunkProgressListener storingChunkProgressListener = StoringChunkProgressListener.createFromGameruleRadius((int)(i + 0));
                this.progressListener.set(storingChunkProgressListener);
                return ProcessorChunkProgressListener.createStarted(storingChunkProgressListener, this.progressTasks::add);
            }));
            this.isLocalServer = true;
            this.updateReportEnvironment(ReportEnvironment.local());
//            this.quickPlayLog.setWorldData(QuickPlayLog.Type.SINGLEPLAYER, levelStorageAccess.getLevelId(), worldStem.worldData().getLevelName());
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable((Throwable)throwable, (String)"Starting replay server");
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
