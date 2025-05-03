package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.record.ReplayMarker;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class WorldRenderHook {

    public static void renderHook(PoseStack poseStack, float partialTick, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, Matrix4f projection) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null || Flashback.isExporting()) {
            return;
        }

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.cameraPath) {
            CameraPath.renderCameraPath(poseStack, camera, replayServer);
        }

        FlashbackMeta meta = replayServer.getMetadata();
        if (!meta.replayMarkers.isEmpty()) {
            var multiBufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            multiBufferSource.endBatch();

            var bufferBuilder = multiBufferSource.getBuffer(RenderType.guiTextured(ResourceLocation.parse("flashback:world_marker_circle.png")));

            String dimension = Minecraft.getInstance().level.dimension().toString();

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
                    position.position().x - camera.getPosition().x,
                    position.position().y - camera.getPosition().y,
                    position.position().z - camera.getPosition().z
                );
                poseStack.mulPose(camera.rotation());

                final float width = 0.2f;
                bufferBuilder.addVertex(poseStack.last(), -width, -width, 0.0f).setUv(0f, 0f).setColor(marker.colour() | 0xFF000000);
                bufferBuilder.addVertex(poseStack.last(), width, -width, 0.0f).setUv(1f, 0f).setColor(marker.colour() | 0xFF000000);
                bufferBuilder.addVertex(poseStack.last(), width, width, 0.0f).setUv(1f, 1f).setColor(marker.colour() | 0xFF000000);
                bufferBuilder.addVertex(poseStack.last(), -width, width, 0.0f).setUv(0f, 1f).setColor(marker.colour() | 0xFF000000);

                if (marker.description() != null) {
                    Font font = Minecraft.getInstance().font;

                    Matrix4f matrix4f = poseStack.last().pose();
                    matrix4f.rotate((float)Math.PI, 0.0f, 1.0f, 0.0f);
                    matrix4f.scale(-0.025f, -0.025f, -0.025f);
                    int descriptionWidth = font.width(marker.description());
                    font.drawInBatch(marker.description(), -descriptionWidth/2f, -20f, 0xFFFFFFFF,
                        true, matrix4f, multiBufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
                }

                poseStack.popPose();
            }

//            MeshData meshData = bufferBuilder.build();
//            if (meshData != null) {
//                RenderType.guiTextured(ResourceLocation.parse("flashback:world_marker_circle.png")).draw(meshData);
//            }

            multiBufferSource.endBatch();
        }
    }
}
