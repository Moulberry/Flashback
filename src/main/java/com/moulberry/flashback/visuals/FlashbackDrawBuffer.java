package com.moulberry.flashback.visuals;

import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

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

    public GpuBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public void draw(PreparedRenderType renderType) {
        RenderTarget renderTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass renderPass = commandEncoder.createRenderPass(() -> "flashback draw", renderTarget.getColorTextureView(), Optional.empty(),
                renderTarget.hasDepth() ? renderTarget.getDepthTextureView() : null, OptionalDouble.empty())) {
            renderType.drawFromBuffer(this.executeInfo(), renderPass);
        }
    }

    public StagedVertexBuffer.ExecuteInfo executeInfo() {
        RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(this.vertexFormatMode);
        autoStorageIndexBuffer.requestIndexCount(this.indexCount);
        return new StagedVertexBuffer.ExecuteInfo(this.vertexBuffer, null, autoStorageIndexBuffer.type(), 0, 0, this.indexCount, this.vertexFormatMode);
    }

    public void draw() {
        this.draw(RenderTypes.LINES.prepare());
    }

}
