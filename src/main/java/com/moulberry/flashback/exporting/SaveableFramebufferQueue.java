package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.moulberry.flashback.visuals.ShaderManager;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public class SaveableFramebufferQueue implements AutoCloseable {

    private final int width;
    private final int height;

    private static final int CAPACITY = 3;

    private final List<SaveableFramebuffer> available = new ArrayList<>();
    private final List<SaveableFramebuffer> waiting = new ArrayList<>();

    private final GpuTexture flipBuffer;
    private final GpuTextureView flipBufferView;

    public SaveableFramebufferQueue(int width, int height) {
        this.width = width;
        this.height = height;

        this.flipBuffer = RenderSystem.getDevice().createTexture(() -> "flip buffer", 0, TextureFormat.RGBA8, width, height, 1, 1);
        this.flipBuffer.setAddressMode(AddressMode.CLAMP_TO_EDGE);
        this.flipBufferView = RenderSystem.getDevice().createTextureView(this.flipBuffer);

        for (int i = 0; i < CAPACITY; i++) {
            this.available.add(new SaveableFramebuffer());
        }
    }

    public SaveableFramebuffer take() {
        if (this.available.isEmpty()) {
            throw new IllegalStateException("No textures available!");
        }
        return this.available.removeFirst();
    }

    private void blitFlip(RenderTarget src, boolean supersampling) {
        FilterMode oldFilterMode = src.filterMode;
        if (supersampling) {
            src.setFilterMode(FilterMode.LINEAR);
        }

        RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = autoStorageIndexBuffer.getBuffer(6);
        GpuBuffer vertexBuffer = RenderSystem.getQuadVertexBuffer();

        GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
            RenderSystem.getModelOffset(), RenderSystem.getTextureMatrix(), RenderSystem.getShaderLineWidth());

        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "flashback flip pass", this.flipBufferView, OptionalInt.empty())) {
            renderPass.setPipeline(ShaderManager.BLIT_SCREEN_FLIP);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
            renderPass.setVertexBuffer(0, vertexBuffer);
            renderPass.setIndexBuffer(indexBuffer, autoStorageIndexBuffer.type());
            renderPass.bindSampler("InSampler", src.getColorTextureView());
            renderPass.drawIndexed(0, 0, 6, 1);
        }

        // todo nobuild
//        GlStateManager._colorMask(true, true, true, true);
//        GlStateManager._disableDepthTest();
//        GlStateManager._depthMask(false);
//        GlStateManager._viewport(0, 0, src.width, src.height);
//        GlStateManager._disableBlend();
//        RenderSystem.disableCull();
//
//        this.flipBuffer.bindWrite(true);
//        CompiledShaderProgram shaderInstance = Objects.requireNonNull(
//                RenderSystem.setShader(ShaderManager.blitScreenFlip), "Blit shader not loaded"
//        );
//        shaderInstance.bindSampler("InSampler", src.colorTextureId);
//        shaderInstance.apply();
//        BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
//        bufferBuilder.addVertex(0.0F, 1.0F, 0.0F);
//        bufferBuilder.addVertex(1.0F, 1.0F, 0.0F);
//        bufferBuilder.addVertex(1.0F, 0.0F, 0.0F);
//        bufferBuilder.addVertex(0.0F, 0.0F, 0.0F);
//        BufferUploader.draw(bufferBuilder.buildOrThrow());
//        shaderInstance.clear();
//
//        GlStateManager._depthMask(true);
//        GlStateManager._colorMask(true, true, true, true);
//        RenderSystem.enableCull();

        if (supersampling) {
            src.setFilterMode(oldFilterMode);
        }
    }

    public void startDownload(RenderTarget target, SaveableFramebuffer texture, boolean supersampling) {
        // Do an inline flip
        this.blitFlip(target, supersampling);

        texture.startDownload(this.flipBuffer, this.width, this.height);
        this.waiting.add(texture);
    }

    record DownloadedFrame(NativeImage image, @Nullable FloatBuffer audioBuffer) {}

    public @Nullable DownloadedFrame finishDownload(boolean drain) {
        if (this.waiting.isEmpty()) {
            return null;
        }

        if (!drain && !this.available.isEmpty()) {
            return null;
        }

        SaveableFramebuffer texture = this.waiting.removeFirst();

        NativeImage nativeImage = texture.finishDownload(this.width, this.height);
        FloatBuffer audioBuffer = texture.audioBuffer;
        texture.audioBuffer = null;

        this.available.add(texture);
        return new DownloadedFrame(nativeImage, audioBuffer);
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
    }


}
