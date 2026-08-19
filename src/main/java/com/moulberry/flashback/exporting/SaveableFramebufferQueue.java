package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.visuals.ShaderManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;

public class SaveableFramebufferQueue implements AutoCloseable {

    private static final Vector4f CLEAR_COLOR = new Vector4f(0.0f);

    private final int width;
    private final int height;

    private final ArrayDeque<SaveableFramebuffer> available = new ArrayDeque<>();
    private final ArrayDeque<SaveableFramebuffer> waiting = new ArrayDeque<>();

    private final GpuTexture flipBuffer;
    private final GpuTextureView flipBufferView;
    private final GpuTexture flipDepthBuffer;
    private final GpuTextureView flipDepthBufferView;

    private final TransformDepthUniform transformDepthUniform = new TransformDepthUniform();

    public SaveableFramebufferQueue(int width, int height) {
        this.width = width;
        this.height = height;

        this.flipBuffer = RenderSystem.getDevice().createTexture(() -> "flip buffer", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT,
            GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        this.flipBufferView = RenderSystem.getDevice().createTextureView(this.flipBuffer);

        this.flipDepthBuffer = RenderSystem.getDevice().createTexture(() -> "flip depth buffer", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT,
            GpuFormat.R32_FLOAT, width, height, 1, 1);
        this.flipDepthBufferView = RenderSystem.getDevice().createTextureView(this.flipDepthBuffer);
    }

    public SaveableFramebuffer take() {
        if (this.available.isEmpty()) {
            return new SaveableFramebuffer(this.width, this.height);
        } else {
            return this.available.removeFirst();
        }
    }

    private void blitFlip(RenderTarget src, boolean supersampling) {
        FilterMode filterMode = supersampling ? FilterMode.LINEAR : FilterMode.NEAREST;

        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "flashback flip pass", this.flipBufferView, Optional.of(CLEAR_COLOR))) {
            renderPass.setPipeline(ShaderManager.BLIT_SCREEN_FLIP);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("InSampler", src.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filterMode));
            renderPass.draw(3, 1, 0, 0);
        }
    }

    private void blitTransformDepth(RenderTarget src) {
        var uniforms = this.transformDepthUniform.getOrUpdate(ReplayUI.lastProjectionMatrix);

        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "flashback depth flip pass", this.flipDepthBufferView, Optional.of(CLEAR_COLOR))) {
            renderPass.setPipeline(ShaderManager.BLIT_TRANSFORM_DEPTH);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("TransformDepth", uniforms);
            renderPass.bindTexture("InSampler", src.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.draw(3, 1, 0, 0);
        }
    }

    public void startDownload(RenderTarget target, SaveableFramebuffer texture, boolean supersampling) {
        // Do an inline flip
        this.blitFlip(target, supersampling);

        texture.startDownload(this.flipBuffer);
        this.waiting.add(texture);
    }

    public void startDepthDownload(RenderTarget target, SaveableFramebuffer texture) {
        this.blitTransformDepth(target);

        texture.startDownload(this.flipDepthBuffer);
        this.waiting.add(texture);
    }

    public @Nullable ImageFrame finishDownload() {
        SaveableFramebuffer first = this.waiting.peekFirst();
        if (first == null) {
            return null;
        }

        ImageFrame downloaded = first.finishDownload();

        if (downloaded == null) {
            return null;
        }

        downloaded.audioBuffer = first.audioBuffer;

        SaveableFramebuffer popped = this.waiting.removeFirst();
        popped.audioBuffer = null;
        this.available.add(popped);

        return downloaded;
    }


    public @Nullable ImageFrame[] finishDownloadMultiple(int n) {
        if (this.waiting.size() < n) {
            return null;
        }

        var iterator = this.waiting.iterator();
        for (int i = 0; i < n; i++) {
            var saveableFramebuffer = iterator.next();
            if (!saveableFramebuffer.canFinishDownload()) {
                return null;
            }
        }

        ImageFrame[] downloads = new ImageFrame[n];
        for (int i = 0; i < n; i++) {
            SaveableFramebuffer next = Objects.requireNonNull(this.waiting.removeFirst());
            ImageFrame downloaded = Objects.requireNonNull(next.finishDownload());

            downloaded.audioBuffer = next.audioBuffer;

            downloads[i] = downloaded;
            next.audioBuffer = null;
            this.available.add(next);
        }

        return downloads;
    }

    public boolean isEmpty() {
        return this.waiting.isEmpty();
    }

    public int pendingCount() {
        return this.waiting.size();
    }

    @Override
    public void close() {
        for (SaveableFramebuffer texture : this.waiting) {
            texture.close();
        }
        for (SaveableFramebuffer texture : this.available) {
            texture.close();
        }
        this.waiting.clear();
        this.available.clear();
        this.flipBuffer.close();
        this.transformDepthUniform.close();
    }


}
