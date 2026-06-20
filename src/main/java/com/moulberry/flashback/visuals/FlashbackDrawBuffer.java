package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
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
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

public class FlashbackDrawBuffer implements AutoCloseable {

    int usageFlags;
    GpuBuffer vertexBuffer;
    int indexCount;
    VertexFormat vertexFormat;
    PrimitiveTopology vertexFormatMode;

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
            this.vertexFormatMode = drawState.primitiveTopology();
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
        RenderTarget renderTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();

        RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(this.vertexFormatMode);
        GpuBuffer indexBuffer = autoStorageIndexBuffer.getBuffer(this.indexCount);
        IndexType indexType = autoStorageIndexBuffer.type();

        GpuBufferSlice gpuBufferSlice = new DynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
            new Vector3f(), new Matrix4f());

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass renderPass = renderTarget.useDepth
                ? commandEncoder.createRenderPass(() -> "flashback draw", renderTarget.getColorTextureView(), Optional.empty(), renderTarget.getDepthTextureView(), OptionalDouble.empty())
                : commandEncoder.createRenderPass(() -> "flashback draw", renderTarget.getColorTextureView(), Optional.empty())) {
            renderPass.setPipeline(RenderPipelines.LINES);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
            renderPass.setVertexBuffer(0, this.vertexBuffer.slice());

            renderPass.setIndexBuffer(indexBuffer, indexType);
            renderPass.drawIndexed(0, 0, this.indexCount, 0, 1);
        }
    }

}
