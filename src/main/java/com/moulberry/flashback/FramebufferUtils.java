package com.moulberry.flashback;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.moulberry.flashback.visuals.ShaderManager;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.EXTFramebufferMultisampleBlitScaled;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;

import java.util.OptionalInt;

public class FramebufferUtils {

    public static void clear(RenderTarget renderTarget, int colour) {
        int oldReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GpuTexture colourTexture = renderTarget.getColorTexture();
        GpuTexture depthTexture = renderTarget.getDepthTexture();
        if (colourTexture != null && !colourTexture.isClosed() && depthTexture != null && !depthTexture.isClosed()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(colourTexture, colour, depthTexture, 1.0f);
        } else if (colourTexture != null && !colourTexture.isClosed()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(colourTexture, colour);
        } else if (depthTexture != null && !depthTexture.isClosed()) {
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthTexture, 1.0f);
        }

        GlStateManager._glBindFramebuffer(GL32.GL_READ_FRAMEBUFFER, oldReadFbo);
        GlStateManager._glBindFramebuffer(GL32.GL_DRAW_FRAMEBUFFER, oldDrawFbo);
    }

    public static RenderTarget resizeOrCreateFramebuffer(RenderTarget renderTarget, int width, int height) {
        if (renderTarget == null) {
            renderTarget = new TextureTarget(null, width, height, true);
        } else if (renderTarget.width != width || renderTarget.height != height) {
            renderTarget.resize(width, height);
        }

        return renderTarget;
    }

    private static final CachedOrthoProjectionMatrixBuffer projectionBuffers = new CachedOrthoProjectionMatrixBuffer("flashback blit", 1000.0f, 3000.0f, true);

    private static void blitTo(GpuTextureView from, RenderTarget to, int width, int height, float x1, float y1, float x2, float y2) {
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(new Matrix4f().translation(0.0f, 0.0f, -2000.0f));
        var oldProjectionMatrix = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType oldProjectionType = RenderSystem.getProjectionType();
        RenderSystem.setProjectionMatrix(projectionBuffers.getBuffer(width, height), ProjectionType.ORTHOGRAPHIC);

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(width*x1, height*y2, 0.0f).setUv(0.0f, 0.0f);
        builder.addVertex(width*x2, height*y2, 0.0f).setUv(1.0f, 0.0f);
        builder.addVertex(width*x2, height*y1, 0.0f).setUv(1.0f, 1.0f);
        builder.addVertex(width*x1, height*y1, 0.0f).setUv(0.0f, 1.0f);
        try (MeshData meshData = builder.buildOrThrow()) {
            RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            GpuBuffer gpuBuffer = autoStorageIndexBuffer.getBuffer(6);
            GpuBuffer vertexBuffer = DefaultVertexFormat.POSITION_TEX.uploadImmediateVertexBuffer(meshData.vertexBuffer());

            GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                RenderSystem.getModelOffset(), RenderSystem.getTextureMatrix(), RenderSystem.getShaderLineWidth());

            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "flashback blit", to.getColorTextureView(), OptionalInt.empty())) {
                renderPass.setPipeline(ShaderManager.BLIT_SCREEN_WITH_UV);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
                renderPass.setVertexBuffer(0, vertexBuffer);
                renderPass.setIndexBuffer(gpuBuffer, autoStorageIndexBuffer.type());
                renderPass.bindSampler("InSampler", from);
                renderPass.drawIndexed(0, 0, 6, 1);
            }
        }

        RenderSystem.setProjectionMatrix(oldProjectionMatrix, oldProjectionType);
        modelViewStack.popMatrix();
    }

    private static RenderTarget tempRenderTarget = null;

    public static void blitToScreenPartial(RenderTarget renderTarget, int width, int height, float x1, float y1, float x2, float y2) {
        GlStateManager._viewport(0, 0, width, height);

        int oldReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        tempRenderTarget = FramebufferUtils.resizeOrCreateFramebuffer(tempRenderTarget, width, height);
        FramebufferUtils.clear(tempRenderTarget, 0);

        blitTo(renderTarget.getColorTextureView(), tempRenderTarget, width, height, x1, y1, x2, y2);

        tempRenderTarget.blitToScreen();

        GlStateManager._glBindFramebuffer(GL32.GL_READ_FRAMEBUFFER, oldReadFbo);
        GlStateManager._glBindFramebuffer(GL32.GL_DRAW_FRAMEBUFFER, oldDrawFbo);
    }

}
