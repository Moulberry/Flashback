package com.moulberry.flashback;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.moulberry.flashback.visuals.FlashbackDrawBuffer;
import com.moulberry.flashback.visuals.ShaderManager;
import org.joml.Vector4f;

import java.util.Optional;

public class FramebufferUtils {

    public static final Vector4f TRANSPARENT_CLEAR_COLOUR = new Vector4f(0.0f);
    public static final Vector4f BLACK_CLEAR_COLOUR = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

    public static void clear(RenderTarget renderTarget, Vector4f clearColour) {
        GpuTexture colourTexture = renderTarget.getColorTexture();
        GpuTexture depthTexture = renderTarget.getDepthTexture();
        if (colourTexture != null && !colourTexture.isClosed() && depthTexture != null && !depthTexture.isClosed()) {
            float minDepth = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 0.0f : -1.0f;
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(colourTexture, clearColour, depthTexture, minDepth);
        } else if (colourTexture != null && !colourTexture.isClosed()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(colourTexture, clearColour);
        } else if (depthTexture != null && !depthTexture.isClosed()) {
            float minDepth = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 0.0f : -1.0f;
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthTexture, minDepth);
        }
    }

    public static RenderTarget resizeOrCreateFramebuffer(RenderTarget renderTarget, int width, int height, boolean useDepth) {
        if (renderTarget == null) {
            renderTarget = new TextureTarget(null, width, height, useDepth, GpuFormat.RGBA8_UNORM);
        } else if (renderTarget.width != width || renderTarget.height != height) {
            renderTarget.resize(width, height);
        }

        return renderTarget;
    }

    public static void blitTo(GpuTextureView from, RenderTarget to, float x1, float y1, float x2, float y2) {
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(256)) {
            BufferBuilder builder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(x1*2-1, -(y2*2-1), 0.0f).setUv(0.0f, 0.0f);
            builder.addVertex(x2*2-1, -(y2*2-1), 0.0f).setUv(1.0f, 0.0f);
            builder.addVertex(x2*2-1, -(y1*2-1), 0.0f).setUv(1.0f, 1.0f);
            builder.addVertex(x1*2-1, -(y1*2-1), 0.0f).setUv(0.0f, 1.0f);

            try (FlashbackDrawBuffer drawBuffer = new FlashbackDrawBuffer(GpuBuffer.USAGE_MAP_WRITE)) {
                drawBuffer.upload(builder.buildOrThrow());

                RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                GpuBuffer indexBuffer = autoStorageIndexBuffer.getBuffer(6);

                try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "flashback blit", to.getColorTextureView(), Optional.empty())) {
                    renderPass.setPipeline(ShaderManager.BLIT_SCREEN_WITH_UV);
                    RenderSystem.bindDefaultUniforms(renderPass);
                    renderPass.setVertexBuffer(0, drawBuffer.getVertexBuffer().slice());
                    renderPass.setIndexBuffer(indexBuffer, autoStorageIndexBuffer.type());
                    renderPass.bindTexture("InSampler", from, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                    renderPass.drawIndexed(6, 1, 0, 0, 0);
                }
            }
        }
    }

}
