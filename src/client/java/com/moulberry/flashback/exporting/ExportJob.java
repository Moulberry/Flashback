package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.FlashbackClient;
import com.moulberry.flashback.keyframe.Keyframes;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Matrix4f;

import java.util.concurrent.locks.LockSupport;

public class ExportJob {

    private final int startTick;
    private final int endTick;
    private final int framesPerTick = 60 / 20;

    private boolean running = false;
    private long lastRenderMillis;
    private long renderStartTime;

    public ExportJob(int startTick, int endTick) {
        this.startTick = startTick;
        this.endTick = endTick;
    }

    public void run() {
        ReplayServer replayServer = FlashbackClient.getReplayServer();
        if (this.running || replayServer == null) {
            return;
        }
        this.running = true;

        AsyncVideoEncoder encoder = new AsyncVideoEncoder(this.framesPerTick * 20);

        int currentFrame = 0;
        int totalFrames = (this.endTick - this.startTick + 1) * this.framesPerTick;
        int clientTickCount = 0;

        boolean shouldDoTextureDownload = false;

        RenderTarget mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
        this.renderStartTime = System.currentTimeMillis();

        while (currentFrame <= totalFrames) {
            int targetServerTick = this.startTick + currentFrame / this.framesPerTick;

            // Wait until server is on correct replay tick
            while (replayServer.getReplayTick() != targetServerTick) {
                replayServer.finishedServerTick.set(false);
                replayServer.jumpToTick = targetServerTick;
                replayServer.replayPaused = true;

                // Do some work by downloading textures while we're waiting for the server
                if (shouldDoTextureDownload) {
                    shouldDoTextureDownload = false;
                    finishFrame(encoder, currentFrame-1, totalFrames);
                }

                while (!replayServer.finishedServerTick.compareAndExchange(true, false)) {
                    LockSupport.parkNanos("waiting for server thread", 100000L);
                }
            }

            if (shouldDoTextureDownload) {
                shouldDoTextureDownload = false;
                finishFrame(encoder, currentFrame-1, totalFrames);
            }

            long targetClientTick = currentFrame / this.framesPerTick;

            // Ensure client isn't frozen so partialTick is correctly used
            Minecraft.getInstance().level.tickRateManager().setFrozen(false);

            // Tick client
            while (clientTickCount < targetClientTick) {
                while (Minecraft.getInstance().pollTask()) {}
                Minecraft.getInstance().tick();
                clientTickCount += 1;
            }

            float partialTick = (currentFrame / (float) this.framesPerTick) % 1.0f;
            Keyframes.applyKeyframes(Minecraft.getInstance(), targetServerTick + partialTick);

            mainRenderTarget.bindWrite(true);
            FogRenderer.setupNoFog();
            RenderSystem.enableCull();
            Minecraft.getInstance().timer.partialTick = partialTick;
            Minecraft.getInstance().gameRenderer.render(partialTick, Util.getNanos(), true);
            mainRenderTarget.unbindWrite();

            shouldDoTextureDownload = true;

            currentFrame += 1;
        }

        if (shouldDoTextureDownload) {
            shouldDoTextureDownload = false;
            finishFrame(encoder, currentFrame-1, totalFrames);
        }

        encoder.finish();
    }


    private void finishFrame(AsyncVideoEncoder encoder, int currentFrame, int totalFrames) {
        RenderTarget mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
        NativeImage nativeImage = Screenshot.takeScreenshot(mainRenderTarget);
        encoder.encode(nativeImage);
        Window window = Minecraft.getInstance().getWindow();

        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastRenderMillis > 10) {
            this.lastRenderMillis = currentTime;

            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            bufferSource.endBatch();
            mainRenderTarget.bindWrite(true);

            RenderSystem.disableDepthTest();
            Matrix4f matrix = new Matrix4f();
            matrix.translate(0.0f, 0.0f, -11000f);
            float x = window.getGuiScaledWidth()/2f;
            float y = window.getGuiScaledHeight()/2f;
            Font font = Minecraft.getInstance().font;

            String line1 = "Exported Frames: " + currentFrame + "/" + totalFrames;
            font.drawInBatch(line1, x - font.width(line1)/2f, y - font.lineHeight,
                -1, true, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);

            long elapsed = currentTime - this.renderStartTime;
            String line2 = "Time elapsed: " + formatTime(elapsed);
            font.drawInBatch(line2, x - font.width(line2)/2f, y,
                -1, true, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);

            if (currentFrame > framesPerTick*20) {
                long estimatedRemaining = (currentTime - this.renderStartTime) * (totalFrames - currentFrame) / currentFrame;
                String line3 = "Estimated time remaining: " + formatTime(estimatedRemaining);
                font.drawInBatch(line3, x - font.width(line3)/2f, y + font.lineHeight,
                    -1, true, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }

            bufferSource.endBatch();
            RenderSystem.enableDepthTest();

            mainRenderTarget.unbindWrite();
            mainRenderTarget.blitToScreen(window.getWidth(), window.getHeight());
            window.updateDisplay();
        }
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
