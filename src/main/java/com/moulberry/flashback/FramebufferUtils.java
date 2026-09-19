package com.moulberry.flashback;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
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
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(colourTexture, clearColour, depthTexture, 0.0f);
        } else if (colourTexture != null && !colourTexture.isClosed()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(colourTexture, clearColour);
        } else if (depthTexture != null && !depthTexture.isClosed()) {
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthTexture, 0.0f);
        }
    }

    public static RenderTarget resizeOrCreateFramebuffer(RenderTarget renderTarget, int width, int height, boolean useDepth) {
        if (renderTarget == null) {
            renderTarget = new TextureTarget(null, width, height, GpuFormat.RGBA8_UNORM, useDepth ? GpuFormat.D32_FLOAT : null);
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
                    renderPass.setPipeline(RenderSystem.getCompiledPipeline(ShaderManager.BLIT_SCREEN_WITH_UV));
                    RenderSystem.bindDefaultUniforms(renderPass);
                    renderPass.setVertexBuffer(0, drawBuffer.getVertexBuffer().slice());
                    renderPass.setIndexBuffer(indexBuffer, autoStorageIndexBuffer.type());
                    renderPass.setUniform("InSampler", from, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                    renderPass.drawIndexed(6, 1, 0, 0, 0);
                }
            }
        }
    }

}
