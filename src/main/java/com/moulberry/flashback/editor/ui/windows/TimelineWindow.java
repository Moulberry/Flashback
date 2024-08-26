package com.moulberry.flashback.editor.ui.windows;

import com.google.gson.reflect.TypeToken;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.editor.SavedTrack;
import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.state.EditorStateHistoryAction;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraShakeKeyframe;
import com.moulberry.flashback.keyframe.impl.TimelapseKeyframe;
import com.moulberry.flashback.state.EditorStateHistoryEntry;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.impl.TickrateKeyframe;
import com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;

public class TimelineWindow {

    private static double zoomMinBeforeDrag = 0.0f;
    private static double zoomMaxBeforeDrag = 1.0f;
    private static boolean grabbedZoomBar = false;
    private static boolean grabbedZoomBarResizeLeft = false;
    private static boolean grabbedZoomBarResizeRight = false;
    private static boolean grabbedExportBarResizeLeft = false;
    private static boolean grabbedExportBarResizeRight = false;
    private static boolean grabbedPlayback = false;
    private static boolean grabbedKeyframe = false;
    private static float grabbedKeyframeMouseX = 0;
    private static boolean enableKeyframeMovement = false;
    private static int grabbedKeyframeTick = 0;
    private static int grabbedKeyframeTrack = 0;

    @Nullable
    private static Vector2f dragSelectOrigin = null;

    private static final float[] fovKeyframeInput = new float[]{70.0f};
    private static final float[] speedKeyframeInput = new float[]{1.0f};
    private static final ImInt timeOfDayKeyframeInput = new ImInt(6000);
    private static boolean cameraShakeSplitXYKeyframeInput = false;
    private static final float[] cameraShakeFrequencyXKeyframeInput = new float[]{1.0f};
    private static final float[] cameraShakeAmplitudeXKeyframeInput = new float[]{0.0f};
    private static final float[] cameraShakeFrequencyYKeyframeInput = new float[]{1.0f};
    private static final float[] cameraShakeAmplitudeYKeyframeInput = new float[]{0.0f};
    private static final ImString timelapseKeyframeInput = ImGuiHelper.createResizableImString("0s");
    static {
        timelapseKeyframeInput.inputData.allowedChars = "0123456789tsmh";
    }

    private static final float[] cameraOrbitCenter = new float[]{0.0f, 0.0f, 0.0f};
    private static final ImFloat cameraOrbitDistance = new ImFloat(8.0f);
    private static final ImFloat cameraOrbitYaw = new ImFloat(0.0f);
    private static final ImFloat cameraOrbitPitch = new ImFloat(0.0f);

    private static float mouseX;
    private static float mouseY;
    private static int timelineOffset;
    private static int minTicks;
    private static float availableTicks;
    private static float timelineWidth;
    private static float x;
    private static float y;
    private static float width;
    private static float height;

    private static int pendingStepBackwardsTicks = 0;
    private static int cursorTicks;

    private static boolean hoveredControls;
    private static boolean hoveredSkipBackwards;
    private static boolean hoveredSlowDown;
    private static boolean hoveredPause;
    private static boolean hoveredFastForwards;
    private static boolean hoveredSkipForwards;

    private static boolean zoomBarHovered;
    private static int zoomBarHeight;
    private static float zoomBarMin;
    private static float zoomBarMax;
    private static boolean zoomBarExpanded;

    private static final int minorSeparatorHeight = 10;
    private static final int majorSeparatorHeight = minorSeparatorHeight * 2;
    private static final int timestampHeight = 20;
    private static final int middleY = timestampHeight + majorSeparatorHeight;
    private static final int middleX = 240;

    private static final List<SelectedKeyframes> selectedKeyframesList = new ArrayList<>();
    private static int editingKeyframeTrack = 0;
    private static int editingKeyframeTick = 0;

    private static final float[] replayTickSpeeds = new float[]{1.0f, 2.0f, 4.0f, 10.0f, 20.0f, 40.0f, 100.0f, 200.0f, 400.0f};

    private static final int KEYFRAME_SIZE = 10;

    public static int getCursorTick() {
        return cursorTicks;
    }

    public static void render() {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            return;
        }


        String timestamp = ticksToTimestamp(cursorTicks);

        ImGuiHelper.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        boolean timelineVisible = ImGui.begin("Timeline (" + timestamp + "/" + cursorTicks + ")###Timeline");
        ImGuiHelper.popStyleVar();

        cursorTicks = replayServer.getReplayTick();

        if (timelineVisible) {
            FlashbackMeta metadata = replayServer.getMetadata();
            EditorState editorState = EditorStateManager.get(metadata.replayIdentifier);

            ImDrawList drawList = ImGui.getWindowDrawList();

            float maxX = ImGui.getWindowContentRegionMaxX();
            float maxY = ImGui.getWindowContentRegionMaxY();
            float minX = ImGui.getWindowContentRegionMinX();
            float minY = ImGui.getWindowContentRegionMinY();

            x = ImGui.getWindowPosX() + minX;
            y = ImGui.getWindowPosY() + minY;
            width = maxX - minX;
            height = maxY - minY;

            if (width < 1 || height < 1) {
                ImGui.end();
                return;
            }

            selectedKeyframesList.removeIf(k -> !k.checkValid(editorState));

            if (editingKeyframeTrack >= 0 && editingKeyframeTick >= 0) {
                for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                    if (selectedKeyframes.trackIndex() == editingKeyframeTrack) {
                        if (!selectedKeyframes.keyframeTicks().contains(editingKeyframeTick)) {
                            editingKeyframeTrack = -1;
                            editingKeyframeTick = -1;
                        }
                        break;
                    }
                }
            }

            mouseX = ImGui.getMousePosX();
            mouseY = ImGui.getMousePosY();

            int currentReplayTick = replayServer.getReplayTick();
            int totalTicks = replayServer.getTotalReplayTicks();

            timelineWidth = width - middleX;
            float shownTicks = Math.round((editorState.zoomMax - editorState.zoomMin) * totalTicks);
            int targetMajorSize = 60;

            float targetTicksPerMajor = 1f / (timelineWidth / shownTicks / targetMajorSize);
            int minorsPerMajor;
            int ticksPerMinor;
            boolean showSubSeconds;

            if (targetTicksPerMajor < 5) {
                minorsPerMajor = 5;
                ticksPerMinor = 1;
                showSubSeconds = true;
            } else if (targetTicksPerMajor < 8) {
                minorsPerMajor = 5;
                ticksPerMinor = 2;
                showSubSeconds = true;
            } else {
                minorsPerMajor = 4;
                ticksPerMinor = (int) Math.ceil(targetTicksPerMajor / 20 ) * 20 / minorsPerMajor;
                showSubSeconds = false;
            }

            int majorSnap = ticksPerMinor * minorsPerMajor;
            minTicks = (int) Math.round(editorState.zoomMin * totalTicks / majorSnap) * majorSnap;

            float minorSeparatorWidth = (timelineWidth / shownTicks) * ticksPerMinor;
            availableTicks = timelineWidth / minorSeparatorWidth * ticksPerMinor;

            double errorTicks = editorState.zoomMin*totalTicks - minTicks;
            int errorOffset = (int)(-errorTicks/ticksPerMinor*minorSeparatorWidth);
            timelineOffset = middleX + errorOffset;

            cursorTicks = currentReplayTick;
            if (grabbedPlayback) {
                cursorTicks = timelineXToReplayTick(mouseX);
            } else if (replayServer.jumpToTick >= 0) {
                cursorTicks = replayServer.jumpToTick;
            } else if (pendingStepBackwardsTicks > 0) {
                cursorTicks = Math.max(0, cursorTicks - pendingStepBackwardsTicks);
            }

            if (grabbedPlayback && !editorState.keyframeTracks.isEmpty()) {
                boolean isCtrlDown = ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
                boolean isShiftDown = ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);

