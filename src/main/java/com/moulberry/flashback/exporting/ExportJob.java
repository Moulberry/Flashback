package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.moulberry.flashback.*;
import com.moulberry.flashback.combo_options.ExportProjection;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.editor.ui.windows.ExportDoneWindow;
import com.moulberry.flashback.exporting.taskbar.TaskbarManager;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.keyframe.handler.TickrateKeyframeCapture;
import com.moulberry.flashback.sound.FlashbackAudioManager;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import com.moulberry.flashback.visuals.FlashbackDrawBuffer;
import com.moulberry.flashback.visuals.WorldRenderHook;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.math3.analysis.function.Min;
import org.bytedeco.ffmpeg.global.avutil;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.openal.SOFTLoopback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class ExportJob {

    private static final Vector4f EMPTY_CLEAR_COLOUR = new Vector4f(0);

    private final ExportSettings settings;

    private boolean running = false;
    private boolean shouldChangeFramebufferSize = false;
    private long lastRenderMillis;
    private long escapeCancelStartMillis = -1;
    private long renderStartTime;

    private final Random particleRandom;
    private long currentClientTickSeed = 0L;
    private long currentInitialEntityTickSeed = 0L;

    private boolean showingDebug = false;
    private boolean pressedDebugKey = false;
    private long serverTickTimeNanos = 0;
    private long clientTickTimeNanos = 0;
    private long renderTimeNanos = 0;
    private long encodeTimeNanos = 0;
    private boolean patreonLinkClicked = false;

    private int extraDummyFrames = 0;

    public int progressCount = 0;
    public int progressOutOf = 0;

    private double currentTickDouble = 0.0;

    private double audioSamples = 0.0;

    private final AtomicBoolean finishedServerTick = new AtomicBoolean(false);

    private NativeImage firstFrame = null;
    private int writtenFrames = 0;

    public static final int SRC_PIXEL_FORMAT = avutil.AV_PIX_FMT_RGBA;

    public ExportJob(ExportSettings settings) {
        this.settings = settings;
        this.particleRandom = this.settings.resetRng() ? new Random(2000) : null;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void onFinishedServerTick() {
        this.finishedServerTick.set(true);
    }

    public boolean shouldChangeFramebufferSize() {
        return this.running && this.shouldChangeFramebufferSize;
    }

    public int getWidth() {
        int resolutionX = this.settings.resolutionX();

        ExportProjection projection = this.settings.projection();
        if (projection == ExportProjection.CUBE_MAP) {
            resolutionX = (resolutionX + 3)/4;
        }

        if (this.settings.ssaa()) {
            resolutionX *= 2;
        }

        return resolutionX;
    }

    public int getHeight() {
        int resolutionY = this.settings.resolutionY();

        ExportProjection projection = this.settings.projection();
        if (projection == ExportProjection.CUBE_MAP) {
            resolutionY = (resolutionY + 2)/3;
        }

        if (this.settings.ssaa()) {
            resolutionY *= 2;
        }

        return resolutionY;
    }

    public double getCurrentTickDouble() {
        return this.currentTickDouble;
    }

    public Random getParticleRandom() {
        return this.particleRandom;
    }

    public long getSeedForCurrentClientTick() {
        return this.currentClientTickSeed;
    }

    public long getInitialEntitySeedForCurrentClientTick() {
        this.currentInitialEntityTickSeed += 1;
        return this.currentInitialEntityTickSeed;
    }

    public ExportSettings getSettings() {
        return this.settings;
    }

    public void run() {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (this.running || replayServer == null) {
            throw new IllegalStateException("run() called twice");
        }
        this.running = true;
        Minecraft.getInstance().mouseHandler.releaseMouse();
        Minecraft.getInstance().getSoundManager().stop();
        Minecraft.getInstance().getSoundManager().tick(true);

        TaskbarManager.launchTaskbarManager();

        UUID uuid = UUID.randomUUID();

        String tempFileName = "replay_export_temp/" + uuid + "." + this.settings.container().extension();
        Path exportTempFile = Path.of(tempFileName);
        Path exportTempFolder = exportTempFile.getParent();

        int oldGuiScale = Minecraft.getInstance().options.guiScale().get();

        this.extraDummyFrames = Flashback.getConfig().exporting.exportRenderDummyFrames;

        try {
            Files.createDirectories(exportTempFolder);

            int resolutionX = this.settings.resolutionX();
            int resolutionY = this.settings.resolutionY();

            if (this.settings.projection() == ExportProjection.CUBE_MAP || this.settings.projection() == ExportProjection.EQUIRECTANGULAR) {
                if (this.settings.projection() == ExportProjection.CUBE_MAP) {
                    resolutionX = (resolutionX + 3)/4;
                    resolutionY = (resolutionY + 2)/3;
                }

                var camera = Minecraft.getInstance().gameRenderer.mainCamera();
                camera.enablePanoramicMode();
            }

            try (VideoWriter encoder = createVideoWriter(this.settings, tempFileName);
                 SaveableFramebufferQueue downloader = new SaveableFramebufferQueue(resolutionX, resolutionY)) {
                doExport(encoder, downloader);
            }

            if (this.settings.container() != VideoContainer.PNG_SEQUENCE) {
                Files.move(exportTempFile, this.settings.output(), StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                long size = 0;
                double duration = this.writtenFrames / this.settings.framerate();

                Path output = this.settings.output();
                if (this.settings.container() != VideoContainer.PNG_SEQUENCE && Files.exists(output) && Files.isRegularFile(output)) {
                    size = Files.size(output);
                }

                ExportDoneWindow.addFinishedExportEntry(new ExportDoneWindow.FinishedExportEntry(this.settings, this.firstFrame, duration, size));
                this.firstFrame = null;
            } catch (IOException ignored) {}
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            this.running = false;
            this.shouldChangeFramebufferSize = false;

            if (this.settings.projection() == ExportProjection.CUBE_MAP || this.settings.projection() == ExportProjection.EQUIRECTANGULAR) {
                Minecraft.getInstance().gameRenderer.mainCamera().disablePanoramicMode();
            }

            // Reset display size
            Minecraft.getInstance().options.guiScale().set(oldGuiScale);
            Minecraft.getInstance().resizeGui();

            // Refreeze server & client
            replayServer.replayPaused = true;
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                level.tickRateManager().setFrozen(true);
            }

            Minecraft.getInstance().getSoundManager().stop();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.0f));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.0f));

            if (this.firstFrame != null) {
                this.firstFrame.close();
                this.firstFrame = null;
            }

            try {
                Files.deleteIfExists(exportTempFile);
            } catch (IOException ignored) {}

            try {
                boolean empty;
                try (var stream = Files.newDirectoryStream(exportTempFolder)) {
                    empty = !stream.iterator().hasNext();
                }
                if (empty) {
                    Files.deleteIfExists(exportTempFolder);
                }
            } catch (IOException ignored) {}
        }
    }

    private static VideoWriter createVideoWriter(ExportSettings settings, String tempFileName) {
        if (settings.container() == VideoContainer.PNG_SEQUENCE) {
            return new PNGSequenceVideoWriter(settings);
        } else {
            return new AsyncFFmpegVideoWriter(settings, tempFileName);
        }
    }

    private void doExport(VideoWriter videoWriter, SaveableFramebufferQueue downloader) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        Random random = new Random(1000);
        Random mathRandom = this.settings.resetRng() ? Utils.getInternalMathRandom() : null;

        this.setup(replayServer);
        this.updateRandoms(random, mathRandom);

        shouldChangeFramebufferSize = true;
        // Double gui scale if using SSAA which doubles resolution
        if (this.settings.ssaa()) {
            mc.options.guiScale().set(mc.options.guiScale().get() * 2);
        }
        mc.resizeGui();

        List<TickInfo> ticks = calculateTicks(this.settings.editorState(), this.settings.startTick(), this.settings.endTick(), this.settings.framerate());

        int clientTickCount = 0;

        this.renderStartTime = System.currentTimeMillis();

        double lastClientTickDouble = 0;

        for (int tickIndex = 0; tickIndex < ticks.size(); tickIndex++) {
            TickInfo tickInfo = ticks.get(tickIndex);
            boolean frozen = tickInfo.frozen;
            this.currentTickDouble = tickInfo.serverTick;
            int targetServerTick = this.settings.startTick() + (int) currentTickDouble;

            float deltaTicksFloat = (float)(tickInfo.clientTick - lastClientTickDouble);
            lastClientTickDouble = tickInfo.clientTick;
            double partialClientTick = tickInfo.clientTick - (int) tickInfo.clientTick;

            // Wait until server is on correct replay tick
            long start = System.nanoTime();
            this.setServerTickAndWait(replayServer, targetServerTick, false);
            serverTickTimeNanos += System.nanoTime() - start;

            // Tick client
            while (clientTickCount < (int) tickInfo.clientTick) {
                start = System.nanoTime();
                this.updateRandoms(random, mathRandom);
                this.runClientTick(frozen);
                clientTickTimeNanos += System.nanoTime() - start;

                clientTickCount += 1;
            }

            this.updateClientFreeze(frozen);

            DeltaTracker.Timer timer = mc.deltaTracker;
            timer.updateFrozenState(frozen);
            timer.updatePauseState(false);
            timer.deltaTicks = deltaTicksFloat;
            timer.realtimeDeltaTicks = deltaTicksFloat;
            timer.deltaTickResidual = (float) partialClientTick;
            timer.pausedDeltaTickResidual = (float) partialClientTick;

            AccurateEntityPositionHandler.apply(mc.level, timer);

            // Apply keyframes
            if (frozen) {
                FlashbackAudioManager.pauseAll();
            } else {
                FlashbackAudioManager.startHandling();
            }
            try {
                KeyframeHandler keyframeHandler = new MinecraftKeyframeHandler(mc);
                this.settings.editorState().applyKeyframes(keyframeHandler, (float)(this.settings.startTick() + currentTickDouble));
            } finally {
                if (!frozen) {
                    FlashbackAudioManager.finishHandling();
                }
            }

            long pauseScreenStart = System.currentTimeMillis();
            int additionalDummyFrames = this.extraDummyFrames;
            while (mc.gui.overlay() != null || mc.gui.screen() != null || additionalDummyFrames > 0) {
                if (mc.gui.overlay() != null || mc.gui.screen() != null) {
                    this.runClientTick(frozen);
                }
                if (additionalDummyFrames > 0) {
                    additionalDummyFrames -= 1;
                }

                RenderTarget renderTarget = mc.gameRenderer.mainRenderTarget();
                render(renderTarget, mc.deltaTracker);

                if (mc.gui.overlay() != null || mc.gui.screen() != null) {
                    LockSupport.parkNanos("waiting for pause overlay to disappear", 50_000_000L);

                    // Force remove screens/overlays after 5s/15s respectively
                    long currentTime = System.currentTimeMillis();
                    if (pauseScreenStart > currentTime) {
                        pauseScreenStart = currentTime;
                    }
                    if (currentTime - pauseScreenStart > 5000) {
                        mc.gui.setScreen(null);
                    }
                    if (currentTime - pauseScreenStart > 15000) {
                        mc.gui.setOverlay(null);
                    }
                }

                this.updateClientFreeze(frozen);

                timer.updateFrozenState(frozen);
                timer.updatePauseState(false);
                timer.deltaTicks = deltaTicksFloat;
                timer.realtimeDeltaTicks = deltaTicksFloat;
                timer.deltaTickResidual = (float) partialClientTick;
                timer.pausedDeltaTickResidual = (float) partialClientTick;
            }

            RenderTarget renderTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();

            ExportProjection projection = this.settings.projection();

            if (projection == ExportProjection.CUBE_MAP || projection == ExportProjection.EQUIRECTANGULAR) {
                var player = mc.player;

                for (int i = 0; i < 6; i++) {
                    if (i < 4) {
                        player.setYRot(90.0f * i - 90.0f);
                        player.setXRot(0.0f);
                    } else if (i == 4) {
                        player.setYRot(0.0f);
                        player.setXRot(-90.0f);
                    } else if (i == 5) {
                        player.setYRot(0.0f);
                        player.setXRot(90.0f);
                    }
                    player.setOldRot();

                    // Perform rendering
                    PerfectFrames.waitUntilFrameReady();
                    start = System.nanoTime();
                    render(renderTarget, timer);
                    renderTimeNanos += System.nanoTime() - start;

                    // Capture audio if necessary
                    FloatBuffer audioBuffer = null;
                    if (this.settings.recordAudio() && i == 0) {
                        long device = Minecraft.getInstance().getSoundManager().soundEngine.library.currentDevice;

                        audioSamples += 48000 / this.settings.framerate();
                        int renderSamples = (int) audioSamples;
                        audioSamples -= renderSamples;

                        int channels = this.settings.stereoAudio() ? 2 : 1;

                        audioBuffer = ByteBuffer.allocateDirect(renderSamples * 4 * channels).order(ByteOrder.nativeOrder()).asFloatBuffer();
                        SOFTLoopback.alcRenderSamplesSOFT(device, audioBuffer, renderSamples);
                    }

                    SaveableFramebuffer saveable = downloader.take();
                    saveable.audioBuffer = audioBuffer;
                    downloader.startDownload(renderTarget, saveable, this.settings.ssaa());
                }
            } else {
                // Perform rendering
                PerfectFrames.waitUntilFrameReady();
                start = System.nanoTime();
                render(renderTarget, timer);
                renderTimeNanos += System.nanoTime() - start;

                // Capture audio if necessary
                FloatBuffer audioBuffer = null;
                if (this.settings.recordAudio()) {
                    long device = Minecraft.getInstance().getSoundManager().soundEngine.library.currentDevice;

                    audioSamples += 48000 / this.settings.framerate();
                    int renderSamples = (int) audioSamples;
                    audioSamples -= renderSamples;

                    int channels = this.settings.stereoAudio() ? 2 : 1;

                    audioBuffer = ByteBuffer.allocateDirect(renderSamples * 4 * channels).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    SOFTLoopback.alcRenderSamplesSOFT(device, audioBuffer, renderSamples);
                }

                SaveableFramebuffer saveable = downloader.take();
                saveable.audioBuffer = audioBuffer;
                downloader.startDownload(renderTarget, saveable, this.settings.ssaa());
            }

            submitDownloadedFrames(videoWriter, downloader, false);

            this.shouldChangeFramebufferSize = false;
            boolean cancel = finishFrame(renderTarget, tickIndex, ticks.size());
            this.shouldChangeFramebufferSize = true;

            if (cancel) {
                ExportJobQueue.drainingQueue = false;
                break;
            }
        }

        submitDownloadedFrames(videoWriter, downloader, true);
        videoWriter.finish();
    }

    private static void render(RenderTarget renderTarget, DeltaTracker.Timer timer) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level != null) {
            minecraft.level.update();
        }
        minecraft.gameRenderer.update(timer);
        minecraft.gameRenderer.extract(timer, true);
        RenderSystem.executePendingTasks();

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        commandEncoder.clearColorAndDepthTextures(renderTarget.getColorTexture(), EMPTY_CLEAR_COLOUR, renderTarget.getDepthTexture(), 1.0);
        minecraft.gameRenderer.render(timer, true);

    }

    private void updateRandoms(Random random, Random mathRandom) {
        if (!this.settings.resetRng()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        long connectionSeed = random.nextLong();
        long levelSeed = random.nextLong();
        long entitySeed = random.nextLong();
        long mathSeed = random.nextLong();
        long particleSeed = random.nextLong();
        this.currentClientTickSeed = random.nextLong();
        this.currentInitialEntityTickSeed = random.nextLong();

        if (minecraft.getConnection() != null) {
            minecraft.getConnection().random.setSeed(connectionSeed);
        }
        if (minecraft.level != null) {
            minecraft.level.random.setSeed(levelSeed);

            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity == null) {
                    continue;
                }

                entity.getRandom().setSeed(entitySeed ^ entity.getUUID().getMostSignificantBits());
            }
        }
        if (mathRandom != null) {
            mathRandom.setSeed(mathSeed);
        }
        this.particleRandom.setSeed(particleSeed);
    }

    private void setup(ReplayServer replayServer) {
        Minecraft minecraft = Minecraft.getInstance();

        replayServer.setDesiredTickRate(20.0f, true);

        if (replayServer.getReplayTick() != this.settings.startTick()) {
            int currentTick = Math.max(0, this.settings.startTick() - 40);

            // Ensure replay server is paused at currentTick
            this.setServerTickAndWait(replayServer, currentTick, true);
            this.runClientTick(false);

            // Clear particles
            minecraft.particleEngine.clearParticles();

            // Reset all walk animations & tick counts
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity instanceof LivingEntity livingEntity) {
                    livingEntity.walkAnimation.stop();
                }
                entity.tickCount = 0;
            }

            // Advance until tick is at start
            while (currentTick < this.settings.startTick()) {
                currentTick += 1;
                this.setServerTickAndWait(replayServer, currentTick, true);
                this.runClientTick(false);
            }

            // Apply initial position and keyframes at start tick
            LocalPlayer player = minecraft.player;
            if (player != null) {
                Vec3 position = this.settings.initialCameraPosition();
                player.snapTo(position.x, position.y, position.z, this.settings.initialCameraYaw(), this.settings.initialCameraPitch());
                player.getInterpolation().cancel();
                player.setDeltaMovement(Vec3.ZERO);
            }
            this.settings.editorState().applyKeyframes(new MinecraftKeyframeHandler(Minecraft.getInstance()), this.settings.startTick());
            this.runClientTick(false);

            // Ensured replay server is paused at startTick
            this.setServerTickAndWait(replayServer, this.settings.startTick(), true);
            this.runClientTick(false);
        }

        // Remove screen
        minecraft.gui.setScreen(null);
    }

    private void setServerTickAndWait(ReplayServer replayServer, int targetTick, boolean force) {
        if (force || replayServer.getReplayTick() != targetTick) {
            this.finishedServerTick.set(false);

            replayServer.jumpToTick = targetTick;
            replayServer.replayPaused = true;
            replayServer.sendFinishedServerTick.set(true);

            while (!this.finishedServerTick.compareAndExchange(true, false)) {
                LockSupport.parkNanos("waiting for server thread", 100000L);
            }
        }
    }

    private void runClientTick(boolean frozen) {
        this.updateClientFreeze(frozen);

        Minecraft minecraft = Minecraft.getInstance();

        minecraft.packetProcessor().processQueuedPackets();
        minecraft.runAllTasks();
        this.updateClientFreeze(frozen);
        if (!frozen) {
            minecraft.getTextureManager().tick();
        }
        minecraft.tick();
        this.updateSoundSource(minecraft);

        this.updateClientFreeze(frozen);
    }

    private void updateSoundSource(Minecraft minecraft) {
        EditorState editorState = this.settings.editorState();
        if (editorState != null) {
            Camera audioCamera = editorState.getAudioCamera();
            if (audioCamera != null) {
                minecraft.getSoundManager().updateSource(audioCamera);
                return;
            }
        }

        minecraft.getSoundManager().updateSource(Minecraft.getInstance().gameRenderer.mainCamera());
    }

    private void updateClientFreeze(boolean frozen) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.tickRateManager().setFrozen(frozen);
        }
    }

    private void submitDownloadedFrames(VideoWriter videoWriter, SaveableFramebufferQueue downloader, boolean drain) {
        while (true) {
            RenderSystem.executePendingTasks();

            if (this.settings.projection() == ExportProjection.CUBE_MAP || this.settings.projection() == ExportProjection.EQUIRECTANGULAR) {
                var frames = downloader.finishDownloadMultiple(6);

                if (frames == null) {
                    if (drain && !downloader.isEmpty()) {
                        LockSupport.parkNanos("waiting for frame to download", 100000L);
                        continue;
                    } else {
                        break;
                    }
                }

                FloatBuffer audioBuffer = null;
                for (SaveableFramebufferQueue.DownloadedFrame frame : frames) {
                    if (frame.audioBuffer() != null) {
                        audioBuffer = frame.audioBuffer();
                        break;
                    }
                }

                int resolutionX = this.settings.resolutionX();
                int resolutionY = this.settings.resolutionY();

                NativeImage target = new NativeImage(resolutionX, resolutionY, true);

                if (this.settings.projection() == ExportProjection.CUBE_MAP) {
                    for (int i = 0; i < frames.length; i++) {
                        int positionX;
                        int positionY;

                        if (i < 4) {
                            positionX = resolutionX * i / 4;
                            positionY = resolutionY / 3;
                        } else if (i == 4) {
                            positionX = resolutionX / 4;
                            positionY = 0;
                        } else if (i == 5) {
                            positionX = resolutionX / 4;
                            positionY = resolutionY * 2 / 3;
                        } else {
                            break;
                        }

                        NativeImage image = frames[i].image();
                        int sizeX = Math.min(image.getWidth(), target.getWidth() - positionX);
                        int sizeY = Math.min(image.getHeight(), target.getHeight() - positionY);
                        image.copyRect(target, 0, 0, positionX, positionY, sizeX, sizeY, false, false);
                    }
                } else {
                    for (int y = 0; y < resolutionY; y++) {
                        for (int x = 0; x < resolutionX; x++) {
                            double yaw = (double) x / resolutionX * 2.0 * Math.PI;
                            double pitch = (double) y / resolutionY * Math.PI - Math.PI/2.0;

                            // Sphere xyz
                            double sx = -Math.sin(yaw) * Math.cos(pitch);
                            double sy = Math.sin(pitch);
                            double sz = -Math.cos(yaw) * Math.cos(pitch);

                            // Cube xyz
                            double a = Math.max(Math.abs(sx), Math.max(Math.abs(sy), Math.abs(sz)));
                            double cx = sx / a;
                            double cy = sy / a;
                            double cz = sz / a;

                            if (cy == -1.0) {
                                NativeImage image = frames[4].image();
                                int imageX = (int) Math.round((cx+1)/2 * (image.getWidth()-1));
                                int imageY = (int) Math.round((cz+1)/2 * (image.getHeight()-1));

                                target.setPixel(x, y, image.getPixel(imageX, imageY));
                            } else if (cy == 1.0) {
                                NativeImage image = frames[5].image();
                                int imageX = (int) Math.round((cx+1)/2 * (image.getWidth()-1));
                                int imageY = image.getHeight()-1 - (int) Math.round((cz+1)/2 * (image.getHeight()-1));

                                target.setPixel(x, y, image.getPixel(imageX, imageY));
                            } else if (cz == -1.0) {
                                NativeImage image = frames[3].image();
                                int imageX = image.getWidth()-1 - (int) Math.round((cx+1)/2 * (image.getWidth()-1));
                                int imageY = (int) Math.round((cy+1)/2 * (image.getHeight()-1));

                                target.setPixel(x, y, image.getPixel(imageX, imageY));
                            } else if (cz == 1.0) {
                                NativeImage image = frames[1].image();
                                int imageX = (int) Math.round((cx+1)/2 * (image.getWidth()-1));
                                int imageY = (int) Math.round((cy+1)/2 * (image.getHeight()-1));

                                target.setPixel(x, y, image.getPixel(imageX, imageY));
                            } else if (cx == 1.0) {
                                NativeImage image = frames[2].image();
                                int imageX = image.getWidth()-1 - (int) Math.round((cz+1)/2 * (image.getWidth()-1));
                                int imageY = (int) Math.round((cy+1)/2 * (image.getHeight()-1));

                                target.setPixel(x, y, image.getPixel(imageX, imageY));
                            } else if (cx == -1.0) {
                                NativeImage image = frames[0].image();
                                int imageX = (int) Math.round((cz+1)/2 * (image.getWidth()-1));
                                int imageY = (int) Math.round((cy+1)/2 * (image.getHeight()-1));

                                target.setPixel(x, y, image.getPixel(imageX, imageY));
                            }
                        }
                    }
                }

                for (SaveableFramebufferQueue.DownloadedFrame frame : frames) {
                    frame.image().close();
                }

                if (this.firstFrame == null) {
                    this.firstFrame = target.mappedCopy(x -> 0xFF000000 | x);
                }
                this.writtenFrames += 1;

                long start = System.nanoTime();
                videoWriter.encode(target, audioBuffer);
                encodeTimeNanos += System.nanoTime() - start;
            } else {
                SaveableFramebufferQueue.DownloadedFrame frame = downloader.finishDownload();

                if (frame == null) {
                    if (drain && !downloader.isEmpty()) {
                        LockSupport.parkNanos("waiting for frame to download", 100000L);
                        continue;
                    } else {
                        break;
                    }
                }

                if (this.firstFrame == null) {
                    this.firstFrame = frame.image().mappedCopy(x -> 0xFF000000 | x);
                }
                this.writtenFrames += 1;

                long start = System.nanoTime();
                videoWriter.encode(frame.image(), frame.audioBuffer());
                encodeTimeNanos += System.nanoTime() - start;
            }
        }
    }

    private static ProjectionMatrixBuffer projectionBuffers = null;
    private static Projection projection;

    private static RenderTarget displayTarget = null;

    private boolean finishFrame(RenderTarget framebuffer, int currentFrame, int totalFrames) {
        RenderSystem.executePendingTasks();
        RenderSystem.pollEvents();

        boolean cancel = false;

        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastRenderMillis > 1000/60 || currentFrame == totalFrames) {
            this.progressCount = currentFrame;
            this.progressOutOf = totalFrames;

            Window window = Minecraft.getInstance().getWindow();

            this.lastRenderMillis = currentTime;

            Font font = Minecraft.getInstance().font;

            int windowFramebufferWidth = WindowSizeTracker.getWidth(window);
            int windowFramebufferHeight = WindowSizeTracker.getHeight(window);

            displayTarget = FramebufferUtils.resizeOrCreateFramebuffer(displayTarget, windowFramebufferWidth, windowFramebufferHeight, false);
            FramebufferUtils.clear(displayTarget, FramebufferUtils.BLACK_CLEAR_COLOUR);

            // Copy framebuffer to display
            float aspectWidth = framebuffer.width / (float) windowFramebufferWidth;
            float aspectHeight = framebuffer.height / (float) windowFramebufferHeight;
            float framebufferX1, framebufferY1, framebufferX2, framebufferY2;
            if (aspectWidth > aspectHeight) {
                framebufferX1 = 0;
                framebufferX2 = 1;
                framebufferY1 = 0.5f - aspectHeight/aspectWidth/2;
                framebufferY2 = 0.5f + aspectHeight/aspectWidth/2;
            } else {
                framebufferY1 = 0;
                framebufferY2 = 1;
                framebufferX1 = 0.5f - aspectWidth/aspectHeight/2;
                framebufferX2 = 0.5f + aspectWidth/aspectHeight/2;
            }
            FramebufferUtils.blitTo(framebuffer.getColorTextureView(), displayTarget, framebufferX1, framebufferY1, framebufferX2, framebufferY2);

            float guiScale = Math.min(windowFramebufferWidth / 420, windowFramebufferHeight / 240);
            int scaledWidth = (int) Math.ceil(windowFramebufferWidth / guiScale);
            int scaledHeight = (int) Math.ceil(windowFramebufferHeight / guiScale);

            if (projectionBuffers == null) {
                projectionBuffers = new ProjectionMatrixBuffer("flashback export");
                projection = new Projection();
                projection.setupOrtho(-1.0f, 1.0f, scaledWidth, scaledHeight, true);
            } else if (projection.width() != scaledWidth || projection.height() != scaledHeight) {
                projection.setSize(scaledWidth, scaledHeight);
            }

            var buffer = projectionBuffers.getBuffer(projection);
            RenderSystem.setProjectionMatrix(buffer, ProjectionType.ORTHOGRAPHIC);

            List<String> lines = new ArrayList<>();

            if (this.settings.name() != null) {
                lines.add(this.settings.name());
                lines.add("");
            }

            lines.add("Exported Frames: " + currentFrame + "/" + totalFrames);

            long elapsed = currentTime - this.renderStartTime;
            lines.add("Time elapsed: " + formatTime(elapsed));

            if (currentFrame >= this.settings.framerate()) {
                long estimatedRemaining = (currentTime - this.renderStartTime) * (totalFrames - currentFrame) / currentFrame;
                lines.add("Estimated time remaining: " + formatTime(estimatedRemaining));
            } else {
                lines.add("Estimated time remaining: ~");
            }

            lines.add("");

            boolean debugPressed = GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_F3) != GLFW.GLFW_RELEASE;
            if (pressedDebugKey != debugPressed) {
                pressedDebugKey = debugPressed;
                if (pressedDebugKey) {
                    showingDebug = !showingDebug;
                }
            }

            if (showingDebug) {
                lines.add("ST: " + serverTickTimeNanos/1000000 + ", CT: " + clientTickTimeNanos/1000000);
                lines.add("RT: " + renderTimeNanos/1000000 + ", ET: " + encodeTimeNanos/1000000);
            } else {
                lines.add("Press [F3] to show debug info");
            }

            lines.add("");

            if (currentFrame == totalFrames) {
                lines.add("Saving...");
            } else if (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_ESCAPE) != GLFW.GLFW_RELEASE) {
                long current = System.currentTimeMillis();
                if (this.escapeCancelStartMillis <= 0 || current < this.escapeCancelStartMillis) {
                    this.escapeCancelStartMillis = current;
                }
                if (current - this.escapeCancelStartMillis > 3000) {
                    cancel = true;
                    lines.add("Saving...");
                } else {
                    long remainingSeconds = 3 - (current - this.escapeCancelStartMillis) / 1000;
                    lines.add("Hold [ESC] to cancel (" + remainingSeconds + "s)");
                }
            } else {
                lines.add("Hold [ESC] to cancel");
                this.escapeCancelStartMillis = -1;
            }

            List<Font.PreparedText> preparedTexts = new ArrayList<>();

            int x = scaledWidth / 2;
            int y = scaledHeight / 2 - font.lineHeight * (lines.size() + 1)/2;
            for (String line : lines) {
                if (line.isEmpty()) {
                    y += font.lineHeight / 2 + 1;
                } else {
                    var prepared = font.prepareText(line, x - font.width(line)/2f, y, -1, true, 0);
                    preparedTexts.add(prepared);
                    y += font.lineHeight;
                }
            }

            double mouseX = ReplayUI.imguiGlfw.rawMouseX / window.getScreenWidth() * scaledWidth;
            double mouseY = ReplayUI.imguiGlfw.rawMouseY / window.getScreenHeight() * scaledHeight;

            y += font.lineHeight / 2 + 1;

            String patreon = "https://www.patreon.com/flashbackmod";
            int patreonWidth = font.width(patreon);
            if (mouseX > x - patreonWidth/2f && mouseX < x + patreonWidth/2f && mouseY > y && mouseY < y + font.lineHeight) {
                var underlined = Component.literal(patreon).withStyle(ChatFormatting.UNDERLINE);
                var prepared = font.prepareText(underlined.getVisualOrderText(), x - patreonWidth/2f, y, -1, true, false, 0);
                preparedTexts.add(prepared);

                if (GLFW.glfwGetMouseButton(window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != 0) {
                    if (!this.patreonLinkClicked) {
                        this.patreonLinkClicked = true;
                        Util.getPlatform().openUri(patreon);
                    }
                } else {
                    this.patreonLinkClicked = false;
                }
            } else {
                var prepared = font.prepareText(patreon, x - patreonWidth/2f, y, -1, true, 0);
                preparedTexts.add(prepared);
                this.patreonLinkClicked = false;
            }

            LinkedHashMap<RenderType, List<TextRenderable>> textRenderablesForType = new LinkedHashMap<>();

            for (Font.PreparedText preparedText : preparedTexts) {
                preparedText.visit(new Font.GlyphVisitor() {
                    @Override
                    public void acceptRenderable(TextRenderable renderable) {
                        var renderType = renderable.renderType(Font.DisplayMode.POLYGON_OFFSET);
                        var renderables = textRenderablesForType.computeIfAbsent(renderType, k -> new ArrayList<>());
                        renderables.add(renderable);
                    }
                });
            }

            try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(1024)) {
                for (var entry : textRenderablesForType.entrySet()) {
                    var renderType = entry.getKey();
                    var renderables = entry.getValue();

                    BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, renderType.primitiveTopology(), renderType.format());

                    var identity = new Matrix4f();
                    for (var renderable : renderables) {
                        renderable.render(identity, bufferBuilder, LightCoordsUtil.FULL_BRIGHT, false);
                    }

                    MeshData meshData = bufferBuilder.build();

                    if (meshData != null) {
                        try (FlashbackDrawBuffer drawBuffer = new FlashbackDrawBuffer(GpuBuffer.USAGE_MAP_WRITE)) {
                            drawBuffer.upload(meshData);
                            PreparedRenderType prepared = renderType.prepare();
                            PreparedRenderType withCustomTarget = new PreparedRenderType(prepared.pipeline(), new OutputTarget("Flashback Info", () -> displayTarget),
                                prepared.dynamicTransforms(), prepared.scissorState(), prepared.textures());
                            drawBuffer.drawRenderType(withCustomTarget);
                        }
                    }
                }
            }

            if (!window.isMinimized()) {
                var windowSurface = Minecraft.getInstance().windowSurface();

                try {
                    GpuSurface.Configuration config = new GpuSurface.Configuration(windowFramebufferWidth, windowFramebufferHeight, GpuSurface.PresentMode.FIFO);
                    windowSurface.configure(config);
                    windowSurface.acquireNextTexture();
                } catch (SurfaceException ignored) {}

                if (windowSurface.isAcquired()) {
                    windowSurface.blitFromTexture(RenderSystem.getDevice().createCommandEncoder(), displayTarget.getColorTextureView());
                }

                RenderSystem.getDevice().createCommandEncoder().submit();

                if (windowSurface.isAcquired()) {
                    windowSurface.present();
                }
            }
        } else {
            RenderSystem.getDevice().createCommandEncoder().submit();
        }

        return cancel;
    }

    private String formatTime(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / 1000 / 60) % 60;
        long hours = millis / 1000 / 60 / 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        } else if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    private record TickInfo(double serverTick, double clientTick, boolean frozen) {}

    private static List<TickInfo> calculateTicks(EditorState editorState, int startTick, int endTick, double fps) {
        List<TickInfo> ticks = new ArrayList<>();

        ticks.add(new TickInfo(0, 0, false));

        double residual = 0;
        int currentTick = 0;

        TickrateKeyframeCapture capture = new TickrateKeyframeCapture();

        int count = endTick - startTick;
        int startFrozen = -1;
        while (currentTick <= count) {
            capture.tickrate = 20.0f;
            capture.frozen = false;
            editorState.applyKeyframes(capture, startTick + currentTick + (float) residual);

            residual += capture.tickrate / fps;

            int roundedResidual = (int) residual;
            residual -= roundedResidual;
            currentTick += roundedResidual;

            if (currentTick > count) {
                break;
            }

            double currentTickDouble = currentTick + residual;
            if (!capture.frozen) {
                startFrozen = -1;
                ticks.add(new TickInfo(currentTickDouble, currentTickDouble, false));
            } else {
                if (startFrozen < 0) {
                    startFrozen = currentTick;
                }

                if (capture.frozenDelay == 0) {
                    ticks.add(new TickInfo(currentTickDouble, Math.min(currentTickDouble, startFrozen), true));
                    continue;
                }

                double deltaFromStart = currentTickDouble - startFrozen;
                double freezeClientTicks = capture.frozenDelay <= 5 ? 0.999 : 1.999;
                double freezeDerivative = capture.frozenDelay <= 5 ? 1.0 : 0.5;
                if (deltaFromStart > capture.frozenDelay) {
                    ticks.add(new TickInfo(currentTickDouble, Math.min(currentTickDouble, startFrozen + freezeClientTicks), true));
                } else if (capture.frozenDelay == 1) {
                    ticks.add(new TickInfo(currentTickDouble, Math.min(currentTickDouble, startFrozen + freezeClientTicks), false));
                } else {
                    double freezePowerBase = FreezeSlowdownFormula.getFreezePowerBase(capture.frozenDelay, freezeDerivative);
                    double clientTicks = freezeClientTicks * FreezeSlowdownFormula.calculateFreezeClientTick(deltaFromStart, capture.frozenDelay, freezePowerBase);
                    ticks.add(new TickInfo(currentTickDouble, Math.min(currentTickDouble, startFrozen + clientTicks), false));
                }
            }
        }

        return ticks;
    }


}
