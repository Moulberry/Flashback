package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.combo_options.Sizing;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumSet;

public class CameraPath {

    private static VertexBuffer cameraPathVertexBuffer = null;
    private static CameraPathArgs lastCameraPathArgs = null;
    private static int lastEditorStateModCount = 0;
    private static int lastCursorTick = 0;

    public static void renderCameraPath(PoseStack poseStack, Camera camera, ReplayServer replayServer) {
        RenderSystem.assertOnRenderThread();

        EditorState state = replayServer.getEditorState();
        int replayTick = TimelineWindow.getCursorTick();

        if (lastEditorStateModCount != state.modCount || lastCursorTick != replayTick) {
            CameraPathArgs cameraPathArgs = createCameraPathArgs(state, replayTick, SUPPORTED_CAMERA_KEYFRAMES);

            if (lastEditorStateModCount != state.modCount || !cameraPathArgs.equals(lastCameraPathArgs)) {
                lastCameraPathArgs = cameraPathArgs;

                BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                buildCameraPath(state, cameraPathArgs, bufferBuilder);

                if (cameraPathVertexBuffer != null) {
                    cameraPathVertexBuffer.close();
                    cameraPathVertexBuffer = null;
                }

                MeshData meshData = bufferBuilder.build();
                if (meshData != null) {
                    cameraPathVertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    cameraPathVertexBuffer.bind();
                    cameraPathVertexBuffer.upload(meshData);
                    VertexBuffer.unbind();
                }
            }

            lastEditorStateModCount = state.modCount;
            lastCursorTick = replayTick;
        }

        if (cameraPathVertexBuffer == null) {
            return;
        }

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2f);
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        float oldFogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        poseStack.pushPose();
        poseStack.translate(-camera.getPosition().x,
            -camera.getPosition().y + camera.eyeHeight, -camera.getPosition().z);

