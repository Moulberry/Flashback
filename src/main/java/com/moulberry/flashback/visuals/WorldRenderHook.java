package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.record.ReplayMarker;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class WorldRenderHook {

    private static final RenderType MARKER_CIRCLE_RENDER_TYPE = RenderType.create("flashback:marker_circle", RenderSetup
        .builder(RenderPipelines.GUI_TEXTURED)
        .withTexture("Sampler0", Identifier.parse("flashback:world_marker_circle.png"))
        .createRenderSetup()
    );

    private record TextRenderableAt(TextRenderable textRenderable, Vector3f location) {}

    public static void renderHook(PoseStack poseStack, CameraRenderState camera) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null || Flashback.isExporting() || !ReplayUI.isActive()) {
            return;
        }

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.cameraPath) {
            CameraPath.renderCameraPath(poseStack, camera, replayServer);
        }

        FlashbackMeta meta = replayServer.getMetadata();
        if (!meta.replayMarkers.isEmpty()) {
            try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(256)) {
                BufferBuilder circleBufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

                String dimension = Minecraft.getInstance().level.dimension().toString();

                LinkedHashMap<RenderType, List<TextRenderableAt>> textRenderablesForType = new LinkedHashMap<>();

                for (ReplayMarker marker : meta.replayMarkers.values()) {
                    if (marker.position() == null) {
                        continue;
                    }

                    ReplayMarker.MarkerPosition position = marker.position();
                    if (!position.dimension().equals(dimension)) {
                        continue;
                    }

                    poseStack.pushPose();
                    poseStack.translate(
                        position.position().x - camera.pos.x,
                        position.position().y - camera.pos.y,
                        position.position().z - camera.pos.z
                    );
                    poseStack.mulPose(camera.orientation);

                    final float width = 0.2f;
                    circleBufferBuilder.addVertex(poseStack.last(), -width, -width, 0.0f).setUv(0f, 0f).setColor(marker.colour() | 0xFF000000);
                    circleBufferBuilder.addVertex(poseStack.last(), width, -width, 0.0f).setUv(1f, 0f).setColor(marker.colour() | 0xFF000000);
                    circleBufferBuilder.addVertex(poseStack.last(), width, width, 0.0f).setUv(1f, 1f).setColor(marker.colour() | 0xFF000000);
                    circleBufferBuilder.addVertex(poseStack.last(), -width, width, 0.0f).setUv(0f, 1f).setColor(marker.colour() | 0xFF000000);

                    if (marker.description() != null) {
                        Font font = Minecraft.getInstance().font;

                        int descriptionWidth = font.width(marker.description());

                        var preparedText = font.prepareText(marker.description(), -descriptionWidth/2f, -20f,
                            -1, true, 0);

                        preparedText.visit(new Font.GlyphVisitor() {
                            @Override
                            public void acceptRenderable(TextRenderable renderable) {
                            var renderType = renderable.renderType(Font.DisplayMode.POLYGON_OFFSET);
                            var renderables = textRenderablesForType.computeIfAbsent(renderType, k -> new ArrayList<>());
                            renderables.add(new TextRenderableAt(renderable, position.position()));
                            }
                        });
                    }

                    poseStack.popPose();
                }

                MeshData circleMeshData = circleBufferBuilder.build();
                if (circleMeshData != null) {
                    try (FlashbackDrawBuffer drawBuffer = new FlashbackDrawBuffer(GpuBuffer.USAGE_MAP_WRITE)) {
                        drawBuffer.upload(circleMeshData);
                        drawBuffer.drawRenderType(MARKER_CIRCLE_RENDER_TYPE.prepare());
                    }
                }

                if (!textRenderablesForType.isEmpty()) {
                    for (var entry : textRenderablesForType.entrySet()) {
                        var renderType = entry.getKey();
                        var renderables = entry.getValue();

                        BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, renderType.primitiveTopology(), renderType.format());

                        for (TextRenderableAt renderableAt : renderables) {
                            poseStack.pushPose();
                            poseStack.translate(
                                renderableAt.location().x - camera.pos.x,
                                renderableAt.location().y - camera.pos.y,
                                renderableAt.location().z - camera.pos.z
                            );
                            poseStack.mulPose(camera.orientation);

                            Matrix4f matrix4f = poseStack.last().pose();
                            matrix4f.rotate((float)Math.PI, 0.0f, 1.0f, 0.0f);
                            matrix4f.scale(-0.025f, -0.025f, -0.025f);

                            renderableAt.textRenderable().render(matrix4f, bufferBuilder, LightCoordsUtil.FULL_BRIGHT, false);

                            poseStack.popPose();
                        }

                        MeshData meshData = bufferBuilder.build();

                        if (meshData != null) {
                            try (FlashbackDrawBuffer drawBuffer = new FlashbackDrawBuffer(GpuBuffer.USAGE_MAP_WRITE)) {
                                drawBuffer.upload(meshData);
                                drawBuffer.drawRenderType(renderType.prepare());
                            }
                        }
                    }
                }
            }
        }
    }
}
