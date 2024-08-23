package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

public class WorldRenderHook {

    public static void renderHook(PoseStack poseStack, float partialTick, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
            LightTexture lightTexture, Matrix4f projection) {
        ReplayServer replayServer = Flashback.getReplayServer();
        EditorState editorState = EditorStateManager.getCurrent();
        if (replayServer != null && editorState != null && !Flashback.isExporting() && editorState.replayVisuals.cameraPath) {
            CameraPath.renderCameraPath(poseStack, camera, replayServer);
        }
    }
}
