package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class FlashbackDrawBuffer implements AutoCloseable {

    int usageFlags;
    GpuBuffer vertexBuffer;
    int indexCount;
    VertexFormat vertexFormat;
    VertexFormat.Mode vertexFormatMode;

    public FlashbackDrawBuffer(int usageFlags) {
        this.usageFlags = usageFlags;
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
        this.vertexBuffer = RenderSystem.getDevice().createBuffer(null, this.usageFlags | GpuBuffer.USAGE_VERTEX, byteBuffer);
    }

    public void draw() {
        RenderTarget renderTarget = Minecraft.getInstance().getMainRenderTarget();

        RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(this.vertexFormatMode);
        GpuBuffer indexBuffer = autoStorageIndexBuffer.getBuffer(this.indexCount);
        VertexFormat.IndexType indexType = autoStorageIndexBuffer.type();

        GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
            new Vector3f(), RenderSystem.getTextureMatrix());

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass renderPass = commandEncoder.createRenderPass(() -> "flashback draw", renderTarget.getColorTextureView(), OptionalInt.empty(), renderTarget.useDepth ? renderTarget.getDepthTextureView() : null, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.LINES);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
            renderPass.setVertexBuffer(0, this.vertexBuffer);

            for (int i = 0; i < 12; i++) {
                RenderSystem.TextureAndSampler textureAndSampler = RenderSystem.getShaderTexture(i);
                if (textureAndSampler != null) {
                    renderPass.bindTexture("Sampler" + i, textureAndSampler.view(), textureAndSampler.sampler());
                }
            }

            renderPass.setIndexBuffer(indexBuffer, indexType);
            renderPass.drawIndexed(0, 0, this.indexCount, 1);
        }
    }

}
