package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.moulberry.flashback.FixedDeltaTracker;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateCache;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    private boolean showingDebug = false;
    private boolean pressedDebugKey = false;
    private long serverTickTimeNanos = 0;
    private long clientTickTimeNanos = 0;
    private long renderTimeNanos = 0;
    private long encodeTimeNanos = 0;
    private long downloadTimeNanos = 0;

    private final AtomicBoolean finishedServerTick = new AtomicBoolean(false);

    public ExportJob(ExportSettings settings) {
        this.settings = settings;
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
        return this.settings.resolutionX();
    }

    public int getHeight() {
        return this.settings.resolutionY();
    }

    public void run() {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (this.running || replayServer == null) {
            throw new IllegalStateException("run() called twice");
        }
        this.running = true;

        UUID uuid = UUID.randomUUID();

        String tempFileName = "replay_export_temp/" + uuid + "." + this.settings.container().extension();
        Path exportTempFile = Path.of(tempFileName);
        Path exportTempFolder = exportTempFile.getParent();

        TextureTarget infoRenderTarget = null;

        try {
            Files.createDirectories(exportTempFolder);

            RenderTarget mainTarget = Minecraft.getInstance().mainRenderTarget;
            infoRenderTarget = new TextureTarget(mainTarget.width, mainTarget.height, false, Minecraft.ON_OSX);

            try (AsyncVideoEncoder encoder = new AsyncVideoEncoder(this.settings, tempFileName);
                    SaveableFramebufferQueue downloader = new SaveableFramebufferQueue(this.settings.resolutionX(), this.settings.resolutionY())) {
                doExport(encoder, downloader, infoRenderTarget);
            }

            Files.move(exportTempFile, this.settings.output(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            this.running = false;
            this.shouldChangeFramebufferSize = false;
            Minecraft.getInstance().resizeDisplay();

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

    private void doExport(AsyncVideoEncoder encoder, SaveableFramebufferQueue downloader, TextureTarget infoRenderTarget) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            return;
        }

        this.setup(replayServer);

        shouldChangeFramebufferSize = true;
        Minecraft.getInstance().resizeDisplay();

        double framesPerTick = this.settings.framerate() / 20.0;

        int currentFrame = 0;
        int totalFrames = (int) Math.ceil((this.settings.endTick() - this.settings.startTick() + 1) * framesPerTick);
        int clientTickCount = 0;

        this.renderStartTime = System.currentTimeMillis();

        EditorState editorState = EditorStateCache.get(replayServer.getMetadata().replayIdentifier);

        while (currentFrame <= totalFrames) {
            double currentTickDouble = currentFrame / framesPerTick;
            int currentTick = (int) currentTickDouble;
            double partialTick = currentTickDouble - currentTick;
            int targetServerTick = this.settings.startTick() + currentTick;

            // Wait until server is on correct replay tick
            long start = System.nanoTime();
            this.setServerTickAndWait(replayServer, targetServerTick, false);
            serverTickTimeNanos += System.nanoTime() - start;

            // Tick client
            while (clientTickCount < currentTick) {
                start = System.nanoTime();
                this.runClientTick();
                clientTickTimeNanos += System.nanoTime() - start;

                clientTickCount += 1;
            }

            long pauseScreenStart = System.currentTimeMillis();
            while (Minecraft.getInstance().getOverlay() != null || Minecraft.getInstance().screen != null) {
                this.runClientTick();

                Window window = Minecraft.getInstance().getWindow();
                RenderTarget renderTarget = Minecraft.getInstance().mainRenderTarget;
                renderTarget.bindWrite(true);
                RenderSystem.clear(16640, Minecraft.ON_OSX);
                Minecraft.getInstance().gameRenderer.render(new FixedDeltaTracker(0.0f, 0.0f), true);
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

            this.tryUnfreezeClient();

            editorState.applyKeyframes(Minecraft.getInstance(), (float)(this.settings.startTick() + currentTickDouble));

            RenderTarget oldTarget = Minecraft.getInstance().getMainRenderTarget();
            SaveableFramebuffer saveable = downloader.take();
            RenderTarget renderTarget = saveable.getFramebuffer(this.settings.resolutionX(), this.settings.resolutionY());

            Minecraft.getInstance().mainRenderTarget = renderTarget;

            renderTarget.bindWrite(true);

            RenderSystem.clear(16640, Minecraft.ON_OSX);

            // Perform rendering
            PerfectFrames.waitUntilFrameReady();
            FogRenderer.setupNoFog();
            RenderSystem.enableCull();
            Minecraft.getInstance().timer.deltaTicks = 1 / (float) framesPerTick;
            Minecraft.getInstance().timer.realtimeDeltaTicks = 1 / (float) framesPerTick;
            Minecraft.getInstance().timer.deltaTickResidual = (float) partialTick;
            Minecraft.getInstance().timer.pausedDeltaTickResidual = (float) partialTick;

            start = System.nanoTime();
            Minecraft.getInstance().gameRenderer.render(new FixedDeltaTracker(1 / (float) framesPerTick, (float) partialTick), true);
            renderTimeNanos += System.nanoTime() - start;

            renderTarget.unbindWrite();

            Minecraft.getInstance().mainRenderTarget = oldTarget;

            this.shouldChangeFramebufferSize = false;
            boolean cancel = finishFrame(renderTarget, infoRenderTarget, currentFrame, totalFrames);
            this.shouldChangeFramebufferSize = true;

            submitDownloadedFrames(encoder, downloader, false);

            downloader.startDownload(saveable);

            if (cancel) {
                break;
            }

            currentFrame += 1;
        }

        submitDownloadedFrames(encoder, downloader, true);
        encoder.finish();
    }

    private void setup(ReplayServer replayServer) {
        Minecraft minecraft = Minecraft.getInstance();

        // Clear particles
        minecraft.particleEngine.clearParticles();

        // Apply keyframes at start tick
        EditorState editorState = EditorStateCache.get(replayServer.getMetadata().replayIdentifier);
        editorState.applyKeyframes(Minecraft.getInstance(), this.settings.startTick());

        // Ensure position is updated before doing anything else
        this.runClientTick();
        this.setServerTickAndWait(replayServer, replayServer.getReplayTick(), true);

        int currentTick = Math.max(0, this.settings.startTick() - 20);

        // Ensure replay server is paused at currentTick
        this.setServerTickAndWait(replayServer, currentTick, true);
        this.runClientTick();

        // Advance until tick is at start
        while (currentTick < this.settings.startTick()) {
            this.setServerTickAndWait(replayServer, currentTick, true);
            this.runClientTick();
            currentTick += 1;
        }

        // Ensured replay server is paused at startTick
        this.setServerTickAndWait(replayServer, this.settings.startTick(), true);
        this.runClientTick();

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

    private void runClientTick() {
        this.tryUnfreezeClient();

        while (Minecraft.getInstance().pollTask()) {}
        Minecraft.getInstance().tick();
        this.tryUnfreezeClient();
    }

    private void tryUnfreezeClient() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.tickRateManager().setFrozen(false);
        }
    }

    private void submitDownloadedFrames(AsyncVideoEncoder encoder, SaveableFramebufferQueue downloader, boolean drain) {
        NativeImage image;
        while (true) {
            long start = System.nanoTime();
            image = downloader.finishDownload(drain);
            downloadTimeNanos += System.nanoTime() - start;

            if (image == null) {
                break;
            }

            start = System.nanoTime();
            encoder.encode(image);
            encodeTimeNanos += System.nanoTime() - start;
        }
    }

    private boolean finishFrame(RenderTarget framebuffer, RenderTarget infoRenderTarget, int currentFrame, int totalFrames) {
        boolean cancel = false;

        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastRenderMillis > 1000/60 || currentFrame == totalFrames) {
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

            int guiScale = 4;
            int scaledWidth = (int) Math.ceil(infoRenderTarget.width / guiScale);
            int scaledHeight = (int) Math.ceil(infoRenderTarget.height / guiScale);

            Matrix4f matrix4f = new Matrix4f().setOrtho(0.0f, scaledWidth, scaledHeight, 0.0f, 1000.0f, 21000.0f);
            RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);

            Matrix4f matrix = new Matrix4f();
            matrix.translate(0.0f, 0.0f, -11000.0f);

            RenderSystem.disableDepthTest();

            List<String> lines = new ArrayList<>();

            lines.add("Exported Frames: " + currentFrame + "/" + totalFrames);

            long elapsed = currentTime - this.renderStartTime;
            lines.add("Time elapsed: " + formatTime(elapsed));

            if (currentFrame >= this.settings.framerate()) {
                long estimatedRemaining = (currentTime - this.renderStartTime) * (totalFrames - currentFrame) / currentFrame;
                lines.add("Estimated time remaining: " + formatTime(estimatedRemaining));
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

}