                if (isShiftDown) {
                    int closestTick = -1;
                    for (KeyframeTrack track : editorState.keyframeTracks) {
                        Integer floor = track.keyframesByTick.floorKey(cursorTicks);
                        Integer ceil = track.keyframesByTick.ceilingKey(cursorTicks);

                        float floorX = floor == null ? Float.NaN : x + replayTickToTimelineX(floor);
                        float ceilX = ceil == null ? Float.NaN : x + replayTickToTimelineX(ceil);

                        Integer closest = Utils.chooseClosest(mouseX, floorX, floor, ceilX, ceil, KEYFRAME_SIZE);
                        if (closest != null) {
                            if (closestTick == -1) {
                                closestTick = closest;
                            } else if (Math.abs(closest - cursorTicks) < Math.abs(closestTick - cursorTicks)) {
                                closestTick = closest;
                            }
                        }
                    }
                    if (closestTick != -1) {
                        cursorTicks = closestTick;
                    }
                }
                if (isCtrlDown) {
                    editorState.applyKeyframes(new MinecraftKeyframeHandler(Minecraft.getInstance()), cursorTicks);
                }
                if (!isCtrlDown && !isShiftDown) {
                    ImGuiHelper.drawTooltip("Hold CTRL to apply keyframes");
                    ImGuiHelper.drawTooltip("Hold SHIFT to snap to keyframes");
                }
            }

            int cursorX = replayTickToTimelineX(cursorTicks);

            zoomBarHeight = 6;
            float zoomBarWidth = width - (middleX+1);
            zoomBarMin = (float)(x + middleX+1 + editorState.zoomMin * zoomBarWidth);
            zoomBarMax = (float)(x + middleX+1 + editorState.zoomMax * zoomBarWidth);

            zoomBarExpanded = false;
            if (mouseY >= y + height - zoomBarHeight*2 && mouseY <= y + height || grabbedZoomBar) {
                zoomBarHeight *= 2;
                zoomBarExpanded = true;
            }
            zoomBarHovered = mouseY >= y + height - zoomBarHeight && mouseY <= y + height &&
                mouseX >= x + zoomBarMin && mouseX <= x + zoomBarMax;

            renderKeyframeElements(replayServer, editorState, x, y + middleY, cursorTicks, middleX);

            drawList.pushClipRect(x + middleX, y, x + width, y + height);

            renderExportBar(editorState, drawList);

            drawList.pushClipRect(x + middleX, y + middleY, x + width, y + height);
            renderKeyframes(editorState, x, y + middleY, mouseX, minTicks, availableTicks, totalTicks);
            drawList.popClipRect();

            renderSeparators(minorsPerMajor, x, middleX, minorSeparatorWidth, errorOffset, width, drawList, y, timestampHeight, middleY, minTicks, ticksPerMinor, showSubSeconds, majorSeparatorHeight, minorSeparatorHeight);
            renderPlaybackHead(cursorX, x, middleX, width, cursorTicks, currentReplayTick, drawList, y, middleY, timestampHeight, height, zoomBarHeight);

            if (dragSelectOrigin != null) {
                drawList.pushClipRect(x + middleX, y + middleY, x + width, y + height);
                drawList.addRectFilled(Math.min(mouseX, dragSelectOrigin.x),
                        Math.min(mouseY, dragSelectOrigin.y), Math.max(mouseX, dragSelectOrigin.x),
                        Math.max(mouseY, dragSelectOrigin.y), 0x80DD6000);
                drawList.addRect(Math.min(mouseX, dragSelectOrigin.x),
                        Math.min(mouseY, dragSelectOrigin.y), Math.max(mouseX, dragSelectOrigin.x),
                        Math.max(mouseY, dragSelectOrigin.y), 0xFFDD6000);
                drawList.popClipRect();
            }

            drawList.popClipRect();

            // Timeline end line
            if (editorState.zoomMax >= 1.0) {
                drawList.addLine(x + width -2, y + timestampHeight, x + width -2, y + height - zoomBarHeight, -1);
            }
            // Middle divider (x)
            drawList.addLine(x + middleX, y + timestampHeight, x + middleX, y + height, -1);

            // Middle divider (y)
            drawList.addLine(0, y + middleY, width, y + middleY, -1);

            // Zoom Bar
            if (zoomBarExpanded) {
                drawList.addRectFilled(x + middleX +1, y + height - zoomBarHeight, x + width, y + height, 0xFF404040, zoomBarHeight);
            }
            if (zoomBarHovered || grabbedZoomBar) {
                drawList.addRectFilled(x + zoomBarMin + zoomBarHeight /2f, y + height - zoomBarHeight, x + zoomBarMax - zoomBarHeight /2f, y + height, -1, zoomBarHeight);

                // Left/right resize
                drawList.addCircleFilled(x + zoomBarMin + zoomBarHeight /2f, y + height - zoomBarHeight /2f, zoomBarHeight /2f, 0xffaaaa00);
                drawList.addCircleFilled(x + zoomBarMax - zoomBarHeight /2f, y + height - zoomBarHeight /2f, zoomBarHeight /2f, 0xffaaaa00);
            } else {
                drawList.addRectFilled(x + zoomBarMin, y + height - zoomBarHeight, x + zoomBarMax, y + height, -1, zoomBarHeight);
            }

            // Pause/play button
            int controlSize = 24;
            int controlsY = (int) y + middleY/2 - controlSize/2;

            // Skip backwards
            int skipBackwardsX = (int) x + middleX/6 - controlSize/2;
            drawList.addTriangleFilled(skipBackwardsX + controlSize/3f, controlsY + controlSize/2f,
                skipBackwardsX + controlSize, controlsY,
                skipBackwardsX + controlSize, controlsY + controlSize, -1);
            drawList.addRectFilled(skipBackwardsX, controlsY,
                skipBackwardsX + controlSize/3f, controlsY+controlSize, -1);

            // Slow down
            int slowDownX = (int) x + middleX*2/6 - controlSize/2;
            drawList.addTriangleFilled(slowDownX, controlsY + controlSize/2f,
                slowDownX + controlSize/2f, controlsY,
                slowDownX + controlSize/2f, controlsY+controlSize, -1);
            drawList.addTriangleFilled(slowDownX + controlSize/2f, controlsY + controlSize/2f,
                slowDownX + controlSize, controlsY,
                slowDownX + controlSize, controlsY + controlSize, -1);

            int pauseX = (int) x + middleX/2 - controlSize/2;
            if (replayServer.replayPaused) {
                // Play button
                drawList.addTriangleFilled(pauseX + controlSize/12f, controlsY,
                    pauseX + controlSize, controlsY + controlSize/2f,
                    pauseX + controlSize/12f, controlsY + controlSize,
                    -1);
            } else {
                // Pause button
                drawList.addRectFilled(pauseX, controlsY,
                    pauseX + controlSize/3f, controlsY + controlSize, -1);
                drawList.addRectFilled(pauseX + controlSize*2f/3f, controlsY,
                    pauseX + controlSize, controlsY + controlSize, -1);
            }

            // Fast-forward
            int fastForwardsX = (int) x + middleX*4/6 - controlSize/2;
            drawList.addTriangleFilled(fastForwardsX, controlsY,
                fastForwardsX + controlSize/2f, controlsY + controlSize/2f,
                fastForwardsX, controlsY+controlSize, -1);
            drawList.addTriangleFilled(fastForwardsX + controlSize/2f, controlsY,
                fastForwardsX + controlSize, controlsY + controlSize/2f,
                fastForwardsX + controlSize/2f, controlsY + controlSize, -1);

            // Skip forward
            int skipForwardsX = (int) x + middleX*5/6 - controlSize/2;
            drawList.addTriangleFilled(skipForwardsX, controlsY,
                skipForwardsX + controlSize*2f/3f, controlsY + controlSize/2f,
                skipForwardsX, controlsY + controlSize, -1);
            drawList.addRectFilled(skipForwardsX + controlSize*2f/3f, controlsY,
                skipForwardsX + controlSize, controlsY+controlSize, -1);


            hoveredControls = mouseY > controlsY && mouseY < controlsY + controlSize;
            hoveredSkipBackwards = hoveredControls && mouseX >= skipBackwardsX && mouseX <= skipBackwardsX+controlSize;
            hoveredSlowDown = hoveredControls && mouseX >= slowDownX && mouseX <= slowDownX+controlSize;
            hoveredPause = hoveredControls && mouseX >= pauseX && mouseX <= pauseX+controlSize;
            hoveredFastForwards = hoveredControls && mouseX >= fastForwardsX && mouseX <= fastForwardsX+controlSize;
            hoveredSkipForwards = hoveredControls && mouseX >= skipForwardsX && mouseX <= skipForwardsX+controlSize;

            float currentTickRate = replayServer.getDesiredTickRate(true);

            if (!grabbedPlayback) {
                if (hoveredSkipBackwards) {
                    ImGuiHelper.drawTooltip("Skip backwards");
                } else if (hoveredSlowDown) {
                    ImGuiHelper.drawTooltip("Slow down\n(Current speed: " + (currentTickRate/20f) + "x)");
                } else if (hoveredPause) {
                    if (replayServer.replayPaused) {
                        ImGuiHelper.drawTooltip("Start replay");
                    } else {
                        ImGuiHelper.drawTooltip("Pause replay");
                    }
                } else if (hoveredFastForwards) {
                    ImGuiHelper.drawTooltip("Fast-forwards\n(Current speed: " + (currentTickRate/20f) + "x)");
                } else if (hoveredSkipForwards) {
                    ImGuiHelper.drawTooltip("Skip forwards");
                }
            }

            if (ImGui.beginPopup("##KeyframePopup")) {
                renderKeyframeOptionsPopup(editorState);
                ImGui.endPopup();
            } else {
                editingKeyframeTrack = -1;
                editingKeyframeTick = -1;
            }

            if (mouseX > x + middleX && mouseX < x + width && mouseY > y + middleY && mouseY < y + height) {
                int scroll = (int) Math.signum(ImGui.getIO().getMouseWheel());

                double mousePercentage = (mouseX - (x + middleX)) / (width - middleX);

                if (scroll > 0) {
                    double zoomDelta = editorState.zoomMax - editorState.zoomMin;
                    if (zoomDelta > 0.001) {
                        editorState.zoomMin += zoomDelta * 0.05 * mousePercentage;
                        editorState.zoomMax -= zoomDelta * 0.05 * (1 - mousePercentage);
                        editorState.markDirty();
                    }
                } else if (scroll < 0) {
                    double zoomDelta = editorState.zoomMax - editorState.zoomMin;

                    editorState.zoomMin = Math.max(0, editorState.zoomMin - zoomDelta * 0.05/0.9 * mousePercentage);
                    editorState.zoomMax = Math.min(1, editorState.zoomMax + zoomDelta * 0.05/0.9 * (1 - mousePercentage));
                    editorState.markDirty();
                }
            }

            boolean shouldProcessInput = !ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopup) && !ImGui.getIO().getWantTextInput();
            if (shouldProcessInput) {
                handleKeyPresses(replayServer, cursorTicks, editorState, totalTicks);

                boolean leftClicked = ImGui.isMouseClicked(ImGuiMouseButton.Left);
                boolean rightClicked = ImGui.isMouseClicked(ImGuiMouseButton.Right);
                if (leftClicked || rightClicked) {
                    handleClick(editorState, replayServer, totalTicks);
                } else if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
                    if (grabbedExportBarResizeLeft) {
                        ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);

                        int target = timelineXToReplayTick(mouseX);
                        editorState.setExportTicks(target, -1, totalTicks);
                    }
                    if (grabbedExportBarResizeRight) {
                        ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);

                        int target = timelineXToReplayTick(mouseX);
                        editorState.setExportTicks(-1, target, totalTicks);
                    }
                    if (zoomBarWidth > 1f && grabbedZoomBar) {
                        float dx = ImGui.getMouseDragDeltaX();
                        float factor = dx / zoomBarWidth;

                        if (grabbedZoomBarResizeLeft) {
                            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
                            editorState.zoomMin = Math.max(0, Math.min(editorState.zoomMax-0.01f, zoomMinBeforeDrag + factor));
                        } else if (grabbedZoomBarResizeRight) {
                            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
                            editorState.zoomMax = Math.max(editorState.zoomMin+0.01f, Math.min(1, zoomMaxBeforeDrag + factor));
                        } else {
                            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);

                            double zoomSize = zoomMaxBeforeDrag - zoomMinBeforeDrag;
                            if (factor < 0) {
                                editorState.zoomMin = Math.max(0, zoomMinBeforeDrag + factor);
                                editorState.zoomMax = editorState.zoomMin + zoomSize;
                            } else if (factor > 0) {
                                editorState.zoomMax = Math.min(1, zoomMaxBeforeDrag + factor);
                                editorState.zoomMin = editorState.zoomMax - zoomSize;
                            }
                        }
                        editorState.markDirty();
                    }
                    if (grabbedPlayback) {
                        int desiredTick = timelineXToReplayTick(mouseX);

                        if (desiredTick > currentReplayTick) {
                            replayServer.goToReplayTick(desiredTick);
                        }

                        replayServer.replayPaused = true;
                    }
                } else if (!ImGui.isAnyMouseDown()) {
                    releaseGrabbed(editorState, replayServer, totalTicks);
                }
            } else {
                releaseGrabbed(editorState, replayServer, totalTicks);
            }
        }
        ImGui.end();
    }

    private static void handleKeyPresses(ReplayServer replayServer, int cursorTicks, EditorState editorState, int totalTicks) {
        boolean pressedIn = ImGui.isKeyPressed(GLFW.GLFW_KEY_I, false);
        boolean pressedOut = ImGui.isKeyPressed(GLFW.GLFW_KEY_O, false);

        boolean ctrlPressed = Minecraft.ON_OSX ? ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_SUPER) : ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL);
        boolean pressedCopy = ctrlPressed && ImGui.isKeyPressed(GLFW.GLFW_KEY_C, false);
        boolean pressedPaste = ctrlPressed && ImGui.isKeyPressed(GLFW.GLFW_KEY_V, false);

        boolean pressedDelete = ImGui.isKeyPressed(GLFW.GLFW_KEY_DELETE) || ImGui.isKeyPressed(GLFW.GLFW_KEY_BACKSPACE);

        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_LEFT)) {
            pendingStepBackwardsTicks += 1;
        } else if (pendingStepBackwardsTicks > 0 && !ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT)) {
            replayServer.goToReplayTick(Math.max(0, replayServer.getReplayTick() - pendingStepBackwardsTicks));
            replayServer.forceApplyKeyframes.set(true);
            pendingStepBackwardsTicks = 0;
        }
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) {
            replayServer.goToReplayTick(Math.min(totalTicks, cursorTicks + 1));
            replayServer.forceApplyKeyframes.set(true);
        }
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_UP)) {
            int nextKeyframeTick;
            if (editorState.exportStartTicks >= 0 && editorState.exportStartTicks > cursorTicks) {
                nextKeyframeTick = editorState.exportStartTicks;
            } else if (editorState.exportEndTicks >= 0 && editorState.exportEndTicks > cursorTicks) {
                nextKeyframeTick = editorState.exportEndTicks;
            } else {
                nextKeyframeTick = totalTicks;
            }
            for (KeyframeTrack track : editorState.keyframeTracks) {
                Integer ceilingKey = track.keyframesByTick.ceilingKey(cursorTicks+1);
                if (ceilingKey != null && ceilingKey < nextKeyframeTick) {
                    nextKeyframeTick = ceilingKey;
                }
            }
            replayServer.goToReplayTick(nextKeyframeTick);
            replayServer.forceApplyKeyframes.set(true);
        }
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
            int previousKeyframeTick;
            if (editorState.exportEndTicks >= 0 && editorState.exportEndTicks < cursorTicks) {
                previousKeyframeTick = editorState.exportEndTicks;
            } else if (editorState.exportStartTicks >= 0 && editorState.exportStartTicks < cursorTicks) {
                previousKeyframeTick = editorState.exportStartTicks;
            } else {
                previousKeyframeTick = 0;
            }
            for (KeyframeTrack track : editorState.keyframeTracks) {
                Integer floorKey = track.keyframesByTick.floorKey(cursorTicks-1);
                if (floorKey != null && floorKey > previousKeyframeTick) {
                    previousKeyframeTick = floorKey;
                }
            }
            replayServer.goToReplayTick(previousKeyframeTick);
            replayServer.forceApplyKeyframes.set(true);
        }
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_Z) && (ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL))) {
            editorState.undo(ReplayUI::setInfoOverlayShort);
        }
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_Y) && (ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL))) {
            editorState.redo(ReplayUI::setInfoOverlayShort);
        }

        if (pressedIn || pressedOut) {
            int start = -1;
            int end = -1;
            if (pressedIn) {
                start = cursorTicks;
            }
            if (pressedOut) {
                end = cursorTicks;
            }
            editorState.setExportTicks(start, end, totalTicks);
        }

        if (pressedDelete && !selectedKeyframesList.isEmpty()) {
            removeAllSelectedKeyframes(editorState);
        }

        if (pressedCopy && !selectedKeyframesList.isEmpty()) {
            List<SavedTrack> tracks = new ArrayList<>();

            int minTick = totalTicks;
            int keyframeCount = 0;

            for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                for (int keyframeTick : selectedKeyframes.keyframeTicks()) {
                    minTick = Math.min(minTick, keyframeTick);
                    keyframeCount += 1;
                }
            }

            for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(selectedKeyframes.trackIndex());

                TreeMap<Integer, Keyframe> keyframes = new TreeMap<>();
                for (int tick : selectedKeyframes.keyframeTicks()) {
                    Keyframe keyframe = keyframeTrack.keyframesByTick.get(tick);
                    keyframes.put(tick - minTick, keyframe);
                }

                tracks.add(new SavedTrack(selectedKeyframes.type(), selectedKeyframes.trackIndex(), !keyframeTrack.enabled, keyframes));
            }

            String serialized = FlashbackGson.COMPRESSED.toJson(tracks);
            GLFW.glfwSetClipboardString(Minecraft.getInstance().getWindow().getWindow(), serialized);

            ReplayUI.setInfoOverlay("Copied " + keyframeCount + " keyframe(s) to clipboard");
        }

        if (pressedPaste) {
            try {
                String clipboard = GLFW.glfwGetClipboardString(Minecraft.getInstance().getWindow().getWindow());
                if (clipboard != null && clipboard.startsWith("[")) {
                    TypeToken<?> typeToken = TypeToken.getParameterized(List.class, SavedTrack.class);

                    // noinspection unchecked
                    List<SavedTrack> tracks = (List<SavedTrack>) FlashbackGson.COMPRESSED.fromJson(clipboard, typeToken);

                    int count = 0;
                    for (SavedTrack savedTrack : tracks) {
                        count += savedTrack.applyToEditorState(editorState, cursorTicks, totalTicks);
                    }

                    ReplayUI.setInfoOverlay("Pasted " + count + " keyframe(s) from clipboard");
                }
            } catch (Exception ignored) {}
        }
    }

    private static void removeAllSelectedKeyframes(EditorState editorState) {
        List<EditorStateHistoryAction> undo = new ArrayList<>();
        List<EditorStateHistoryAction> redo = new ArrayList<>();

        for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
            KeyframeTrack track = editorState.keyframeTracks.get(selectedKeyframes.trackIndex());
            for (int tick : selectedKeyframes.keyframeTicks()) {
                Keyframe keyframe = track.keyframesByTick.get(tick);

                undo.add(new EditorStateHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick, keyframe.copy()));
                redo.add(new EditorStateHistoryAction.RemoveKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick));
            }
        }

        selectedKeyframesList.clear();
        editingKeyframeTrack = -1;
        editingKeyframeTick = -1;
        editorState.push(new EditorStateHistoryEntry(undo, redo, "Deleted " + undo.size() + " keyframe(s)"));
    }

    private static void handleClick(EditorState editorState, ReplayServer replayServer, int totalTicks) {
        releaseGrabbed(editorState, replayServer, totalTicks);
        List<SelectedKeyframes> oldSelectedKeyframesList = new ArrayList<>(selectedKeyframesList);
        selectedKeyframesList.clear();

        boolean leftClicked = ImGui.isMouseClicked(ImGuiMouseButton.Left);
        boolean rightClicked = ImGui.isMouseClicked(ImGuiMouseButton.Right);

        if (hoveredControls) {
            // Skip backwards
            if (hoveredSkipBackwards) {
                replayServer.goToReplayTick(0);
                return;
            }

            // Slow down
            if (hoveredSlowDown) {
                float highest = replayTickSpeeds[0];
                float currentTickRate = replayServer.getDesiredTickRate(true);

                for (float replayTickSpeed : replayTickSpeeds) {
                    if (replayTickSpeed >= currentTickRate) {
                        break;
                    }
                    highest = replayTickSpeed;
                }

                replayServer.setDesiredTickRate(highest, true);
                return;
            }

            // Pause button
            if (hoveredPause) {
                replayServer.replayPaused = !replayServer.replayPaused;
                return;
            }

            // Fast-forward
            if (hoveredFastForwards) {
                float lowest = replayTickSpeeds[replayTickSpeeds.length - 1];
                float currentTickRate = replayServer.getDesiredTickRate(true);

                for (int i = replayTickSpeeds.length - 1; i >= 0; i--) {
                    float replayTickSpeed = replayTickSpeeds[i];
                    if (replayTickSpeed <= currentTickRate) {
                        break;
                    }
                    lowest = replayTickSpeed;
                }

                replayServer.setDesiredTickRate(lowest, true);
                return;
            }

            // Skip forward
            if (hoveredSkipForwards) {
                replayServer.goToReplayTick(replayServer.getTotalReplayTicks());
                return;
            }
        }

        // Timeline
        if (mouseY > y && mouseY < y + middleY && mouseX > x + middleX && mouseX < x + width) {
            if (editorState.exportStartTicks >= 0 && editorState.exportEndTicks >= 0 && mouseY > y + timestampHeight) {
                int exportStartX = replayTickToTimelineX(editorState.exportStartTicks);
                int exportEndX = replayTickToTimelineX(editorState.exportEndTicks);

                Utils.ClosestElement closestElement = Utils.findClosest(mouseX, exportStartX, exportEndX, 5);

                switch (closestElement) {
                    case LEFT -> grabbedExportBarResizeLeft = leftClicked;
                    case RIGHT -> grabbedExportBarResizeRight = leftClicked;
                    case NONE -> {
                        replayServer.replayPaused = true;
                        grabbedPlayback = leftClicked;
                    }
                }
            } else {
                replayServer.replayPaused = true;
                grabbedPlayback = leftClicked;
            }
            return;
        }

        if (zoomBarHovered) {
            if (mouseX <= x + zoomBarMin + zoomBarHeight) {
                grabbedZoomBarResizeLeft = leftClicked;
            } else if (mouseX >= x + zoomBarMax - zoomBarHeight) {
                grabbedZoomBarResizeRight = leftClicked;
            }
            grabbedZoomBar = leftClicked;
            zoomMinBeforeDrag = editorState.zoomMin;
            zoomMaxBeforeDrag = editorState.zoomMax;
            return;
        } else if (zoomBarExpanded && mouseX >= x + middleX && mouseX <= x + width) {
            double zoomSize = editorState.zoomMax - editorState.zoomMin;
            float targetZoom = (mouseX - (x + middleX))/(x + width - (x + middleX));
            editorState.zoomMin = targetZoom - zoomSize/2f;
            editorState.zoomMax = targetZoom + zoomSize/2f;
            if (editorState.zoomMax > 1.0f) {
                editorState.zoomMax = 1.0f;
                editorState.zoomMin = editorState.zoomMax - zoomSize;
            } else if (editorState.zoomMin < 0.0f) {
                editorState.zoomMin = 0.0f;
                editorState.zoomMax = editorState.zoomMin + zoomSize;
            }
            editorState.markDirty();

            grabbedZoomBar = leftClicked;
            zoomMinBeforeDrag = editorState.zoomMin;
            zoomMaxBeforeDrag = editorState.zoomMax;
            return;
        }

        // Tracks
        if (mouseY > y + middleY && mouseY < y + height && mouseX > x + middleX && mouseX < x + width) {
            float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();

            int trackIndex = (int) Math.max(0, Math.floor((mouseY - (y + middleY + 2))/lineHeight));

            if (trackIndex >= 0 && trackIndex < editorState.keyframeTracks.size()) {
                KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(trackIndex);

                int tick = timelineXToReplayTick(mouseX);

                Map.Entry<Integer, Keyframe> floor = keyframeTrack.keyframesByTick.floorEntry(tick);
                Map.Entry<Integer, Keyframe> ceil = keyframeTrack.keyframesByTick.ceilingEntry(tick);

                float floorX = floor == null ? Float.NaN : x + replayTickToTimelineX(floor.getKey());
                float ceilX = ceil == null ? Float.NaN : x + replayTickToTimelineX(ceil.getKey());

                Map.Entry<Integer, Keyframe> closest = Utils.chooseClosest(mouseX, floorX, floor, ceilX, ceil, KEYFRAME_SIZE);
                if (closest != null) {
                    boolean reuseOld = false;
                    for (SelectedKeyframes selectedKeyframes : oldSelectedKeyframesList) {
                        if (selectedKeyframes.trackIndex() == trackIndex) {
                            reuseOld = selectedKeyframes.keyframeTicks().contains((int) closest.getKey());
                            break;
                        }
                    }

                    if (reuseOld) {
                        selectedKeyframesList.addAll(oldSelectedKeyframesList);
                    } else {
                        IntSet intSet = new IntOpenHashSet();
                        intSet.add((int) closest.getKey());
                        selectedKeyframesList.add(new SelectedKeyframes(keyframeTrack.keyframeType, trackIndex, intSet));
                    }

                    if (leftClicked) {
                        grabbedKeyframe = true;
                        grabbedKeyframeMouseX = mouseX;
                        grabbedKeyframeTick = closest.getKey();
                        grabbedKeyframeTrack = trackIndex;
                        enableKeyframeMovement = false;
                    } else if (rightClicked) {
                        ImGui.openPopup("##KeyframePopup");
                        editingKeyframeTrack = trackIndex;
                        editingKeyframeTick = closest.getKey();
                        grabbedKeyframeTrack = trackIndex;
                    }

                    return;
                }
            }

            dragSelectOrigin = new Vector2f(mouseX, mouseY);
        }
    }

    private static void renderExportBar(EditorState editorState, ImDrawList drawList) {
        if (editorState.exportStartTicks >= 0 && editorState.exportEndTicks >= 0) {
            int exportStartX = replayTickToTimelineX(editorState.exportStartTicks);
            int exportEndX = replayTickToTimelineX(editorState.exportEndTicks);
            drawList.addRectFilled(x +exportStartX, y + timestampHeight, x +exportEndX, y + middleY, 0x60FFAA00);
            drawList.addLine(x +exportStartX, y + timestampHeight, x +exportStartX, y + middleY, 0xFFFFAA00, 4);
            drawList.addLine(x +exportEndX, y + timestampHeight, x +exportEndX, y + middleY, 0xFFFFAA00, 4);

            if (mouseY > y + timestampHeight && mouseY < y + middleY) {
                if ((mouseX >= exportStartX-5 && mouseX <= exportStartX+5) || (mouseX >= exportEndX-5 && mouseX <= exportEndX+5)) {
                    ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
                }
            }
        }
    }

    private static void renderKeyframeOptionsPopup(EditorState editorState) {
        if (editingKeyframeTrack < 0 || editingKeyframeTick < 0) {
            return;
        }

        KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(editingKeyframeTrack);
        Keyframe editingKeyframe = keyframeTrack.keyframesByTick.get(editingKeyframeTick);

        if (editingKeyframe == null || selectedKeyframesList.isEmpty()) {
            editingKeyframeTrack = -1;
            ImGui.closeCurrentPopup();
            return;
        }

        editingKeyframe.renderEditKeyframe(updateFunction -> {
            List<EditorStateHistoryAction> undo = new ArrayList<>();
            List<EditorStateHistoryAction> redo = new ArrayList<>();

            int modified = 0;

            for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                KeyframeTrack track = editorState.keyframeTracks.get(selectedKeyframes.trackIndex());
                for (int tick : selectedKeyframes.keyframeTicks()) {
                    Keyframe keyframe = track.keyframesByTick.get(tick);
                    if (keyframe.getClass() == editingKeyframe.getClass()) {
                        modified += 1;

                        undo.add(new EditorStateHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick, keyframe.copy()));
                        updateFunction.accept(keyframe);
                        redo.add(new EditorStateHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick, keyframe.copy()));
                    }
                }
            }

            if (modified > 0) {
                editorState.push(new EditorStateHistoryEntry(undo, redo, "Modified " + modified + " keyframe(s)"));
            }
        });

        if (!(editingKeyframe instanceof TimelapseKeyframe)) {
            int[] type = new int[]{editingKeyframe.interpolationType().ordinal()};
            ImGui.setNextItemWidth(160);
            if (ImGuiHelper.combo("Type", type, InterpolationType.NAMES)) {
                InterpolationType interpolationType = InterpolationType.INTERPOLATION_TYPES[type[0]];

                List<EditorStateHistoryAction> undo = new ArrayList<>();
                List<EditorStateHistoryAction> redo = new ArrayList<>();

                for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                    KeyframeTrack track = editorState.keyframeTracks.get(selectedKeyframes.trackIndex());
                    for (int tick : selectedKeyframes.keyframeTicks()) {
                        Keyframe keyframe = track.keyframesByTick.get(tick);

                        if (keyframe.interpolationType() != interpolationType) {
                            Keyframe changed = keyframe.copy();
                            changed.interpolationType(interpolationType);

                            undo.add(new EditorStateHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick, keyframe.copy()));
                            redo.add(new EditorStateHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick, changed));
                        }
                    }
                }

                editorState.push(new EditorStateHistoryEntry(undo, redo, "Changed interpolation type to " + interpolationType));
            }

            ImInt intWrapper = new ImInt(editingKeyframeTick);
            ImGui.setNextItemWidth(160);
            ImGui.inputInt("Tick", intWrapper, 0);
            int newEditingKeyframeTick = intWrapper.get();

            if (ImGui.isItemDeactivatedAfterEdit() && newEditingKeyframeTick != editingKeyframeTick) {
                for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                    KeyframeTrack track = editorState.keyframeTracks.get(selectedKeyframes.trackIndex());

                    Keyframe possibleKeyframe = track.keyframesByTick.get(editingKeyframeTick);
                    if (possibleKeyframe == editingKeyframe) {
                        List<EditorStateHistoryAction> undo = List.of(
                            new EditorStateHistoryAction.RemoveKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), newEditingKeyframeTick),
                            new EditorStateHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), editingKeyframeTick, editingKeyframe.copy())
                        );
                        List<EditorStateHistoryAction> redo = List.of(
                            new EditorStateHistoryAction.RemoveKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), editingKeyframeTick),
                            new EditorStateHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), newEditingKeyframeTick, editingKeyframe.copy())
                        );

                        editorState.push(new EditorStateHistoryEntry(undo, redo, "Moved 1 keyframe(s)"));
                        selectedKeyframes.keyframeTicks().remove(editingKeyframeTick);
                        selectedKeyframes.keyframeTicks().add(newEditingKeyframeTick);
                        editingKeyframeTick = newEditingKeyframeTick;
                        return;
                    }
                }
            }
        }

        boolean multiple = selectedKeyframesList.size() >= 2 || selectedKeyframesList.getFirst().keyframeTicks().size() >= 2;

        if (ImGui.button(multiple ? "Remove All" : "Remove")) {
            ImGui.closeCurrentPopup();
            removeAllSelectedKeyframes(editorState);
        }

        if (!multiple && editingKeyframe instanceof CameraKeyframe cameraKeyframe) {
            ImGui.sameLine();
            if (ImGui.button("Apply")) {
                cameraKeyframe.apply(new MinecraftKeyframeHandler(Minecraft.getInstance()));
            }
        }
    }

    private static void releaseGrabbed(EditorState editorState, ReplayServer replayServer, int totalTicks) {
        grabbedZoomBar = false;
        grabbedZoomBarResizeLeft = false;
        grabbedZoomBarResizeRight = false;
        grabbedExportBarResizeLeft = false;
        grabbedExportBarResizeRight = false;

        if (dragSelectOrigin != null) {
            float dragMinX = Math.min(dragSelectOrigin.x, mouseX);
            float dragMinY = Math.min(dragSelectOrigin.y, mouseY);
            float dragMaxX = Math.max(dragSelectOrigin.x, mouseX);
            float dragMaxY = Math.max(dragSelectOrigin.y, mouseY);

            float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
            int minTrackIndex = (int) Math.floor((dragMinY - (y + middleY + 2))/lineHeight);
            int maxTrackIndex = (int) Math.floor((dragMaxY - (y + middleY + 2))/lineHeight);
            minTrackIndex = Math.max(0, minTrackIndex);
            maxTrackIndex = Math.min(editorState.keyframeTracks.size()-1, maxTrackIndex);

            for (int trackIndex = minTrackIndex; trackIndex <= maxTrackIndex; trackIndex++) {
                KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(trackIndex);

                int minTick = timelineXToReplayTick(dragMinX - KEYFRAME_SIZE);
                int maxTick = timelineXToReplayTick(dragMaxX + KEYFRAME_SIZE);

                IntSet intSet = new IntOpenHashSet();

                for (int tick = minTick; tick <= maxTick; tick++) {
                    var entry = keyframeTrack.keyframesByTick.ceilingEntry(tick);
                    if (entry == null || entry.getKey() > maxTick) {
                        break;
                    }
                    tick = entry.getKey();
                    intSet.add(tick);
                }

                if (!intSet.isEmpty()) {
                    selectedKeyframesList.add(new SelectedKeyframes(keyframeTrack.keyframeType, trackIndex, intSet));
                }
            }

            dragSelectOrigin = null;
        }

        if (grabbedPlayback) {
            int desiredTick = timelineXToReplayTick(mouseX);
            replayServer.goToReplayTick(desiredTick);
            replayServer.replayPaused = true;
            grabbedPlayback = false;
        }

        if (grabbedKeyframe) {
            GrabMovementInfo grabMovementInfo = calculateGrabMovementInfo(editorState, totalTicks);

            if (grabMovementInfo.grabbedScalePivotTick >= 0 || grabMovementInfo.grabbedDelta != 0) {
                List<EditorStateHistoryAction> undo = new ArrayList<>();
                List<EditorStateHistoryAction> redo = new ArrayList<>();
                int movedKeyframes = 0;

                for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                    int trackIndex = selectedKeyframes.trackIndex();
                    KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(trackIndex);

                    IntList selectedTicks = new IntArrayList(selectedKeyframes.keyframeTicks());

                    // Sorting because clamping can result in overlaps
                    selectedTicks.sort(mouseX < grabbedKeyframeMouseX ? IntComparators.NATURAL_COMPARATOR : IntComparators.OPPOSITE_COMPARATOR);

                    selectedKeyframes.keyframeTicks().clear();

                    Int2ObjectMap<Keyframe> removeFromTick = new Int2ObjectOpenHashMap<>();
                    Int2ObjectMap<Keyframe> addToTick = new Int2ObjectOpenHashMap<>();

                    for (int i = 0; i < selectedTicks.size(); i++) {
                        int tick = selectedTicks.getInt(i);

                        Keyframe keyframe = keyframeTrack.keyframesByTick.get(tick);
                        if (keyframe == null) {
                            continue;
                        }

                        int newTick = tick + grabMovementInfo.grabbedDelta;

                        if (grabMovementInfo.grabbedScalePivotTick >= 0) {
                            int tickDelta = tick - grabMovementInfo.grabbedScalePivotTick;
                            newTick = grabMovementInfo.grabbedScalePivotTick + Math.round(tickDelta * grabMovementInfo.grabbedScaleFactor);
                        }

                        newTick = Math.max(0, Math.min(totalTicks, newTick));

                        if (tick != newTick) {
                            Keyframe copied = keyframe.copy();

                            removeFromTick.put(tick, copied);
                            addToTick.put(newTick, copied);

//                            undo.add(new EditorStateHistoryAction.RemoveKeyframe(keyframeTrack.keyframeType, trackIndex, newTick));
//                            undoAdd.add(new EditorStateHistoryAction.SetKeyframe(keyframeTrack.keyframeType, trackIndex, tick, copied));
//
//                            redo.add(new EditorStateHistoryAction.RemoveKeyframe(keyframeTrack.keyframeType, trackIndex, tick));
//                            redoAdd.add(new EditorStateHistoryAction.SetKeyframe(keyframeTrack.keyframeType, trackIndex, newTick, copied));

                            movedKeyframes += 1;
                        }

                        selectedKeyframes.keyframeTicks().add(newTick);
                    }

                    for (int removeTick : removeFromTick.keySet()) {
                        if (!addToTick.containsKey(removeTick)) {
                            redo.add(new EditorStateHistoryAction.RemoveKeyframe(keyframeTrack.keyframeType, trackIndex, removeTick));
                        }
                    }
                    for (Int2ObjectMap.Entry<Keyframe> entry : addToTick.int2ObjectEntrySet()) {
                        redo.add(new EditorStateHistoryAction.SetKeyframe(keyframeTrack.keyframeType, trackIndex, entry.getIntKey(), entry.getValue()));

                        if (!removeFromTick.containsKey(entry.getIntKey())) {
                            Keyframe existing = keyframeTrack.keyframesByTick.get(entry.getIntKey());
                            if (existing != null) {
                                undo.add(new EditorStateHistoryAction.SetKeyframe(keyframeTrack.keyframeType, trackIndex, entry.getIntKey(), existing.copy()));
                            } else {
                                undo.add(new EditorStateHistoryAction.RemoveKeyframe(keyframeTrack.keyframeType, trackIndex, entry.getIntKey()));
                            }
                        }
                    }
                    for (Int2ObjectMap.Entry<Keyframe> entry : removeFromTick.int2ObjectEntrySet()) {
                        undo.add(new EditorStateHistoryAction.SetKeyframe(keyframeTrack.keyframeType, trackIndex, entry.getIntKey(), entry.getValue()));
                    }
                }

                editorState.push(new EditorStateHistoryEntry(undo, redo, "Moved " + movedKeyframes + " keyframe(s)"));
            }

            grabbedKeyframe = false;
        }

        if (pendingStepBackwardsTicks > 0 && !ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT)) {
            replayServer.goToReplayTick(Math.max(0, replayServer.getReplayTick() - pendingStepBackwardsTicks));
            replayServer.forceApplyKeyframes.set(true);
            pendingStepBackwardsTicks = 0;
        }
    }

    record GrabMovementInfo(int grabbedDelta, int grabbedScalePivotTick, float grabbedScaleFactor) {}

    private static GrabMovementInfo calculateGrabMovementInfo(EditorState editorState, int totalTicks) {
        int grabbedDelta = 0;
        int grabbedScalePivotTick = -1;
        float grabbedScaleFactor = 0f;

        if (ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT)) {
            int minTick = totalTicks;
            int maxTick = 0;

            for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                IntIterator iterator = selectedKeyframes.keyframeTicks().iterator();
                while (iterator.hasNext()) {
                    int tick = iterator.nextInt();
                    minTick = Math.min(minTick, tick);
                    maxTick = Math.max(maxTick, tick);
                }
            }

            if (minTick == maxTick) {
                ImGuiHelper.drawTooltip("Scale: 100%");
            } else {
                if (minTick == grabbedKeyframeTick || minTick == timelineXToReplayTick(grabbedKeyframeMouseX - x)) {
                    grabbedScalePivotTick = maxTick;
                } else {
                    grabbedScalePivotTick = minTick;
                }

                float pivotMouseX = x + replayTickToTimelineX(grabbedScalePivotTick);
                grabbedScaleFactor = (mouseX - pivotMouseX) / (grabbedKeyframeMouseX - pivotMouseX);

                if (!Float.isFinite(grabbedScaleFactor)) {
                    grabbedScaleFactor = 1f;
                }

                ImGuiHelper.drawTooltip("Scale: " + Math.round(grabbedScaleFactor*100) + "%");
            }

            return new GrabMovementInfo(0, grabbedScalePivotTick, grabbedScaleFactor);
        } else if (grabbedKeyframeMouseX != mouseX || enableKeyframeMovement) {
            enableKeyframeMovement = true;
            grabbedDelta = timelineDeltaToReplayTickDelta(mouseX - grabbedKeyframeMouseX);

            boolean isShiftDown = ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);

            if (isShiftDown) {
                int closestTick = -1;
                for (int i = 0; i < editorState.keyframeTracks.size(); i++) {
                    if (i == grabbedKeyframeTrack) {
                        continue;
                    }

                    KeyframeTrack track = editorState.keyframeTracks.get(i);

                    Integer floor = track.keyframesByTick.floorKey(grabbedKeyframeTick);
                    Integer ceil = track.keyframesByTick.ceilingKey(grabbedKeyframeTick);

                    float floorX = floor == null ? Float.NaN : x + replayTickToTimelineX(floor);
                    float ceilX = ceil == null ? Float.NaN : x + replayTickToTimelineX(ceil);

                    Integer closest = Utils.chooseClosest(mouseX, floorX, floor, ceilX, ceil, KEYFRAME_SIZE);
                    if (closest != null) {
                        if (closestTick == -1) {
                            closestTick = closest;
                        } else if (Math.abs(closest - grabbedKeyframeTick) < Math.abs(closestTick - grabbedKeyframeTick)) {
                            closestTick = closest;
                        }
                    }
                }
                if (closestTick != -1) {
                    grabbedDelta = closestTick - grabbedKeyframeTick;
                }
            }

            String tooltip = "Tick: " + (grabbedKeyframeTick + grabbedDelta);

            if (grabbedDelta >= 0) {
                tooltip += " (+" + grabbedDelta + " ticks)";
            } else {
                tooltip += " (" + grabbedDelta + " ticks)";
            }

            if (!isShiftDown && editorState.keyframeTracks.size() > 1) {
                tooltip += "\nHold SHIFT to snap";
            }

            ImGuiHelper.drawTooltip(tooltip);
        }

        return new GrabMovementInfo(grabbedDelta, grabbedScalePivotTick, grabbedScaleFactor);
    }

    private static void renderKeyframes(EditorState editorState, float x, float y, float mouseX, int minTicks, float availableTicks, int totalTicks) {
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();

        ImDrawList drawList = ImGui.getWindowDrawList();
        ImDrawList foregroundDrawList = ImGui.getForegroundDrawList();

        foregroundDrawList.pushClipRect(x + middleX, y, x + width, y + height);

        GrabMovementInfo grabMovementInfo = null;
        if (grabbedKeyframe) {
            grabMovementInfo = calculateGrabMovementInfo(editorState, totalTicks);
        }

        for (int trackIndex = 0; trackIndex < editorState.keyframeTracks.size(); trackIndex++) {
            KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(trackIndex);

            TreeMap<Integer, Keyframe> keyframeTimes = keyframeTrack.keyframesByTick;

            SelectedKeyframes selectedKeyframesForTrack = null;
            for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                if (selectedKeyframes.trackIndex() == trackIndex) {
                    selectedKeyframesForTrack = selectedKeyframes;
                    break;
                }
            }

            for (int tick = minTicks; tick <= minTicks + availableTicks; tick++) {
                var entry = keyframeTimes.ceilingEntry(tick);
                if (entry == null || entry.getKey() > minTicks + availableTicks) {
                    break;
                }
                tick = entry.getKey();

                Keyframe keyframe = entry.getValue();

                if (selectedKeyframesForTrack != null && selectedKeyframesForTrack.keyframeTicks().contains(tick)) {
                    int newTick = tick;

                    if (grabMovementInfo != null) {
                        newTick = tick + grabMovementInfo.grabbedDelta;

                        if (grabMovementInfo.grabbedScalePivotTick >= 0) {
                            int tickDelta = tick - grabMovementInfo.grabbedScalePivotTick;
                            newTick = grabMovementInfo.grabbedScalePivotTick + Math.round(tickDelta * grabMovementInfo.grabbedScaleFactor);
                        }

                        newTick = Math.max(0, Math.min(totalTicks, newTick));
                    }

                    int keyframeX = replayTickToTimelineX(newTick);

                    float midX = x + keyframeX;
                    float midY = y + 2 + (trackIndex+0.5f) * lineHeight;

                    drawKeyframe(foregroundDrawList, keyframe.interpolationType(), midX, midY, keyframeTrack.enabled ? 0xFF0000FF : 0x800000FF);
                } else {
                    int keyframeX = replayTickToTimelineX(tick);

                    float midX = x + keyframeX;
                    float midY = y + 2 + (trackIndex+0.5f) * lineHeight;

                    drawKeyframe(drawList, keyframe.interpolationType(), midX, midY, keyframeTrack.enabled ? -1 : 0x80FFFFFF);
                }
            }
        }

        foregroundDrawList.popClipRect();
    }

    private static void drawKeyframe(ImDrawList drawList, InterpolationType interpolationType, float x, float y, int colour) {
        int easeSize = KEYFRAME_SIZE / 5;
        switch (interpolationType) {
            case SMOOTH -> {
                drawList.addCircleFilled(x, y, KEYFRAME_SIZE, colour);
            }
            case LINEAR -> {
                drawList.addTriangleFilled(x - KEYFRAME_SIZE, y, x, y - KEYFRAME_SIZE, x, y + KEYFRAME_SIZE, colour);
                drawList.addTriangleFilled(x + KEYFRAME_SIZE, y, x, y + KEYFRAME_SIZE, x, y - KEYFRAME_SIZE, colour);
            }
            case EASE_IN -> {
                // Left inverted triangle
                drawList.addTriangleFilled(x - KEYFRAME_SIZE, y - KEYFRAME_SIZE, x - easeSize, y - KEYFRAME_SIZE, x - easeSize, y, colour);
                drawList.addTriangleFilled(x - KEYFRAME_SIZE, y + KEYFRAME_SIZE, x - easeSize, y, x - easeSize, y + KEYFRAME_SIZE, colour);
                // Right triangle
                drawList.addTriangleFilled(x + KEYFRAME_SIZE, y, x + easeSize, y + KEYFRAME_SIZE, x + easeSize, y - KEYFRAME_SIZE, colour);
                // Center
                drawList.addRectFilled(x - easeSize, y - KEYFRAME_SIZE, x + easeSize, y + KEYFRAME_SIZE, colour);
            }
            case EASE_OUT -> {
                // Left triangle
                drawList.addTriangleFilled(x - KEYFRAME_SIZE, y, x - easeSize, y - KEYFRAME_SIZE, x - easeSize, y + KEYFRAME_SIZE, colour);
                // Right inverted triangle
                drawList.addTriangleFilled(x + KEYFRAME_SIZE, y - KEYFRAME_SIZE, x + easeSize, y, x + easeSize, y - KEYFRAME_SIZE, colour);
                drawList.addTriangleFilled(x + KEYFRAME_SIZE, y + KEYFRAME_SIZE, x + easeSize, y + KEYFRAME_SIZE, x + easeSize, y, colour);
                // Center
                drawList.addRectFilled(x - easeSize, y - KEYFRAME_SIZE, x + easeSize, y + KEYFRAME_SIZE, colour);
            }
            case EASE_IN_OUT -> {
                // Left inverted triangle
                drawList.addTriangleFilled(x - KEYFRAME_SIZE, y - KEYFRAME_SIZE, x - easeSize, y - KEYFRAME_SIZE, x - easeSize, y, colour);
                drawList.addTriangleFilled(x - KEYFRAME_SIZE, y + KEYFRAME_SIZE, x - easeSize, y, x - easeSize, y + KEYFRAME_SIZE, colour);
                // Right inverted triangle
                drawList.addTriangleFilled(x + KEYFRAME_SIZE, y - KEYFRAME_SIZE, x + easeSize, y, x + easeSize, y - KEYFRAME_SIZE, colour);
                drawList.addTriangleFilled(x + KEYFRAME_SIZE, y + KEYFRAME_SIZE, x + easeSize, y + KEYFRAME_SIZE, x + easeSize, y, colour);
                // Center
                drawList.addRectFilled(x - easeSize, y - KEYFRAME_SIZE, x + easeSize, y + KEYFRAME_SIZE, colour);
            }
            case HOLD -> {
                drawList.addRectFilled(x - KEYFRAME_SIZE, y - KEYFRAME_SIZE, x + KEYFRAME_SIZE, y + KEYFRAME_SIZE, colour);
            }
        }
    }

    private static void renderKeyframeElements(ReplayServer replayServer, EditorState editorState, float x, float y, int cursorTicks, int middleX) {
        ImGui.setCursorScreenPos(x + 8, y + 6);

        int keyframeTrackToClear = -1;

        ImDrawList drawList = ImGui.getWindowDrawList();

        float buttonSize = ImGui.getTextLineHeight();
        float spacingX = ImGui.getStyle().getItemSpacingX();

        for (int trackIndex = 0; trackIndex < editorState.keyframeTracks.size(); trackIndex++) {
            KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(trackIndex);
            KeyframeType keyframeType = keyframeTrack.keyframeType;

            ImGui.pushID(trackIndex);

            ImGui.setCursorPosX(8);

            if (keyframeTrack.enabled) {
                ImGui.text(keyframeType.name);
            } else {
                ImGui.textDisabled(keyframeType.name);
            }

            ImGui.sameLine();

            float buttonX = middleX - (buttonSize + spacingX) * 3;
            float buttonY = ImGui.getCursorScreenPosY();
            ImGui.setCursorPosX(buttonX);
            if (ImGui.button("##Add", buttonSize, buttonSize)) {
                LocalPlayer player = Minecraft.getInstance().player;
                Keyframe keyframe = switch (keyframeType) {
                    case CAMERA -> new CameraKeyframe(player);
                    case CAMERA_ORBIT -> {
                        Vec3 eyePosition = player.getEyePosition();
                        cameraOrbitCenter[0] = (float) eyePosition.x;
                        cameraOrbitCenter[1] = (float) eyePosition.y;
                        cameraOrbitCenter[2] = (float) eyePosition.z;
                        ImGui.openPopup("##EnterCameraOrbit");
                        yield null;
                    }
                    case FOV -> {
                        fovKeyframeInput[0] = editorState.replayVisuals.overrideFov ? editorState.replayVisuals.overrideFovAmount : Minecraft.getInstance().options.fov().get();
                        ImGui.openPopup("##EnterFov");
                        yield null;
                    }
                    case SPEED -> {
                        speedKeyframeInput[0] = replayServer.getDesiredTickRate(false) / 20.0f;
                        ImGui.openPopup("##EnterSpeed");
                        yield null;
                    }
                    case TIMELAPSE -> {
                        ImGui.openPopup("##EnterTimelapse");
                        yield null;
                    }
                    case TIME_OF_DAY -> {
                        if (editorState.replayVisuals.overrideTimeOfDay >= 0) {
                            timeOfDayKeyframeInput.set((int) editorState.replayVisuals.overrideTimeOfDay);
                        } else {
                            timeOfDayKeyframeInput.set((int)(Minecraft.getInstance().level.getDayTime() % 24000));
                        }
                        ImGui.openPopup("##EnterTime");
                        yield null;
                    }
                    case CAMERA_SHAKE -> {
                        if (editorState.replayVisuals.overrideCameraShake) {
                            cameraShakeSplitXYKeyframeInput = editorState.replayVisuals.cameraShakeSplitParams;
                            cameraShakeFrequencyXKeyframeInput[0] = editorState.replayVisuals.cameraShakeXFrequency;
                            cameraShakeAmplitudeXKeyframeInput[0] = editorState.replayVisuals.cameraShakeXAmplitude;
                            cameraShakeFrequencyYKeyframeInput[0] = editorState.replayVisuals.cameraShakeYFrequency;
                            cameraShakeAmplitudeYKeyframeInput[0] = editorState.replayVisuals.cameraShakeYAmplitude;
                            ImGui.openPopup("##EnterCameraShake");
                            yield null;
                        } else {
                            cameraShakeSplitXYKeyframeInput = false;
                            cameraShakeFrequencyXKeyframeInput[0] = 1.0f;
                            cameraShakeAmplitudeXKeyframeInput[0] = 0.0f;
                            cameraShakeFrequencyYKeyframeInput[0] = 1.0f;
                            cameraShakeAmplitudeYKeyframeInput[0] = 0.0f;
                            ImGui.openPopup("##EnterCameraShake");
                            yield null;
                        }
                    }
                };
                if (keyframe != null) {
                    editorState.setKeyframe(trackIndex, cursorTicks, keyframe);
                }
            }
            drawList.addRectFilled(buttonX + 2, buttonY + buttonSize/2 - 1, buttonX + buttonSize - 2, buttonY + buttonSize/2 + 1, -1);
            drawList.addRectFilled(buttonX + buttonSize/2 - 1, buttonY + 2, buttonX + buttonSize/2 + 1, buttonY + buttonSize - 2, -1);
            ImGuiHelper.tooltip("Add keyframe");

            if (ImGui.beginPopup("##EnterSpeed")) {
                ImGui.sliderFloat("Speed", speedKeyframeInput, 0.1f, 10f);
                if (ImGui.button("Add")) {
                    editorState.setKeyframe(trackIndex, cursorTicks, new TickrateKeyframe(speedKeyframeInput[0] * 20.0f));
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }
            if (ImGui.beginPopup("##EnterTimelapse")) {
                ImGui.inputText("Time", timelapseKeyframeInput);
                if (ImGui.button("Add")) {
                    editorState.setKeyframe(trackIndex, cursorTicks, new TimelapseKeyframe(Utils.stringToTime(ImGuiHelper.getString(timelapseKeyframeInput))));
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }
            if (ImGui.beginPopup("##EnterTime")) {
                ImGui.inputInt("Time", timeOfDayKeyframeInput, 0);
                if (ImGui.button("Add")) {
                    editorState.setKeyframe(trackIndex, cursorTicks, new TimeOfDayKeyframe(timeOfDayKeyframeInput.get()));
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }
            if (ImGui.beginPopup("##EnterFov")) {
                ImGui.sliderFloat("FOV", fovKeyframeInput, 1f, 110f);
                if (ImGui.button("Add")) {
                    editorState.setKeyframe(trackIndex, cursorTicks, new FOVKeyframe(fovKeyframeInput[0]));
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }
            if (ImGui.beginPopup("##EnterCameraOrbit")) {
                ImGui.inputFloat3("Position", cameraOrbitCenter);
                ImGui.inputFloat("Distance", cameraOrbitDistance);
                ImGui.inputFloat("Yaw", cameraOrbitYaw);
                ImGui.inputFloat("Pitch", cameraOrbitPitch);

                if (ImGui.button("Add")) {
                    Vector3f center = new Vector3f(cameraOrbitCenter[0], cameraOrbitCenter[1], cameraOrbitCenter[2]);
                    Keyframe keyframe = new CameraOrbitKeyframe(center, cameraOrbitDistance.get(), cameraOrbitYaw.get(), cameraOrbitPitch.get());
                    editorState.setKeyframe(trackIndex, cursorTicks, keyframe);
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }
            if (ImGui.beginPopup("##EnterCameraShake")) {
                if (ImGui.checkbox("Split Y/X", cameraShakeSplitXYKeyframeInput)) {
                    cameraShakeSplitXYKeyframeInput = !cameraShakeSplitXYKeyframeInput;
                    if (!cameraShakeSplitXYKeyframeInput) {
                        cameraShakeFrequencyYKeyframeInput[0] = cameraShakeFrequencyXKeyframeInput[0];
                        cameraShakeAmplitudeYKeyframeInput[0] = cameraShakeAmplitudeXKeyframeInput[0];
                    }
                }

                if (cameraShakeSplitXYKeyframeInput) {
                    ImGui.sliderFloat("Frequency X", cameraShakeFrequencyXKeyframeInput, 0.1f, 10.0f, "%.1f");
                    ImGui.sliderFloat("Amplitude X", cameraShakeAmplitudeXKeyframeInput, 0.0f, 10.0f, "%.1f");
                    ImGui.sliderFloat("Frequency Y", cameraShakeFrequencyYKeyframeInput, 0.1f, 10.0f, "%.1f");
                    ImGui.sliderFloat("Amplitude Y", cameraShakeAmplitudeYKeyframeInput, 0.0f, 10.0f, "%.1f");
                } else {
                    ImGui.sliderFloat("Frequency", cameraShakeFrequencyXKeyframeInput, 0.1f, 10.0f, "%.1f");
                    ImGui.sliderFloat("Amplitude", cameraShakeAmplitudeXKeyframeInput, 0.0f, 10.0f, "%.1f");
                }

                if (ImGui.button("Add")) {
                    Keyframe keyframe;
                    if (cameraShakeSplitXYKeyframeInput) {
                        keyframe = new CameraShakeKeyframe(cameraShakeFrequencyXKeyframeInput[0], cameraShakeAmplitudeXKeyframeInput[0],
                            cameraShakeFrequencyYKeyframeInput[0], cameraShakeAmplitudeYKeyframeInput[0]);
                    } else {
                        keyframe = new CameraShakeKeyframe(cameraShakeFrequencyXKeyframeInput[0], cameraShakeAmplitudeXKeyframeInput[0],
                            cameraShakeFrequencyXKeyframeInput[0], cameraShakeAmplitudeXKeyframeInput[0]);
                    }
                    editorState.setKeyframe(trackIndex, cursorTicks, keyframe);
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }

            ImGui.sameLine();

            buttonX += buttonSize + spacingX;
            ImGui.setCursorPosX(buttonX);
            if (ImGui.button("##Clear", buttonSize, buttonSize)) {
                keyframeTrackToClear = trackIndex;
            }
            drawList.addRectFilled(buttonX + 2, buttonY + buttonSize/2 - 1, buttonX + buttonSize - 2, buttonY + buttonSize/2 + 1, -1);
            ImGuiHelper.tooltip("Remove all keyframes");

            ImGui.sameLine();

            buttonX += buttonSize + spacingX;
            ImGui.setCursorPosX(buttonX);
            if (ImGui.button("##ToggleEnabled", buttonSize, buttonSize)) {
                keyframeTrack.enabled = !keyframeTrack.enabled;
            }
            if (keyframeTrack.enabled) {
                drawList.addCircleFilled(buttonX + buttonSize/2, buttonY + buttonSize/2, buttonSize/3, -1);
                ImGuiHelper.tooltip("Disable keyframe track");
            } else {
                drawList.addCircle(buttonX + buttonSize/2, buttonY + buttonSize/2, buttonSize/3, -1, 16, 2);
                ImGuiHelper.tooltip("Enable keyframe track");
            }

            ImGui.separator();

            ImGui.popID();
        }

        if (keyframeTrackToClear >= 0) {
            List<EditorStateHistoryAction> undo = new ArrayList<>();
            List<EditorStateHistoryAction> redo = new ArrayList<>();

            KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(keyframeTrackToClear);

            undo.add(new EditorStateHistoryAction.AddTrack(keyframeTrack.keyframeType, keyframeTrackToClear));
            for (Map.Entry<Integer, Keyframe> entry : keyframeTrack.keyframesByTick.entrySet()) {
                undo.add(new EditorStateHistoryAction.SetKeyframe(keyframeTrack.keyframeType, keyframeTrackToClear, entry.getKey(), entry.getValue().copy()));
            }

            redo.add(new EditorStateHistoryAction.RemoveTrack(keyframeTrack.keyframeType, keyframeTrackToClear));

            editorState.push(new EditorStateHistoryEntry(undo, redo, "Delete " + keyframeTrack.keyframeType + " track"));
            selectedKeyframesList.clear();
        }

        ImGui.setCursorPosX(8);
        if (ImGui.smallButton("Add Element")) {
            ImGui.openPopup("##AddKeyframeElement");
        }
        if (ImGui.beginPopup("##AddKeyframeElement")) {
            for (KeyframeType keyframeType : KeyframeType.KEYFRAME_TYPES) {
                if (ImGui.selectable(keyframeType.name)) {
                    List<EditorStateHistoryAction> undo = new ArrayList<>();
                    List<EditorStateHistoryAction> redo = new ArrayList<>();

                    int index = editorState.keyframeTracks.size();
                    undo.add(new EditorStateHistoryAction.RemoveTrack(keyframeType, index));
                    redo.add(new EditorStateHistoryAction.AddTrack(keyframeType, index));

                    editorState.push(new EditorStateHistoryEntry(undo, redo, "Create " + keyframeType + " track"));
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.endPopup();
        }
    }

    private static void renderSeparators(int minorsPerMajor, float x, int middleX, float minorSeparatorWidth, int errorOffset, float width, ImDrawList drawList, float y, int timestampHeight, int middleY, int minTicks, int ticksPerMinor, boolean showSubSeconds, int majorSeparatorHeight, int minorSeparatorHeight) {
        int minor = -minorsPerMajor;
        while (true) {
            float h = x + middleX + minorSeparatorWidth *minor;
            int hi = (int) (h + errorOffset);

            if (hi >= x + width) {
                break;
            }

            if (minor % minorsPerMajor == 0) {
                drawList.addLine(hi, y + timestampHeight, hi, y + middleY, -1);

                int ticks = minTicks + minor* ticksPerMinor;
                String timestamp = ticksToTimestamp(ticks);
                drawList.addText(hi, y, -1, timestamp);
                if (showSubSeconds) {
                    float timestampWidth = ImGuiHelper.calcTextWidth(timestamp);
                    drawList.addText(hi+(int)Math.ceil(timestampWidth), y, 0xFF808080, "/"+(ticks % 20));
                }
            } else {
                drawList.addLine(hi, y + timestampHeight +(majorSeparatorHeight - minorSeparatorHeight), hi, y + middleY, -1);
            }

            minor += 1;
        }
    }

    private static void renderPlaybackHead(int cursorX, float x, int middleX, float width, int cursorTicks, int currentReplayTick, ImDrawList drawList, float y, int middleY, int timestampHeight, float height, int zoomBarHeight) {
        if (cursorX > x + middleX -10 && cursorX < x + width +10) {
            int colour = -1;
            if (cursorTicks < currentReplayTick) {
                colour = 0x80FFFFFF;
            }

            drawList.addTriangleFilled(x + cursorX, y + middleY, x + cursorX -10, y + timestampHeight +5,
                x + cursorX +10, y + timestampHeight +5, colour);
            drawList.addRectFilled(x + cursorX -1, y + middleY -2, x + cursorX +1, y + height - zoomBarHeight, colour);
        }
    }

    private static int replayTickToTimelineX(int tick) {
        return timelineOffset + (int) ((tick - minTicks) / availableTicks * timelineWidth);
    }

    private static int timelineXToReplayTick(float x) {
        float relativeX = x - timelineOffset;
        float amount = Math.max(0, Math.min(1, relativeX/timelineWidth));
        int numTicks = Math.round(amount * availableTicks);
        return minTicks + numTicks;
    }

    private static int timelineDeltaToReplayTickDelta(float x) {
        return Math.round(x / timelineWidth * availableTicks);
    }

    private static String ticksToTimestamp(int ticks) {
        int seconds = ticks/20;
        int minutes = seconds/60;
        int hours = minutes/60;

        if (hours == 0) {
            return String.format("%02d:%02d", minutes, seconds % 60);
        } else {
            return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
        }
    }

}