        cameraPathVertexBuffer.bind();
        cameraPathVertexBuffer.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), GameRenderer.getRendertypeLinesShader());
        VertexBuffer.unbind();

        if (replayServer.replayPaused) {
            var handler = new CapturingKeyframeHandler();
            var fovHandler = new FovCapturingKeyframeHandler();

            fovHandler.fov = Minecraft.getInstance().options.fov().get();
            state.applyKeyframes(handler, replayTick);
            state.applyKeyframes(fovHandler, replayTick);
            if (handler.position != null) {
                BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                renderCamera(bufferBuilder, handler.position, handler.angle, fovHandler.fov, getCameraColour(false, true), 1.0f);

                var oldModelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
                RenderSystem.getModelViewMatrix().set(poseStack.last().pose());
                BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
                RenderSystem.getModelViewMatrix().set(oldModelViewMatrix);
            }
        }

        poseStack.popPose();

        RenderSystem.setShaderFogStart(oldFogStart);
        RenderSystem.enableCull();
    }

    private record CameraPathArgs(int lastLastCameraTick, int lastCameraTick, int nextCameraTick, int nextNextCameraTick) {}

    private static CameraPathArgs createCameraPathArgs(EditorState state, int replayTick, EnumSet<KeyframeType> supportedKeyframes) {
        int lastCameraTick = -1;
        int nextCameraTick = -1;

        for (int trackIndex = 0; trackIndex < state.keyframeTracks.size(); trackIndex++) {
            KeyframeTrack keyframeTrack = state.keyframeTracks.get(trackIndex);
            if (keyframeTrack.enabled && supportedKeyframes.contains(keyframeTrack.keyframeType) && !keyframeTrack.keyframesByTick.isEmpty()) {
                var lastEntry = keyframeTrack.keyframesByTick.floorEntry(replayTick);
                var nextEntry = keyframeTrack.keyframesByTick.ceilingEntry(replayTick + 1);

                if (lastEntry != null && (lastCameraTick == -1 || lastEntry.getKey() > lastCameraTick)) {
                    lastCameraTick = lastEntry.getKey();
                }
                if (nextEntry != null && (nextCameraTick == -1 || nextEntry.getKey() < nextCameraTick)) {
                    nextCameraTick = nextEntry.getKey();
                }

                if (lastEntry != null && nextEntry != null) {
                    break;
                }
            }
        }

        int lastLastCameraTick = -1;
        int nextNextCameraTick = -1;

        for (int trackIndex = 0; trackIndex < state.keyframeTracks.size(); trackIndex++) {
            KeyframeTrack keyframeTrack = state.keyframeTracks.get(trackIndex);
            if (keyframeTrack.enabled && supportedKeyframes.contains(keyframeTrack.keyframeType) && !keyframeTrack.keyframesByTick.isEmpty()) {
                var lastLastEntry = lastCameraTick == -1 ? null : keyframeTrack.keyframesByTick.floorEntry(lastCameraTick - 1);
                var nextNextEntry = nextCameraTick == -1 ? null : keyframeTrack.keyframesByTick.ceilingEntry(nextCameraTick + 1);

                if (lastLastEntry != null && lastLastCameraTick == -1) {
                    lastLastCameraTick = lastLastEntry.getKey();
                }
                if (nextNextEntry != null && nextNextCameraTick == -1) {
                    nextNextCameraTick = nextNextEntry.getKey();
                }
            }
        }

        return new CameraPathArgs(lastLastCameraTick, lastCameraTick, nextCameraTick, nextNextCameraTick);
    }

    private static void buildCameraPath(EditorState state, CameraPathArgs args, BufferBuilder bufferBuilder) {
        var handler = new CapturingKeyframeHandler();
        var fovHandler = new FovCapturingKeyframeHandler();
        float defaultFov = Minecraft.getInstance().options.fov().get();

        if (args.lastCameraTick != -1) {
            fovHandler.fov = defaultFov;
            state.applyKeyframes(handler, args.lastCameraTick);
            state.applyKeyframes(fovHandler, args.lastCameraTick);

            renderCamera(bufferBuilder, handler.position, handler.angle, fovHandler.fov,
                getCameraColour(false, false), 1.0f);

            if (args.lastLastCameraTick != -1) {
                fovHandler.fov = defaultFov;
                state.applyKeyframes(handler, args.lastLastCameraTick);
                state.applyKeyframes(fovHandler, args.lastLastCameraTick);

                renderCamera(bufferBuilder, handler.position, handler.angle, fovHandler.fov,
                    getCameraColour(false, false), 0.6f);
                renderPath(bufferBuilder, args.lastLastCameraTick, args.lastCameraTick, state, handler, 0.6f);
            }
        }

        if (args.nextCameraTick != -1) {
            fovHandler.fov = defaultFov;
            state.applyKeyframes(handler, args.nextCameraTick);
            state.applyKeyframes(fovHandler, args.nextCameraTick);

            renderCamera(bufferBuilder, handler.position, handler.angle, fovHandler.fov,
                getCameraColour(false, false), 1.0f);

            if (args.nextNextCameraTick != -1) {
                fovHandler.fov = defaultFov;
                state.applyKeyframes(handler, args.nextNextCameraTick);
                state.applyKeyframes(fovHandler, args.nextNextCameraTick);

                renderCamera(bufferBuilder, handler.position, handler.angle, fovHandler.fov,
                    getCameraColour(false, false), 0.6f);
                renderPath(bufferBuilder, args.nextCameraTick, args.nextNextCameraTick, state, handler, 0.6f);
            }
        }

        if (args.lastCameraTick != -1 && args.nextCameraTick != -1) {
            renderPath(bufferBuilder, args.lastCameraTick, args.nextCameraTick, state, handler, 1.0f);
        }
    }

    private static int getCameraColour(boolean selected, boolean current) {
        if (current) {
            return 0x20FF20;
        } else if (selected) {
            return 0xFF2020;
        } else {
            return 0xFFFF20;
        }
    }

    private static void renderPath(BufferBuilder bufferBuilder, int fromTick, int toTick,
            EditorState editorState, CapturingKeyframeHandler handler, float opacity) {
        Vector3f lastPosition = null;

        int step = (toTick - fromTick) / 2000 + 1;

        for (int tick = fromTick; tick <= toTick; tick += step) {
            editorState.applyKeyframes(handler, tick);
            Vector3f rawPosition = new Vector3f(handler.position);

            double x = rawPosition.x;
            double y = rawPosition.y;
            double z = rawPosition.z;

            Vector3f position = new Vector3f((float) x, (float) y, (float) z);

            if (lastPosition != null) {
                float dx = position.x - lastPosition.x;
                float dy = position.y - lastPosition.y;
                float dz = position.z - lastPosition.z;
                float distanceInv = 1f / (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
                dx *= distanceInv;
                dy *= distanceInv;
                dz *= distanceInv;

                bufferBuilder.addVertex(lastPosition.x, lastPosition.y, lastPosition.z).setColor(1.0f, 1.0f, 0.1f, 0.0f).setNormal(dx, dy, dz);
                bufferBuilder.addVertex( position.x, position.y, position.z).setColor(1.0f, 1.0f, 0.1f, opacity).setNormal(dx, dy, dz);
            }

            lastPosition = position;
        }
    }

    private static final PoseStack cameraPoseStack = new PoseStack();
    private static void renderCamera(BufferBuilder bufferBuilder, Vector3f position, Quaternionf angle, float fov, int rgb, float opacity) {
        cameraPoseStack.pushPose();
        cameraPoseStack.translate(position.x, position.y, position.z);
        cameraPoseStack.mulPose(angle);

        PoseStack.Pose pose = cameraPoseStack.last();

        EditorState editorState = EditorStateManager.getCurrent();

        float aspectRatio;
        if (editorState != null && editorState.replayVisuals.sizing == Sizing.CHANGE_ASPECT_RATIO) {
            aspectRatio = editorState.replayVisuals.changeAspectRatio.aspectRatio();
        } else {
            Window window = Minecraft.getInstance().getWindow();
            aspectRatio = (float) window.getWidth() / window.getHeight();
        }

        float focalLength = Utils.fovToFocalLength(fov);

        float targetArea = (float) Math.sqrt(aspectRatio) * 0.3f;
        float d1 = (float) Math.sqrt(targetArea / aspectRatio * (focalLength*focalLength));
        float d2 = 0.5f;
        float d = (d1 + d2)/2.0f;
        float h = d / focalLength;
        float w = h * aspectRatio;

        float red = ((rgb >> 16) & 0xFF)/255f;
        float green = ((rgb >> 8) & 0xFF)/255f;
        float blue = (rgb & 0xFF)/255f;

        Shapes.line(bufferBuilder, pose, Vec3.ZERO, new Vec3(w, h, d), red, green, blue, opacity);
        Shapes.line(bufferBuilder, pose, new Vec3(w, h, d), new Vec3(-w, h, d), red, green, blue, opacity);
        Shapes.line(bufferBuilder, pose, Vec3.ZERO, new Vec3(-w, h, d), red, green, blue, opacity);
        Shapes.line(bufferBuilder, pose, new Vec3(-w, h, d), new Vec3(-w, -h, d), red, green, blue, opacity);
        Shapes.line(bufferBuilder, pose, Vec3.ZERO, new Vec3(-w, -h, d), red, green, blue, opacity);
        Shapes.line(bufferBuilder, pose, new Vec3(-w, -h, d), new Vec3(w, -h, d), red, green, blue, opacity);
        Shapes.line(bufferBuilder, pose, Vec3.ZERO, new Vec3(w, -h, d), red, green, blue, opacity);
        Shapes.line(bufferBuilder, pose, new Vec3(w, -h, d), new Vec3(w, h, d), red, green, blue, opacity);

        cameraPoseStack.popPose();
    }

    private static final EnumSet<KeyframeType> SUPPORTED_CAMERA_KEYFRAMES = EnumSet.of(KeyframeType.CAMERA, KeyframeType.CAMERA_ORBIT);

    private static class CapturingKeyframeHandler implements KeyframeHandler {
        private Vector3f position;
        private Quaternionf angle;

        @Override
        public EnumSet<KeyframeType> supportedKeyframes() {
            return SUPPORTED_CAMERA_KEYFRAMES;
        }

        @Override
        public boolean alwaysApplyLastKeyframe() {
            return true;
        }

        @Override
        public void applyCameraPosition(Vector3f position, float yaw, float pitch, float roll) {
            this.position = position;
            this.angle = new Quaternionf().rotationYXZ((float) -Math.toRadians(yaw), (float) Math.toRadians(pitch), (float) -Math.toRadians(roll));
        }
    }

    private static class FovCapturingKeyframeHandler implements KeyframeHandler {
        private float fov;

        @Override
        public EnumSet<KeyframeType> supportedKeyframes() {
            return EnumSet.of(KeyframeType.FOV);
        }

        @Override
        public boolean alwaysApplyLastKeyframe() {
            return true;
        }

        @Override
        public void applyFov(float fov) {
            this.fov = fov;
        }
    }

}
