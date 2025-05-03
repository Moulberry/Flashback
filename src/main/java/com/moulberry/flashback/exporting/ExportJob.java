package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FreezeSlowdownFormula;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.exporting.taskbar.ITaskbar;
import com.moulberry.flashback.exporting.taskbar.TaskbarHost;
import com.moulberry.flashback.exporting.taskbar.TaskbarManager;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.keyframe.handler.TickrateKeyframeCapture;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.bytedeco.ffmpeg.global.avutil;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.openal.SOFTLoopback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class ExportJob {

    private final ExportSettings settings;

    private boolean running = false;
    private boolean shouldChangeFramebufferSize = false;
    private long lastRenderMillis;
    private long escapeCancelStartMillis = -1;
    private long renderStartTime;

    private final Random particleRandom;

    private boolean showingDebug = false;
    private boolean pressedDebugKey = false;
    private long serverTickTimeNanos = 0;
    private long clientTickTimeNanos = 0;
    private long renderTimeNanos = 0;
    private long encodeTimeNanos = 0;
    private long downloadTimeNanos = 0;
    private boolean patreonLinkClicked = false;

    public int progressCount = 0;
    public int progressOutOf = 0;

    private double currentTickDouble = 0.0;

    private double audioSamples = 0.0;

    private final AtomicBoolean finishedServerTick = new AtomicBoolean(false);

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
        return this.settings.resolutionX() * (this.settings.ssaa() ? 2 : 1);
    }

    public int getHeight() {
        return this.settings.resolutionY() * (this.settings.ssaa() ? 2 : 1);
    }

    public double getCurrentTickDouble() {
        return this.currentTickDouble;
    }

    public Random getParticleRandom() {
        return this.particleRandom;
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

        TaskbarManager.launchTaskbarManager();

        UUID uuid = UUID.randomUUID();

        String tempFileName = "replay_export_temp/" + uuid + "." + this.settings.container().extension();
        Path exportTempFile = Path.of(tempFileName);
        Path exportTempFolder = exportTempFile.getParent();

        TextureTarget infoRenderTarget = null;

        int oldGuiScale = Minecraft.getInstance().options.guiScale().get();

        try {
            Files.createDirectories(exportTempFolder);

            RenderTarget mainTarget = Minecraft.getInstance().mainRenderTarget;
            infoRenderTarget = new TextureTarget(mainTarget.width, mainTarget.height, false, Minecraft.ON_OSX);

            try (VideoWriter encoder = createVideoWriter(this.settings, tempFileName);
                 SaveableFramebufferQueue downloader = new SaveableFramebufferQueue(this.settings.resolutionX(), this.settings.resolutionY())) {
                doExport(encoder, downloader, infoRenderTarget);
            }

            if (this.settings.container() != VideoContainer.PNG_SEQUENCE) {
                Files.move(exportTempFile, this.settings.output(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            this.running = false;
            this.shouldChangeFramebufferSize = false;

            // Reset display size
            Minecraft.getInstance().options.guiScale().set(oldGuiScale);
            Minecraft.getInstance().resizeDisplay();

            // Refreeze server & client
            replayServer.replayPaused = true;
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                level.tickRateManager().setFrozen(true);
            }

            Minecraft.getInstance().getSoundManager().stop();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.0f));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.0f));

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

            if (infoRenderTarget != null) {
                infoRenderTarget.destroyBuffers();
            }
        }
    }

    private static VideoWriter createVideoWriter(ExportSettings settings, String tempFileName) {
        if (settings.container() == VideoContainer.PNG_SEQUENCE) {
            return new PNGSequenceVideoWriter(settings);
        } else {
            return new AsyncFFmpegVideoWriter(settings, tempFileName);
        }
    }

    private void doExport(VideoWriter videoWriter, SaveableFramebufferQueue downloader, TextureTarget infoRenderTarget) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            return;
        }

        Random random = new Random(1000);
        Random mathRandom = this.settings.resetRng() ? Utils.getInternalMathRandom() : null;

        this.setup(replayServer);
        this.updateRandoms(random, mathRandom);

        shouldChangeFramebufferSize = true;
        // Double gui scale if using SSAA which doubles resolution
        if (this.settings.ssaa()) {
            Minecraft.getInstance().options.guiScale().set(Minecraft.getInstance().options.guiScale().get() * 2);
        }
        Minecraft.getInstance().resizeDisplay();

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

            long pauseScreenStart = System.currentTimeMillis();
            while (Minecraft.getInstance().getOverlay() != null || Minecraft.getInstance().screen != null) {
                this.runClientTick(frozen);

                Window window = Minecraft.getInstance().getWindow();
                RenderTarget renderTarget = Minecraft.getInstance().mainRenderTarget;
                renderTarget.bindWrite(true);
                RenderSystem.clear(16640, Minecraft.ON_OSX);
                Minecraft.getInstance().gameRenderer.render(Minecraft.getInstance().timer, true);
                renderTarget.unbindWrite();

                this.shouldChangeFramebufferSize = false;
                renderTarget.blitToScreen(window.getWidth(), window.getHeight(), false);
                window.updateDisplay();
                this.shouldChangeFramebufferSize = true;

                LockSupport.parkNanos("waiting for pause overlay to disappear", 50_000_000L);

                // Force remove screens/overlays after 5s/15s respectively
                long currentTime = System.currentTimeMillis();
                if (pauseScreenStart > currentTime) {
                    pauseScreenStart = currentTime;
                }
                if (currentTime - pauseScreenStart > 5000) {
                    Minecraft.getInstance().setScreen(null);
                }
                if (currentTime - pauseScreenStart > 15000) {
                    Minecraft.getInstance().setOverlay(null);
                }
            }

            this.updateClientFreeze(frozen);

            DeltaTracker.Timer timer = Minecraft.getInstance().timer;
            timer.updateFrozenState(frozen);
            timer.updatePauseState(false);
            timer.deltaTicks = deltaTicksFloat;
            timer.realtimeDeltaTicks = deltaTicksFloat;
            timer.deltaTickResidual = (float) partialClientTick;
            timer.pausedDeltaTickResidual = (float) partialClientTick;

            AccurateEntityPositionHandler.apply(Minecraft.getInstance().level, timer);

            KeyframeHandler keyframeHandler = new MinecraftKeyframeHandler(Minecraft.getInstance());
            this.settings.editorState().applyKeyframes(keyframeHandler, (float)(this.settings.startTick() + currentTickDouble));

            SaveableFramebuffer saveable = downloader.take();
            RenderTarget renderTarget = Minecraft.getInstance().mainRenderTarget;

            renderTarget.bindWrite(true);
            RenderSystem.clear(16640, Minecraft.ON_OSX);

            // Perform rendering
            PerfectFrames.waitUntilFrameReady();
            FogRenderer.setupNoFog();
            RenderSystem.enableCull();

            start = System.nanoTime();
            Minecraft.getInstance().gameRenderer.render(timer, true);
            renderTimeNanos += System.nanoTime() - start;

            renderTarget.unbindWrite();

            boolean cancel;

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

            this.shouldChangeFramebufferSize = false;
            cancel = finishFrame(renderTarget, infoRenderTarget, tickIndex, ticks.size());
            this.shouldChangeFramebufferSize = true;

            submitDownloadedFrames(videoWriter, downloader, false);

            saveable.audioBuffer = audioBuffer;
            downloader.startDownload(renderTarget, saveable, this.settings.ssaa());

            if (cancel) {
                ExportJobQueue.drainingQueue = false;
                break;
            }
        }

        submitDownloadedFrames(videoWriter, downloader, true);
        videoWriter.finish();
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
                    // livingEntity.walkAnimation.stop(); // unsupported in 1.21.1
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
                player.moveTo(position.x, position.y, position.z, this.settings.initialCameraYaw(), this.settings.initialCameraPitch());
                player.setDeltaMovement(Vec3.ZERO);
            }
            this.settings.editorState().applyKeyframes(new MinecraftKeyframeHandler(Minecraft.getInstance()), this.settings.startTick());
            this.runClientTick(false);

            // Ensured replay server is paused at startTick
            this.setServerTickAndWait(replayServer, this.settings.startTick(), true);
            this.runClientTick(false);
        }

        // Remove screen
        minecraft.setScreen(null);
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

        while (minecraft.pollTask()) {}
        this.updateClientFreeze(frozen);
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

        minecraft.getSoundManager().updateSource(Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    private void updateClientFreeze(boolean frozen) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.tickRateManager().setFrozen(frozen);
        }
    }

    private void submitDownloadedFrames(VideoWriter videoWriter, SaveableFramebufferQueue downloader, boolean drain) {
        SaveableFramebufferQueue.DownloadedFrame frame;
        while (true) {
            long start = System.nanoTime();
            frame = downloader.finishDownload(drain);
            downloadTimeNanos += System.nanoTime() - start;

            if (frame == null) {
                break;
            }

            start = System.nanoTime();
            videoWriter.encode(frame.image(), frame.audioBuffer());
            encodeTimeNanos += System.nanoTime() - start;
        }
    }

    private boolean finishFrame(RenderTarget framebuffer, RenderTarget infoRenderTarget, int currentFrame, int totalFrames) {
        boolean cancel = false;

        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastRenderMillis > 1000/60 || currentFrame == totalFrames) {
            this.progressCount = currentFrame;
            this.progressOutOf = totalFrames;

            Window window = Minecraft.getInstance().getWindow();

            {
                GlStateManager._colorMask(true, true, true, false);
                GlStateManager._disableDepthTest();
                GlStateManager._depthMask(false);
                GlStateManager._viewport(0, 0, window.getWidth(), window.getHeight());
                GlStateManager._disableBlend();

                Minecraft minecraft = Minecraft.getInstance();
                ShaderInstance shaderInstance = Objects.requireNonNull(minecraft.gameRenderer.blitShader, "Blit shader not loaded");
                shaderInstance.setSampler("DiffuseSampler", framebuffer.colorTextureId);
                shaderInstance.apply();
                BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
                bufferBuilder.addVertex(0.0F, 0.0F, 0.0F);
                bufferBuilder.addVertex(1.0F, 0.0F, 0.0F);
                bufferBuilder.addVertex(1.0F, 1.0F, 0.0F);
                bufferBuilder.addVertex(0.0F, 1.0F, 0.0F);
                BufferUploader.draw(bufferBuilder.buildOrThrow());
                shaderInstance.clear();
                GlStateManager._depthMask(true);
                GlStateManager._colorMask(true, true, true, true);
            }

            this.lastRenderMillis = currentTime;

            Font font = Minecraft.getInstance().font;
            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            bufferSource.endBatch();
            infoRenderTarget.bindWrite(true);

            RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);

            RenderSystem.clear(16640, Minecraft.ON_OSX);

            float guiScale = 4f;
            int scaledWidth = (int) Math.ceil(infoRenderTarget.width / guiScale);
            int scaledHeight = (int) Math.ceil(infoRenderTarget.height / guiScale);

            Matrix4f matrix4f = new Matrix4f().setOrtho(0.0f, scaledWidth, scaledHeight, 0.0f, 1000.0f, 21000.0f);
            RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);

            Matrix4f matrix = new Matrix4f();
            matrix.translate(0.0f, 0.0f, -11000.0f);

            RenderSystem.disableDepthTest();

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

            boolean debugPressed = GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_F3) != GLFW.GLFW_RELEASE;
            if (pressedDebugKey != debugPressed) {
                pressedDebugKey = debugPressed;
                if (pressedDebugKey) {
                    showingDebug = !showingDebug;
                }
            }

            if (showingDebug) {
                lines.add("ST: " + serverTickTimeNanos/1000000 + ", CT: " + clientTickTimeNanos/1000000);
                lines.add("RT: " + renderTimeNanos/1000000 + ", ET: " + encodeTimeNanos/1000000);
                lines.add("DT: " + downloadTimeNanos/1000000);
            } else {
                lines.add("Press [F3] to show debug info");
            }

            lines.add("");

            if (currentFrame == totalFrames) {
                lines.add("Saving...");
            } else if (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE) != GLFW.GLFW_RELEASE) {
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

            int x = scaledWidth / 2;
            int y = scaledHeight / 2 - font.lineHeight * (lines.size() + 1)/2;
            for (String line : lines) {
                if (line.isEmpty()) {
                    y += font.lineHeight / 2 + 1;
                } else {
                    font.drawInBatch(line, x - font.width(line)/2f, y,
                            -1, true, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
                    y += font.lineHeight;
                }
            }

            double mouseX = ReplayUI.imguiGlfw.rawMouseX / window.getWidth() * scaledWidth;
            double mouseY = ReplayUI.imguiGlfw.rawMouseY / window.getHeight() * scaledHeight;

            y += font.lineHeight / 2 + 1;

            String patreon = "https://www.patreon.com/flashbackmod";
            int patreonWidth = font.width(patreon);
            if (mouseX > x - patreonWidth/2f && mouseX < x + patreonWidth/2f && mouseY > y && mouseY < y + font.lineHeight) {
                font.drawInBatch(Component.literal(patreon).withStyle(ChatFormatting.UNDERLINE), x - patreonWidth/2f, y,
                    -1, true, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);

                if (GLFW.glfwGetMouseButton(window.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != 0) {
                    if (!this.patreonLinkClicked) {
                        this.patreonLinkClicked = true;
                        Util.getPlatform().openUri(patreon);
                    }
                } else {
                    this.patreonLinkClicked = false;
                }
            } else {
                font.drawInBatch(patreon, x - patreonWidth/2f, y,
                    -1, true, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
                this.patreonLinkClicked = false;
            }

            bufferSource.endBatch();
            RenderSystem.enableDepthTest();

            infoRenderTarget.unbindWrite();

            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();
            infoRenderTarget.blitToScreen(window.getWidth(), window.getHeight(), false);
            Minecraft.getInstance().getWindow().updateDisplay();
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
