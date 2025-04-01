package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class FlashbackDrawBuffer implements AutoCloseable {

    BufferUsage bufferUsage;
    GpuBuffer vertexBuffer;
    int indexCount;
    VertexFormat vertexFormat;
    VertexFormat.Mode vertexFormatMode;

    public FlashbackDrawBuffer(BufferUsage bufferUsage) {
        this.bufferUsage = bufferUsage;
    }

    @Override
    public void close() {
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
        }
    }

    public void upload(MeshData meshData) {
        try (meshData) {
            MeshData.DrawState drawState = meshData.drawState();
            this.uploadVertexBuffer(meshData.vertexBuffer());
            this.vertexFormat = drawState.format();
            this.vertexFormatMode = drawState.mode();
            this.indexCount = drawState.indexCount();
        }
    }

    private void uploadVertexBuffer(ByteBuffer byteBuffer) {
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
        }
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(null, BufferType.VERTICES, this.bufferUsage, byteBuffer);
    }

    public void draw() {
        RenderTarget renderTarget = Minecraft.getInstance().getMainRenderTarget();

        RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(this.vertexFormatMode);
        GpuBuffer indexBuffer = autoStorageIndexBuffer.getBuffer(this.indexCount);
        VertexFormat.IndexType indexType = autoStorageIndexBuffer.type();

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass renderPass = commandEncoder.createRenderPass(renderTarget.getColorTexture(), OptionalInt.empty(), renderTarget.useDepth ? renderTarget.getDepthTexture() : null, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.LINES);
            renderPass.setVertexBuffer(0, this.vertexBuffer);
            if (RenderSystem.SCISSOR_STATE.isEnabled()) {
                renderPass.enableScissor(RenderSystem.SCISSOR_STATE);
            }

            for (int i = 0; i < 12; i++) {
                GpuTexture gpuTexture = RenderSystem.getShaderTexture(i);
                if (gpuTexture != null) {
                    renderPass.bindSampler("Sampler" + i, gpuTexture);
                }
            }

            renderPass.setIndexBuffer(indexBuffer, indexType);
            renderPass.drawIndexed(0, this.indexCount);
        }
    }

}
