package com.moulberry.flashback.editor.ui.windows;

import com.google.gson.reflect.TypeToken;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.editor.CopiedKeyframes;
import com.moulberry.flashback.editor.SavedTrack;
import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.KeyframeRelativeOffsets;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.KeyframeRegistry;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.keyframe.types.CameraKeyframeType;
import com.moulberry.flashback.keyframe.types.TimelapseKeyframeType;
import com.moulberry.flashback.record.ReplayMarker;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.keyframe.impl.TimelapseKeyframe;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiComboFlags;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class TimelineWindow {

    private static EditorState editorState;
    private static long editorSceneStamp;
    private static boolean editorSceneStampIsWrite;
    private static EditorScene editorScene;
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
    private static int draggingMouseButton = ImGuiMouseButton.Left;
    private static int repositioningKeyframeTrack = 0;
    private static float dragStartMouseX = 0;
    private static float dragStartMouseY = 0;

    private static boolean trackDisabledButtonDrag = false;
    private static boolean trackDisabledButtonDragValue = false;

    @Nullable
    private static Vector2f dragSelectOrigin = null;

    private static KeyframeType.KeyframeCreatePopup<?> createKeyframeWithPopup = null;
    private static int createKeyframeWithPopupTick = 0;
    private static ImString sceneNameString = null;
    private static boolean copyRelativeToPosition = false;
    private static boolean copyRelativeToYaw = false;
    private static boolean copyRelativeToPitch = false;

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

    private static long lastRenderNanos = 0;
    private static long renderDeltaNanos = 0;

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

    private static int minorSeparatorHeight = 10;
    private static int majorSeparatorHeight = minorSeparatorHeight * 2;
    private static int timestampHeight = 20;
    private static int middleY = timestampHeight + majorSeparatorHeight;
    private static int middleX = 240;
    private static int keyframeSize = 10;

    private static final List<SelectedKeyframes> selectedKeyframesList = new ArrayList<>();
    private static int editingKeyframeTrack = 0;
    private static int editingKeyframeTick = 0;

    private static int createKeyframeAtTick = 0;
    private static int openCreateKeyframeAtTickTrack = -1;

    private static final float[] replayTickSpeeds = new float[]{1.0f, 2.0f, 4.0f, 10.0f, 20.0f, 40.0f, 100.0f, 200.0f, 400.0f};

    public static int getCursorTick() {
        return cursorTicks;
    }

    public static void render() {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            return;
        }

        String timestamp = ticksToTimestamp(cursorTicks);

        boolean hoveredBody = mouseX > x && mouseX < x + width && mouseY > y && mouseY < y + height;

        ImGuiHelper.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        int flags = ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoScrollbar;
        if (hoveredBody) {
            flags |= ImGuiWindowFlags.NoMove;
        }
        boolean timelineVisible = ImGui.begin("Timeline (" + timestamp + "/" + cursorTicks + ")###Timeline", flags);
        ImGuiHelper.popStyleVar();

        cursorTicks = replayServer.getReplayTick();

        if (timelineVisible) {
            FlashbackMeta metadata = replayServer.getMetadata();
            editorState = EditorStateManager.get(metadata.replayIdentifier);

            editorSceneStamp = editorState.acquireRead();
            editorSceneStampIsWrite = false;
            try {
                editorScene = editorState.getCurrentScene(editorSceneStamp);
                renderInner(replayServer, metadata);
            } finally {
                editorState.release(editorSceneStamp);
                editorSceneStamp = 0L;
                editorSceneStampIsWrite = false;
                editorScene = null;
            }
        }
        ImGui.end();
    }

    private static void renderInner(ReplayServer replayServer, FlashbackMeta metadata) {
        ImDrawList drawList = ImGui.getWindowDrawList();

        float maxX = ImGui.getWindowContentRegionMaxX();
        float maxY = ImGui.getWindowContentRegionMaxY();
        float minX = ImGui.getWindowContentRegionMinX();
        float minY = ImGui.getWindowContentRegionMinY();

        x = ImGui.getWindowPosX() + minX;
        y = ImGui.getWindowPosY() + minY;
        width = maxX - minX;
        height = maxY - minY;

        minorSeparatorHeight = ReplayUI.scaleUi(10);
        majorSeparatorHeight = minorSeparatorHeight * 2;
        timestampHeight = ReplayUI.scaleUi(20);
        middleY = timestampHeight + majorSeparatorHeight;
        middleX = ReplayUI.scaleUi(240);
        keyframeSize = ReplayUI.scaleUi(10);

        float totalTrackHeight = (editorScene.keyframeTracks.size() + 1) * (ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY());
        boolean showTrackScroll = totalTrackHeight > height - middleY;

        if (showTrackScroll) {
            width -= ImGui.getStyle().getScrollbarSize() - 1;
        }

        if (width < 1 || height < 1) {
            return;
        }

        selectedKeyframesList.removeIf(k -> !k.checkValid(editorScene));

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

        long currentTime = System.nanoTime();
        renderDeltaNanos = Math.max(0, Math.min(1_000_000_000, currentTime - lastRenderNanos));
        lastRenderNanos = currentTime;

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
            cursorTicks = timelineXToReplayTick(mouseX - x);
        } else if (replayServer.jumpToTick >= 0) {
            cursorTicks = replayServer.jumpToTick;
        } else if (pendingStepBackwardsTicks > 0) {
            cursorTicks = Math.max(0, cursorTicks - pendingStepBackwardsTicks);
        }

        if (grabbedPlayback && !editorScene.keyframeTracks.isEmpty()) {
            boolean isCtrlDown = ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
            boolean isShiftDown = ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);

            if (isShiftDown) {
                int closestTick = findClosestKeyframeForSnap(cursorTicks);
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
            mouseX >= zoomBarMin && mouseX <= zoomBarMax;

        // Middle divider (x)
        drawList.addLine(x + middleX, y + timestampHeight, x + middleX, y + height, -1);

        // Middle divider (y)
        drawList.addLine(x, y + middleY, x + width - 2, y + middleY, -1);

        ImGui.dummy(0, middleY - 1);

        int timelineContentsFlags = ImGuiWindowFlags.NoScrollWithMouse;
        if (showTrackScroll) {
            timelineContentsFlags |= ImGuiWindowFlags.AlwaysVerticalScrollbar;
        } else {
            timelineContentsFlags |= ImGuiWindowFlags.NoScrollbar;
        }
        ImGui.beginChild("##TimelineContents", 0, 0, false, timelineContentsFlags);

        ImDrawList childDrawList = ImGui.getWindowDrawList();

        float contentY = y + middleY - ImGui.getScrollY();
        renderKeyframeElements(x, contentY, cursorTicks, middleX);

        childDrawList.pushClipRect(x + middleX, y + middleY, x + width, y + height);
        renderKeyframes(x, contentY, mouseX, minTicks, availableTicks, totalTicks);
        childDrawList.popClipRect();

        ImGui.endChild();

        drawList.pushClipRect(x + middleX, y, x + width, y + height);

        renderExportBar(drawList);
        renderSeparators(minorsPerMajor, x, middleX, minorSeparatorWidth, errorOffset, width, drawList, y, timestampHeight, middleY, minTicks, ticksPerMinor, showSubSeconds, majorSeparatorHeight, minorSeparatorHeight);

        // render markers
        {
            for (int tick = minTicks-10; tick <= minTicks + availableTicks + 10; tick++) {
                Map.Entry<Integer, ReplayMarker> markerEntry = metadata.replayMarkers.ceilingEntry(tick);
                if (markerEntry == null || markerEntry.getKey() > minTicks + availableTicks + 10) {
                    break;
                }

                int markerTick = markerEntry.getKey();
                ReplayMarker marker = markerEntry.getValue();

                float markerX = x + replayTickToTimelineX(markerTick);
                int colour = marker.colour();
                colour = ((colour >> 16) & 0xFF) | (colour & 0xFF00) | ((colour << 16) & 0xFF0000) | 0xFF000000; // change endianness
                drawList.addCircleFilled(markerX, y+middleY, ReplayUI.scaleUi(5), colour);

                if (!ImGui.isAnyMouseDown() && marker.description() != null && Math.abs(markerX - mouseX) <= 5 && Math.abs(y+middleY - mouseY) <= 5) {
                    ImGuiHelper.drawTooltip(marker.description());
                }

                tick = markerTick + 1;
            }
        }

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
            drawList.addLine(x + width - 2, y + timestampHeight, x + width - 2, y + height - zoomBarHeight, -1);
        }

        // Zoom Bar
        if (zoomBarExpanded) {
            drawList.addRectFilled(x + middleX +1, y + height - zoomBarHeight, x + width, y + height, 0xFF404040, zoomBarHeight);
        }
        if (zoomBarHovered || grabbedZoomBar) {
            drawList.addRectFilled(zoomBarMin + zoomBarHeight/2f, y + height - zoomBarHeight, zoomBarMax - zoomBarHeight/2f, y + height, -1, zoomBarHeight);

            // Left/right resize
            drawList.addCircleFilled(zoomBarMin + zoomBarHeight/2f, y + height - zoomBarHeight/2f, zoomBarHeight/2f, 0xffaaaa00);
            drawList.addCircleFilled(zoomBarMax - zoomBarHeight/2f, y + height - zoomBarHeight/2f, zoomBarHeight/2f, 0xffaaaa00);
        } else {
            drawList.addRectFilled(zoomBarMin, y + height - zoomBarHeight, zoomBarMax, y + height, -1, zoomBarHeight);
        }

        // Pause/play button
        int controlSize = ReplayUI.scaleUi(24);
        int controlsY = (int) y + middleY/2 - controlSize/2;

        float manualTickRate = replayServer.getDesiredTickRate(true);

        // Skip backwards
        int skipBackwardsX = (int) x + middleX/6 - controlSize/2;
        drawList.addTriangleFilled(skipBackwardsX + controlSize/3f, controlsY + controlSize/2f,
            skipBackwardsX + controlSize, controlsY,
            skipBackwardsX + controlSize, controlsY + controlSize, -1);
        drawList.addRectFilled(skipBackwardsX, controlsY,
            skipBackwardsX + controlSize/3f, controlsY+controlSize, -1);

        // Slow down
        int slowDownX = (int) x + middleX*2/6 - controlSize/2;
        int slowDownColor = manualTickRate < 20 ? 0xFF8080FF : -1;
        drawList.addTriangleFilled(slowDownX, controlsY + controlSize/2f,
            slowDownX + controlSize/2f, controlsY,
            slowDownX + controlSize/2f, controlsY+controlSize, slowDownColor);
        drawList.addTriangleFilled(slowDownX + controlSize/2f, controlsY + controlSize/2f,
            slowDownX + controlSize, controlsY,
            slowDownX + controlSize, controlsY + controlSize, slowDownColor);

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
        int fastForwardsColor = manualTickRate > 20 ? 0xFF80FF80 : -1;
        drawList.addTriangleFilled(fastForwardsX, controlsY,
            fastForwardsX + controlSize/2f, controlsY + controlSize/2f,
            fastForwardsX, controlsY+controlSize, fastForwardsColor);
        drawList.addTriangleFilled(fastForwardsX + controlSize/2f, controlsY,
            fastForwardsX + controlSize, controlsY + controlSize/2f,
            fastForwardsX + controlSize/2f, controlsY + controlSize, fastForwardsColor);

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
            renderKeyframeOptionsPopup(totalTicks);
            ImGui.endPopup();
        } else {
            editingKeyframeTrack = -1;
            editingKeyframeTick = -1;
        }

        boolean shouldProcessInput = !ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopup) && !ImGui.getIO().getWantTextInput();
        if (shouldProcessInput) {
            int scroll = (int) Math.signum(ImGui.getIO().getMouseWheel());
            if (scroll != 0 && mouseX > x + middleX && mouseX < x + width && mouseY > y && mouseY < y + height) {
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

            handleKeyPresses(replayServer, cursorTicks, totalTicks);

            boolean leftClicked = ImGui.isMouseClicked(ImGuiMouseButton.Left);
            boolean rightClicked = ImGui.isMouseClicked(ImGuiMouseButton.Right);
            if (mouseX < x + width - 2 && (leftClicked || rightClicked)) {
                handleClick(replayServer, totalTicks, contentY);
                if (leftClicked) {
                    draggingMouseButton = ImGuiMouseButton.Left;
                } else {
                    draggingMouseButton = ImGuiMouseButton.Right;
                }
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
            } else if (ImGui.isMouseDragging(draggingMouseButton)) {
                if (repositioningKeyframeTrack >= 0 && repositioningKeyframeTrack < editorScene.keyframeTracks.size()) {
                    float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();

                    float mouseDeltaY = mouseY - dragStartMouseY;
                    if (mouseDeltaY > lineHeight/2) {
                        if (repositioningKeyframeTrack < editorScene.keyframeTracks.size()-1) {
                            selectedKeyframesList.clear();
                            editingKeyframeTrack = -1;
                            editingKeyframeTick = -1;

                            dragStartMouseY += lineHeight;

                            upgradeToSceneWrite();

                            if (repositioningKeyframeTrack < editorScene.keyframeTracks.size()-1) {
                                var track = editorScene.keyframeTracks.remove(repositioningKeyframeTrack);
                                editorScene.keyframeTracks.get(repositioningKeyframeTrack).animatedOffsetInUi += lineHeight;
                                repositioningKeyframeTrack += 1;
                                editorScene.keyframeTracks.add(repositioningKeyframeTrack, track);
                            }
                        }
                    } else if (mouseDeltaY < -lineHeight/2) {
                        if (repositioningKeyframeTrack > 0) {
                            selectedKeyframesList.clear();
                            editingKeyframeTrack = -1;
                            editingKeyframeTick = -1;

                            dragStartMouseY -= lineHeight;

                            upgradeToSceneWrite();

                            if (repositioningKeyframeTrack > 0) {
                                var track = editorScene.keyframeTracks.remove(repositioningKeyframeTrack);
                                repositioningKeyframeTrack -= 1;
                                editorScene.keyframeTracks.get(repositioningKeyframeTrack).animatedOffsetInUi -= lineHeight;
                                editorScene.keyframeTracks.add(repositioningKeyframeTrack, track);
                            }
                        }
                    }
                }
                if (grabbedExportBarResizeLeft) {
                    ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);

                    int target = timelineXToReplayTick(mouseX - x);

                    if (ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                        int closestTick = findClosestKeyframeForSnap(target);
                        if (closestTick != -1) {
                            target = closestTick;
                        }
                    }

                    upgradeToSceneWrite();
                    editorScene.setExportTicks(target, -1, totalTicks);
                    editorState.markDirty();
                }
                if (grabbedExportBarResizeRight) {
                    ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);

                    int target = timelineXToReplayTick(mouseX - x);

                    if (ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                        int closestTick = findClosestKeyframeForSnap(target);
                        if (closestTick != -1) {
                            target = closestTick;
                        }
                    }

                    upgradeToSceneWrite();
                    editorScene.setExportTicks(-1, target, totalTicks);
                    editorState.markDirty();
                }
                if (zoomBarWidth > 1f && grabbedZoomBar) {
                    float dx = mouseX - dragStartMouseX;
                    float factor = dx / zoomBarWidth;

                    if (grabbedZoomBarResizeLeft) {
                        ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
                        editorState.zoomMin = Math.max(0, Math.min(editorState.zoomMax - 0.01f, zoomMinBeforeDrag + factor));
                    } else if (grabbedZoomBarResizeRight) {
                        ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
                        editorState.zoomMax = Math.max(editorState.zoomMin + 0.01f, Math.min(1, zoomMaxBeforeDrag + factor));
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
                    int desiredTick = timelineXToReplayTick(mouseX - x);

                    if (ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                        int closestTick = findClosestKeyframeForSnap(desiredTick);
                        if (closestTick != -1) {
                            desiredTick = closestTick;
                        }
                    }

                    if (desiredTick > currentReplayTick) {
                        replayServer.goToReplayTick(desiredTick);
                    }

                    replayServer.replayPaused = true;
                }
            } else if (zoomBarWidth > 1f && mouseY > y + middleY && mouseY < y + height && mouseX > x + middleX && mouseX < x + width && ImGui.isMouseDown(ImGuiMouseButton.Middle)) {
                grabbedZoomBar = true;
                draggingMouseButton = ImGuiMouseButton.Middle;
                zoomMinBeforeDrag = editorState.zoomMin;
                zoomMaxBeforeDrag = editorState.zoomMax;
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
            } else if (!ImGui.isAnyMouseDown()) {
                releaseGrabbed(replayServer, totalTicks, contentY);
            }
        } else {
            releaseGrabbed(replayServer, totalTicks, contentY);
        }
    }

    private static void upgradeToSceneWrite() {
        if (!editorSceneStampIsWrite) {
            editorState.release(editorSceneStamp);
            editorSceneStamp = editorState.acquireWrite();
            editorSceneStampIsWrite = true;
        }
    }

    private static int findClosestKeyframeForSnap(int tick) {
        int closestTick = -1;
        for (KeyframeTrack track : editorScene.keyframeTracks) {
            Integer floor = track.keyframesByTick.floorKey(tick);
            Integer ceil = track.keyframesByTick.ceilingKey(tick);

            float floorX = floor == null ? Float.NaN : x + replayTickToTimelineX(floor);
            float ceilX = ceil == null ? Float.NaN : x + replayTickToTimelineX(ceil);

            Integer closest = Utils.chooseClosest(mouseX, floorX, floor, ceilX, ceil, keyframeSize);
            if (closest != null) {
                if (closestTick == -1) {
                    closestTick = closest;
                } else if (Math.abs(closest - tick) < Math.abs(closestTick - tick)) {
                    closestTick = closest;
                }
            }
        }
        return closestTick;
    }

    private static void handleKeyPresses(ReplayServer replayServer, int cursorTicks, int totalTicks) {
        boolean pressedIn = ImGui.isKeyPressed(GLFW.GLFW_KEY_I, false);
        boolean pressedOut = ImGui.isKeyPressed(GLFW.GLFW_KEY_O, false);

        boolean ctrlPressed = Minecraft.ON_OSX ? ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_SUPER) : ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL);
        boolean pressedCopy = ctrlPressed && ImGui.isKeyPressed(GLFW.GLFW_KEY_C, false);
        boolean pressedPaste = ctrlPressed && ImGui.isKeyPressed(GLFW.GLFW_KEY_V, false);

        boolean pressedDelete = ImGui.isKeyPressed(GLFW.GLFW_KEY_DELETE, false) || ImGui.isKeyPressed(GLFW.GLFW_KEY_BACKSPACE, false);

        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_P, false)) {
            togglePaused(replayServer);
        }
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_LEFT, false)) {
            pendingStepBackwardsTicks += ReplayUI.isCtrlOrCmdDown() ? 5 : 1;
        } else if (pendingStepBackwardsTicks > 0 && !ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT)) {
            replayServer.goToReplayTick(Math.max(0, replayServer.getReplayTick() - pendingStepBackwardsTicks));
            replayServer.forceApplyKeyframes.set(true);
            pendingStepBackwardsTicks = 0;
        }
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_RIGHT, false)) {
            replayServer.goToReplayTick(Math.min(totalTicks, cursorTicks + (ReplayUI.isCtrlOrCmdDown() ? 5 : 1)));
            replayServer.forceApplyKeyframes.set(true);
        }
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_UP, false)) {
            int nextKeyframeTick;
            if (editorScene.exportStartTicks >= 0 && editorScene.exportStartTicks > cursorTicks) {
                nextKeyframeTick = editorScene.exportStartTicks;
            } else if (editorScene.exportEndTicks >= 0 && editorScene.exportEndTicks > cursorTicks) {
                nextKeyframeTick = editorScene.exportEndTicks;
            } else {
                nextKeyframeTick = totalTicks;
            }

            FlashbackMeta meta = replayServer.getMetadata();
            Integer nextMarker = meta.replayMarkers.ceilingKey(cursorTicks + 1);
            if (nextMarker != null && nextMarker < nextKeyframeTick) {
                nextKeyframeTick = nextMarker;
            }

            for (KeyframeTrack track : editorScene.keyframeTracks) {
                Integer ceilingKey = track.keyframesByTick.ceilingKey(cursorTicks+1);
                if (ceilingKey != null && ceilingKey < nextKeyframeTick) {
                    nextKeyframeTick = ceilingKey;
                }
            }
            replayServer.goToReplayTick(nextKeyframeTick);
            replayServer.forceApplyKeyframes.set(true);
        }
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_DOWN, false)) {
            int previousKeyframeTick;
            if (editorScene.exportEndTicks >= 0 && editorScene.exportEndTicks < cursorTicks) {
                previousKeyframeTick = editorScene.exportEndTicks;
            } else if (editorScene.exportStartTicks >= 0 && editorScene.exportStartTicks < cursorTicks) {
                previousKeyframeTick = editorScene.exportStartTicks;
            } else {
                previousKeyframeTick = 0;
            }

            FlashbackMeta meta = replayServer.getMetadata();
            Integer lastMarker = meta.replayMarkers.floorKey(cursorTicks - 1);
            if (lastMarker != null && lastMarker > previousKeyframeTick) {
                previousKeyframeTick = lastMarker;
            }

            for (KeyframeTrack track : editorScene.keyframeTracks) {
                Integer floorKey = track.keyframesByTick.floorKey(cursorTicks-1);
                if (floorKey != null && floorKey > previousKeyframeTick) {
                    previousKeyframeTick = floorKey;
                }
            }
            replayServer.goToReplayTick(previousKeyframeTick);
            replayServer.forceApplyKeyframes.set(true);
        }
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_Z, false) && (ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL))) {
            upgradeToSceneWrite();
            editorScene.undo(ReplayUI::setInfoOverlayShort);
            editorState.markDirty();
        }
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_Y, false) && (ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL))) {
            upgradeToSceneWrite();
            editorScene.redo(ReplayUI::setInfoOverlayShort);
            editorState.markDirty();
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
            upgradeToSceneWrite();
            editorScene.setExportTicks(start, end, totalTicks);
            editorState.markDirty();
        }

        if (pressedDelete && !selectedKeyframesList.isEmpty()) {
            removeAllSelectedKeyframes();
        }

        if (pressedCopy && !selectedKeyframesList.isEmpty()) {
            performCopy(totalTicks, false, false, false);
        }

        if (pressedPaste) {
            try {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard().trim();
                if (clipboard.startsWith("{") && clipboard.endsWith("}")) {
                    CopiedKeyframes copiedKeyframes = FlashbackGson.COMPRESSED.fromJson(clipboard, CopiedKeyframes.class);

                    KeyframeRelativeOffsets offsets = new KeyframeRelativeOffsets();
                    LocalPlayer p = Minecraft.getInstance().player;
                    if (p != null) {
                        if (copiedKeyframes.relativePosition != null) {
                            offsets.oldOrigin = copiedKeyframes.relativePosition;
                            offsets.newOrigin = new Vector3d(p.getX(), p.getY(), p.getZ());
                        }
                        if (copiedKeyframes.relativeYaw != null) {
                            offsets.oldYaw = copiedKeyframes.relativeYaw;
                            offsets.newYaw = p.getViewYRot(1.0f);
                        }
                        if (copiedKeyframes.relativePitch != null) {
                            offsets.oldPitch = copiedKeyframes.relativePitch;
                            offsets.newPitch = p.getViewXRot(1.0f);
                        }
                    }

                    upgradeToSceneWrite();

                    int count = 0;
                    for (SavedTrack savedTrack : copiedKeyframes.savedTracks) {
                        count += savedTrack.applyToScene(editorScene, cursorTicks, totalTicks, offsets);
                    }

                    if (count > 0) {
                        ReplayUI.setInfoOverlay("Pasted " + count + " keyframe(s) from clipboard");
                        editorState.markDirty();
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void performCopy(int totalTicks, boolean relativePosition, boolean relativeYaw, boolean relativePitch) {
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
            KeyframeTrack keyframeTrack = editorScene.keyframeTracks.get(selectedKeyframes.trackIndex());

            TreeMap<Integer, Keyframe> keyframes = new TreeMap<>();
            for (int tick : selectedKeyframes.keyframeTicks()) {
                Keyframe keyframe = keyframeTrack.keyframesByTick.get(tick);
                keyframes.put(tick - minTick, keyframe);
            }

            tracks.add(new SavedTrack(selectedKeyframes.type(), selectedKeyframes.trackIndex(), !keyframeTrack.enabled, keyframes));
        }

        LocalPlayer p = Minecraft.getInstance().player;
        CopiedKeyframes copiedKeyframes = new CopiedKeyframes();
        if (p != null) {
            copiedKeyframes.relativePosition = relativePosition ? new Vector3d(p.getX(), p.getY(), p.getZ()) : null;
            copiedKeyframes.relativeYaw = relativeYaw ? p.getViewYRot(1.0f) : null;
            copiedKeyframes.relativePitch = relativePitch ? p.getViewXRot(1.0f) : null;
        }
        copiedKeyframes.savedTracks = tracks;

        String serialized = FlashbackGson.COMPRESSED.toJson(copiedKeyframes);
        Minecraft.getInstance().keyboardHandler.setClipboard(serialized);

        ReplayUI.setInfoOverlay("Copied " + keyframeCount + " keyframe(s) to clipboard");
    }

    private static void removeAllSelectedKeyframes() {
        upgradeToSceneWrite();

        List<EditorSceneHistoryAction> undo = new ArrayList<>();
        List<EditorSceneHistoryAction> redo = new ArrayList<>();

        for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
            KeyframeTrack track = editorScene.keyframeTracks.get(selectedKeyframes.trackIndex());
            for (int tick : selectedKeyframes.keyframeTicks()) {
                Keyframe keyframe = track.keyframesByTick.get(tick);

                undo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick, keyframe.copy()));
                redo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick));
            }
        }

        selectedKeyframesList.clear();
        editingKeyframeTrack = -1;
        editingKeyframeTick = -1;
        editorScene.push(new EditorSceneHistoryEntry(undo, redo, "Deleted " + undo.size() + " keyframe(s)"));
        editorState.markDirty();
    }

    private static void handleClick(ReplayServer replayServer, int totalTicks, float contentY) {
        releaseGrabbed(replayServer, totalTicks, contentY);
        List<SelectedKeyframes> oldSelectedKeyframesList = new ArrayList<>(selectedKeyframesList);
        selectedKeyframesList.clear();

        boolean leftClicked = ImGui.isMouseClicked(ImGuiMouseButton.Left);
        boolean rightClicked = ImGui.isMouseClicked(ImGuiMouseButton.Right);

        if (hoveredControls) {
            // Skip backwards
            if (hoveredSkipBackwards) {
                FlashbackMeta meta = replayServer.getMetadata();
                Integer lastMarker = meta.replayMarkers.floorKey(cursorTicks - 1);

                if (lastMarker != null) {
                    replayServer.goToReplayTick(lastMarker);
                } else {
                    replayServer.goToReplayTick(0);
                }
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
                togglePaused(replayServer);
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
                FlashbackMeta meta = replayServer.getMetadata();
                Integer nextMarker = meta.replayMarkers.ceilingKey(cursorTicks + 1);

                if (nextMarker != null) {
                    replayServer.goToReplayTick(nextMarker);
                } else {
                    replayServer.goToReplayTick(replayServer.getTotalReplayTicks());
                }
                return;
            }
        }

        // Timeline
        if (mouseY > y && mouseY < y + middleY && mouseX > x + middleX && mouseX < x + width) {
            if (editorScene.exportStartTicks >= 0 && editorScene.exportEndTicks >= 0 && mouseY > y + timestampHeight) {
                int exportStartX = replayTickToTimelineX(editorScene.exportStartTicks);
                int exportEndX = replayTickToTimelineX(editorScene.exportEndTicks);

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
            if (mouseX <= zoomBarMin + zoomBarHeight) {
                grabbedZoomBarResizeLeft = leftClicked;
            } else if (mouseX >= zoomBarMax - zoomBarHeight) {
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

            int trackIndex = (int) Math.max(0, Math.floor((mouseY - (contentY + 2))/lineHeight));

            if (trackIndex >= 0 && trackIndex < editorScene.keyframeTracks.size()) {
                KeyframeTrack keyframeTrack = editorScene.keyframeTracks.get(trackIndex);

                int tick = timelineXToReplayTick(mouseX - x);

                Map.Entry<Integer, Keyframe> floor = keyframeTrack.keyframesByTick.floorEntry(tick);
                Map.Entry<Integer, Keyframe> ceil = keyframeTrack.keyframesByTick.ceilingEntry(tick);

                float floorX = floor == null ? Float.NaN : x + replayTickToTimelineX(floor.getKey());
                float ceilX = ceil == null ? Float.NaN : x + replayTickToTimelineX(ceil.getKey());

                Map.Entry<Integer, Keyframe> closest = Utils.chooseClosest(mouseX, floorX, floor, ceilX, ceil, keyframeSize);
                if (closest != null) {
                    boolean reuseOld = false;
                    for (SelectedKeyframes selectedKeyframes : oldSelectedKeyframesList) {
                        if (selectedKeyframes.trackIndex() == trackIndex) {
                            reuseOld = selectedKeyframes.keyframeTicks().contains((int) closest.getKey());
                            break;
                        }
                    }

                    boolean deselectedClicked = false;

                    if (ReplayUI.isCtrlOrCmdDown()) {
                        selectedKeyframesList.addAll(oldSelectedKeyframesList);

                        boolean handled = false;
                        for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                            if (selectedKeyframes.type() == keyframeTrack.keyframeType && selectedKeyframes.trackIndex() == trackIndex) {
                                if (selectedKeyframes.keyframeTicks().remove((int) closest.getKey())) {
                                    // If we ctrl-click a keyframe that is already selected, deselect it
                                    deselectedClicked = true;
                                } else {
                                    // Otherwise, if it isn't selected, select it
                                    selectedKeyframes.keyframeTicks().add((int) closest.getKey());
                                }
                                handled = true;
                                break;
                            }
                        }
                        if (!handled) {
                            IntSet intSet = new IntOpenHashSet();
                            intSet.add((int) closest.getKey());
                            selectedKeyframesList.add(new SelectedKeyframes(keyframeTrack.keyframeType, trackIndex, intSet));
                        }
                    } else if (reuseOld) {
                        selectedKeyframesList.addAll(oldSelectedKeyframesList);
                    } else {
                        IntSet intSet = new IntOpenHashSet();
                        intSet.add((int) closest.getKey());
                        selectedKeyframesList.add(new SelectedKeyframes(keyframeTrack.keyframeType, trackIndex, intSet));
                    }

                    selectedKeyframesList.removeIf(k -> !k.checkValid(editorScene));

                    if (!deselectedClicked) {
                        if (leftClicked) {
                            if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                                MinecraftKeyframeHandler minecraftKeyframeHandler = new MinecraftKeyframeHandler(Minecraft.getInstance());
                                if (closest.getValue().keyframeType().supportsHandler(minecraftKeyframeHandler)) {
                                    closest.getValue().createChange().apply(minecraftKeyframeHandler);
                                }
                            } else {
                                grabbedKeyframe = true;
                                grabbedKeyframeMouseX = mouseX;
                                grabbedKeyframeTick = closest.getKey();
                                grabbedKeyframeTrack = trackIndex;
                                enableKeyframeMovement = false;
                            }
                        } else if (rightClicked) {
                            ImGui.openPopup("##KeyframePopup");
                            editingKeyframeTrack = trackIndex;
                            editingKeyframeTick = closest.getKey();
                            grabbedKeyframeTrack = trackIndex;
                        }
                    }

                    return;
                } else if (rightClicked && keyframeTrack.keyframeType.canBeCreatedNormally()) {
                    createKeyframeAtTick = tick;
                    openCreateKeyframeAtTickTrack = trackIndex;
                }
            }

            dragSelectOrigin = new Vector2f(mouseX, mouseY);
        }
    }

    private static void togglePaused(ReplayServer replayServer) {
        if (replayServer.getReplayTick() >= replayServer.getTotalReplayTicks()) {
            replayServer.jumpToTick = 0;
        }
        replayServer.replayPaused = !replayServer.replayPaused;
        if (!replayServer.replayPaused) {
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null && screen.isPauseScreen()) {
                Minecraft.getInstance().setScreen(null);
            }
        }
    }

    private static void renderExportBar(ImDrawList drawList) {
        if (editorScene.exportStartTicks >= 0 && editorScene.exportEndTicks >= 0) {
            int exportStartX = replayTickToTimelineX(editorScene.exportStartTicks);
            int exportEndX = replayTickToTimelineX(editorScene.exportEndTicks);
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

    private static void renderKeyframeOptionsPopup(int totalTicks) {
        if (editingKeyframeTrack < 0 || editingKeyframeTick < 0) {
            return;
        }

        KeyframeTrack keyframeTrack = editorScene.keyframeTracks.get(editingKeyframeTrack);
        Keyframe editingKeyframe = keyframeTrack.keyframesByTick.get(editingKeyframeTick);

        if (editingKeyframe == null || selectedKeyframesList.isEmpty()) {
            editingKeyframeTrack = -1;
            ImGui.closeCurrentPopup();
            return;
        }

        editingKeyframe.renderEditKeyframe(updateFunction -> {
            upgradeToSceneWrite();

            List<EditorSceneHistoryAction> undo = new ArrayList<>();
            List<EditorSceneHistoryAction> redo = new ArrayList<>();

            int modified = 0;

            for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                KeyframeTrack track = editorScene.keyframeTracks.get(selectedKeyframes.trackIndex());
                for (int tick : selectedKeyframes.keyframeTicks()) {
                    Keyframe keyframe = track.keyframesByTick.get(tick);
                    if (keyframe.getClass() == editingKeyframe.getClass()) {
                        modified += 1;

                        undo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick, keyframe.copy()));
                        updateFunction.accept(keyframe);
                        redo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick, keyframe.copy()));
                    }
                }
            }

            if (modified > 0) {
                editorScene.push(new EditorSceneHistoryEntry(undo, redo, "Modified " + modified + " keyframe(s)"));
                editorState.markDirty();
            }
        });

        if (editingKeyframe.keyframeType().allowChangingInterpolationType()) {
            int[] type = new int[]{editingKeyframe.interpolationType().ordinal()};
            ImGui.setNextItemWidth(160);
            if (ImGuiHelper.combo("Type", type, InterpolationType.NAMES)) {
                upgradeToSceneWrite();

                InterpolationType interpolationType = InterpolationType.INTERPOLATION_TYPES[type[0]];

                List<EditorSceneHistoryAction> undo = new ArrayList<>();
                List<EditorSceneHistoryAction> redo = new ArrayList<>();

                for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                    KeyframeTrack track = editorScene.keyframeTracks.get(selectedKeyframes.trackIndex());
                    for (int tick : selectedKeyframes.keyframeTicks()) {
                        Keyframe keyframe = track.keyframesByTick.get(tick);

                        if (keyframe.interpolationType() != interpolationType) {
                            Keyframe changed = keyframe.copy();
                            changed.interpolationType(interpolationType);

                            undo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick, keyframe.copy()));
                            redo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), tick, changed));
                        }
                    }
                }

                editorScene.push(new EditorSceneHistoryEntry(undo, redo, "Changed interpolation type to " + interpolationType));
                editorState.markDirty();
            }
        }
        if (editingKeyframe.keyframeType().allowChangingTimelineTick()) {
            int[] intWrapper = new int[]{editingKeyframeTick};
            ImGui.setNextItemWidth(160);
            ImGuiHelper.inputInt("Tick", intWrapper);
            int newEditingKeyframeTick = intWrapper[0];

            if (ImGui.isItemDeactivatedAfterEdit() && newEditingKeyframeTick != editingKeyframeTick) {
                upgradeToSceneWrite();

                for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                    KeyframeTrack track = editorScene.keyframeTracks.get(selectedKeyframes.trackIndex());

                    Keyframe possibleKeyframe = track.keyframesByTick.get(editingKeyframeTick);
                    if (possibleKeyframe == editingKeyframe) {
                        List<EditorSceneHistoryAction> undo = List.of(
                            new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), newEditingKeyframeTick),
                            new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), editingKeyframeTick, editingKeyframe.copy())
                        );
                        List<EditorSceneHistoryAction> redo = List.of(
                            new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), editingKeyframeTick),
                            new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, selectedKeyframes.trackIndex(), newEditingKeyframeTick, editingKeyframe.copy())
                        );

                        editorScene.push(new EditorSceneHistoryEntry(undo, redo, "Moved 1 keyframe(s)"));
                        editorState.markDirty();
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
            removeAllSelectedKeyframes();
        }

        if (!multiple && editingKeyframe instanceof CameraKeyframe cameraKeyframe) {
            ImGui.sameLine();
            if (ImGui.button("Apply")) {
                cameraKeyframe.createChange().apply(new MinecraftKeyframeHandler(Minecraft.getInstance()));
            }
        }

        if (editingKeyframe instanceof CameraKeyframe || editingKeyframe instanceof CameraOrbitKeyframe) {
            if (ImGui.button("Copy Relative...")) {
                ImGui.openPopup("##CopyOptions");
            }
            if (ImGui.beginPopup("##CopyOptions")) {
                if (ImGui.checkbox("Copy Relative To Position", copyRelativeToPosition)) {
                    copyRelativeToPosition = !copyRelativeToPosition;
                }
                if (ImGui.checkbox("Copy Relative To Yaw", copyRelativeToYaw)) {
                    copyRelativeToYaw = !copyRelativeToYaw;
                }
                if (ImGui.checkbox("Copy Relative To Pitch", copyRelativeToPitch)) {
                    copyRelativeToPitch = !copyRelativeToPitch;
                }
                if (ImGui.button("Copy Relative")) {
                    performCopy(totalTicks, copyRelativeToPosition, copyRelativeToYaw, copyRelativeToPitch);
                    ImGui.closeCurrentPopup();
                }

                ImGui.endPopup();
            }
        }
    }

    private static void releaseGrabbed(ReplayServer replayServer, int totalTicks, float contentY) {
        grabbedZoomBar = false;
        grabbedZoomBarResizeLeft = false;
        grabbedZoomBarResizeRight = false;
        grabbedExportBarResizeLeft = false;
        grabbedExportBarResizeRight = false;

        if (!ImGui.isAnyMouseDown()) {
            trackDisabledButtonDrag = false;
            repositioningKeyframeTrack = -1;
        }

        if (dragSelectOrigin != null) {
            float dragMinX = Math.min(dragSelectOrigin.x, mouseX);
            float dragMinY = Math.min(dragSelectOrigin.y, mouseY);
            float dragMaxX = Math.max(dragSelectOrigin.x, mouseX);
            float dragMaxY = Math.max(dragSelectOrigin.y, mouseY);

            float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
            int minTrackIndex = (int) Math.floor((dragMinY - (contentY + 2))/lineHeight);
            int maxTrackIndex = (int) Math.floor((dragMaxY - (contentY + 2))/lineHeight);
            minTrackIndex = Math.max(0, minTrackIndex);
            maxTrackIndex = Math.min(editorScene.keyframeTracks.size()-1, maxTrackIndex);

            for (int trackIndex = minTrackIndex; trackIndex <= maxTrackIndex; trackIndex++) {
                KeyframeTrack keyframeTrack = editorScene.keyframeTracks.get(trackIndex);

                int minTick = timelineXToReplayTick(dragMinX - keyframeSize);
                int maxTick = timelineXToReplayTick(dragMaxX + keyframeSize);

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
            int desiredTick = timelineXToReplayTick(mouseX - x);

            if (ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                int closestTick = findClosestKeyframeForSnap(desiredTick);
                if (closestTick != -1) {
                    desiredTick = closestTick;
                }
            }

            replayServer.goToReplayTick(desiredTick);
            replayServer.replayPaused = true;
            grabbedPlayback = false;
        }

        if (grabbedKeyframe) {
            GrabMovementInfo grabMovementInfo = calculateGrabMovementInfo(totalTicks);

            if (grabMovementInfo.grabbedScalePivotTick >= 0 || grabMovementInfo.grabbedDelta != 0) {
                upgradeToSceneWrite();

                List<EditorSceneHistoryAction> undo = new ArrayList<>();
                List<EditorSceneHistoryAction> redo = new ArrayList<>();
                int movedKeyframes = 0;

                for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                    int trackIndex = selectedKeyframes.trackIndex();
                    KeyframeTrack keyframeTrack = editorScene.keyframeTracks.get(trackIndex);

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
                            redo.add(new EditorSceneHistoryAction.RemoveKeyframe(keyframeTrack.keyframeType, trackIndex, removeTick));
                        }
                    }
                    for (Int2ObjectMap.Entry<Keyframe> entry : addToTick.int2ObjectEntrySet()) {
                        redo.add(new EditorSceneHistoryAction.SetKeyframe(keyframeTrack.keyframeType, trackIndex, entry.getIntKey(), entry.getValue()));

                        if (!removeFromTick.containsKey(entry.getIntKey())) {
                            Keyframe existing = keyframeTrack.keyframesByTick.get(entry.getIntKey());
                            if (existing != null) {
                                undo.add(new EditorSceneHistoryAction.SetKeyframe(keyframeTrack.keyframeType, trackIndex, entry.getIntKey(), existing.copy()));
                            } else {
                                undo.add(new EditorSceneHistoryAction.RemoveKeyframe(keyframeTrack.keyframeType, trackIndex, entry.getIntKey()));
                            }
                        }
                    }
                    for (Int2ObjectMap.Entry<Keyframe> entry : removeFromTick.int2ObjectEntrySet()) {
                        undo.add(new EditorSceneHistoryAction.SetKeyframe(keyframeTrack.keyframeType, trackIndex, entry.getIntKey(), entry.getValue()));
                    }
                }

                editorScene.push(new EditorSceneHistoryEntry(undo, redo, "Moved " + movedKeyframes + " keyframe(s)"));
                editorState.markDirty();
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

    private static GrabMovementInfo calculateGrabMovementInfo(int totalTicks) {
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
                for (int i = 0; i < editorScene.keyframeTracks.size(); i++) {
                    if (i == grabbedKeyframeTrack) {
                        continue;
                    }

                    KeyframeTrack track = editorScene.keyframeTracks.get(i);

                    Integer floor = track.keyframesByTick.floorKey(grabbedKeyframeTick);
                    Integer ceil = track.keyframesByTick.ceilingKey(grabbedKeyframeTick);

                    float floorX = floor == null ? Float.NaN : x + replayTickToTimelineX(floor);
                    float ceilX = ceil == null ? Float.NaN : x + replayTickToTimelineX(ceil);

                    Integer closest = Utils.chooseClosest(mouseX, floorX, floor, ceilX, ceil, keyframeSize);
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

            if (!isShiftDown && editorScene.keyframeTracks.size() > 1) {
                tooltip += "\nHold SHIFT to snap";
            }

            ImGuiHelper.drawTooltip(tooltip);
        }

        return new GrabMovementInfo(grabbedDelta, grabbedScalePivotTick, grabbedScaleFactor);
    }

    private static void renderKeyframes(float x, float y, float mouseX, int minTicks, float availableTicks, int totalTicks) {
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();

        ImDrawList drawList = ImGui.getWindowDrawList();

        GrabMovementInfo grabMovementInfo = null;
        if (grabbedKeyframe) {
            grabMovementInfo = calculateGrabMovementInfo(totalTicks);
        }

        for (int trackIndex = 0; trackIndex < editorScene.keyframeTracks.size(); trackIndex++) {
            KeyframeTrack keyframeTrack = editorScene.keyframeTracks.get(trackIndex);

            TreeMap<Integer, Keyframe> keyframeTimes = keyframeTrack.keyframesByTick;

            SelectedKeyframes selectedKeyframesForTrack = null;
            for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                if (selectedKeyframes.trackIndex() == trackIndex) {
                    selectedKeyframesForTrack = selectedKeyframes;
                    break;
                }
            }

            for (int tick = minTicks - 10; tick <= minTicks + availableTicks + 10; tick++) {
                var entry = keyframeTimes.ceilingEntry(tick);
                if (entry == null || entry.getKey() > minTicks + availableTicks + 10) {
                    break;
                }
                tick = entry.getKey();

                Keyframe keyframe = entry.getValue();

                float midY = y + 2 + (trackIndex+0.5f) * lineHeight;

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

                    drawKeyframe(drawList, keyframe.interpolationType(), midX, midY, keyframeTrack.enabled ? 0xFF0000FF : 0x800000FF);
                } else {
                    int keyframeX = replayTickToTimelineX(tick);

                    float midX = x + keyframeX;

                    int colour = -1;
                    if (keyframeTrack.keyframeType == TimelapseKeyframeType.INSTANCE) {
                        if (keyframeTrack.keyframesByTick.size() == 1) {
                            colour = 0xFF155FFF;

                            if (Math.abs(mouseX - midX) < keyframeSize && Math.abs(mouseY - midY) < keyframeSize) {
                                ImGuiHelper.drawTooltip("Timelapse requires two keyframes");
                            }
                        } else {
                            var floorEntry = keyframeTrack.keyframesByTick.floorEntry(tick - 1);
                            if (floorEntry != null && floorEntry.getValue() instanceof TimelapseKeyframe timelapseKeyframe) {
                                if (timelapseKeyframe.ticks >= ((TimelapseKeyframe) keyframe).ticks) {
                                    colour = 0xFF155FFF;

                                    if (Math.abs(mouseX - midX) < keyframeSize && Math.abs(mouseY - midY) < keyframeSize) {
                                        ImGuiHelper.drawTooltip("This keyframe's Time must be greater than the Time on the left.\nThe left keyframe must be set to the base time, e.g. 0s and the right keyframe must be set to the duration of the timelapse e.g. 10s");
                                    }
                                }
                            }
                        }
                    }
                    if (!keyframeTrack.enabled) {
                        colour &= 0xFFFFFF;
                        colour |= 0x80000000;
                    }

                    drawKeyframe(drawList, keyframe.interpolationType(), midX, midY, colour);
                }

                if ((selectedKeyframesForTrack == null || grabMovementInfo == null) && keyframeTrack.keyframeType == TimelapseKeyframeType.INSTANCE) {
                    var floorEntry = keyframeTrack.keyframesByTick.floorEntry(tick - 1);
                    if (floorEntry != null) {
                        TimelapseKeyframe left = (TimelapseKeyframe) floorEntry.getValue();
                        TimelapseKeyframe right = (TimelapseKeyframe) keyframe;

                        int leftX = replayTickToTimelineX(floorEntry.getKey());
                        int rightX = replayTickToTimelineX(tick);

                        int tickDelta = right.ticks - left.ticks;
                        String message;
                        int textColour = -1;
                        if (tickDelta <= 0) {
                            message = "INVALID";
                            textColour = 0xFF155FFF;
                        } else {
                            message = Utils.timeToString(tickDelta);
                        }

                        float textY = midY - lineHeight*0.3f;
                        float midX = (leftX + rightX)/2f;

                        float textWidth = ImGuiHelper.calcTextWidth("Duration: " + message);
                        if (textWidth <= rightX - leftX) {
                            ImGui.getWindowDrawList().addText(x + midX - textWidth/2, textY,
                                textColour, "Duration: " + message);
                        } else {
                            textWidth = ImGuiHelper.calcTextWidth(message);
                            if (textWidth <= rightX - leftX) {
                                ImGui.getWindowDrawList().addText(x + midX - textWidth/2, textY,
                                    textColour, message);
                            }
                        }

                        float startLine1 = x + leftX + keyframeSize;
                        float endLine1 = x + midX - textWidth/2f - 5;
                        float startLine2 = x + midX + textWidth/2f + 5;
                        float endLine2 = x + rightX - keyframeSize;
                        if (startLine1 < endLine1) {
                            ImGui.getWindowDrawList().addLine(startLine1, midY, endLine1, midY, 0x80FFFFFF);
                        }
                        if (startLine2 < endLine2) {
                            ImGui.getWindowDrawList().addLine(startLine2, midY, endLine2, midY, 0x80FFFFFF);
                        }
                    }
                }
            }
        }
    }

    private static void drawKeyframe(ImDrawList drawList, InterpolationType interpolationType, float x, float y, int colour) {
        int easeSize = keyframeSize / 5;
        switch (interpolationType) {
            case SMOOTH -> {
                drawList.addCircleFilled(x, y, keyframeSize, colour);
            }
            case LINEAR -> {
                drawList.addTriangleFilled(x - keyframeSize, y, x, y - keyframeSize, x, y + keyframeSize, colour);
                drawList.addTriangleFilled(x + keyframeSize, y, x, y + keyframeSize, x, y - keyframeSize, colour);
            }
            case EASE_IN -> {
                // Left inverted triangle
                drawList.addTriangleFilled(x - keyframeSize, y - keyframeSize, x - easeSize, y - keyframeSize, x - easeSize, y, colour);
                drawList.addTriangleFilled(x - keyframeSize, y + keyframeSize, x - easeSize, y, x - easeSize, y + keyframeSize, colour);
                // Right triangle
                drawList.addTriangleFilled(x + keyframeSize, y, x + easeSize, y + keyframeSize, x + easeSize, y - keyframeSize, colour);
                // Center
                drawList.addRectFilled(x - easeSize, y - keyframeSize, x + easeSize, y + keyframeSize, colour);
            }
            case EASE_OUT -> {
                // Left triangle
                drawList.addTriangleFilled(x - keyframeSize, y, x - easeSize, y - keyframeSize, x - easeSize, y + keyframeSize, colour);
                // Right inverted triangle
                drawList.addTriangleFilled(x + keyframeSize, y - keyframeSize, x + easeSize, y, x + easeSize, y - keyframeSize, colour);
                drawList.addTriangleFilled(x + keyframeSize, y + keyframeSize, x + easeSize, y + keyframeSize, x + easeSize, y, colour);
                // Center
                drawList.addRectFilled(x - easeSize, y - keyframeSize, x + easeSize, y + keyframeSize, colour);
            }
            case EASE_IN_OUT -> {
                // Left inverted triangle
                drawList.addTriangleFilled(x - keyframeSize, y - keyframeSize, x - easeSize, y - keyframeSize, x - easeSize, y, colour);
                drawList.addTriangleFilled(x - keyframeSize, y + keyframeSize, x - easeSize, y, x - easeSize, y + keyframeSize, colour);
                // Right inverted triangle
                drawList.addTriangleFilled(x + keyframeSize, y - keyframeSize, x + easeSize, y, x + easeSize, y - keyframeSize, colour);
                drawList.addTriangleFilled(x + keyframeSize, y + keyframeSize, x + easeSize, y + keyframeSize, x + easeSize, y, colour);
                // Center
                drawList.addRectFilled(x - easeSize, y - keyframeSize, x + easeSize, y + keyframeSize, colour);
            }
            case HOLD -> {
                drawList.addRectFilled(x - keyframeSize, y - keyframeSize, x + keyframeSize, y + keyframeSize, colour);
            }
            case HERMITE -> {
                drawList.addTriangleFilled(x, y - keyframeSize, x + keyframeSize, y + keyframeSize,
                        x - keyframeSize, y + keyframeSize, colour);
            }
        }
    }

    private static void renderKeyframeElements(float x, float y, int cursorTicks, int middleX) {
        ImGui.setCursorScreenPos(x + 8, y + 6);
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();

        int keyframeTrackToDelete = -1;
        int keyframeTrackToClear = -1;

        ImDrawList drawList = ImGui.getWindowDrawList();

        float buttonSize = ImGui.getTextLineHeight();
        float spacingX = ImGui.getStyle().getItemSpacingX();

        boolean hasOpenPopup = false;

        double animationMultiplier = Math.pow(0.9D, renderDeltaNanos / 10_000_000D);

        for (int trackIndex = 0; trackIndex < editorScene.keyframeTracks.size(); trackIndex++) {
            KeyframeTrack keyframeTrack = editorScene.keyframeTracks.get(trackIndex);
            KeyframeType<?> keyframeType = keyframeTrack.keyframeType;

            ImGui.pushID(trackIndex);

            if (trackIndex == openCreateKeyframeAtTickTrack) {
                ImGui.openPopup("##CreateKeyframeAtTickPopup");
                openCreateKeyframeAtTickTrack = -1;
            }

            if (ImGui.beginPopup("##CreateKeyframeAtTickPopup")) {
                if (ImGui.menuItem("Create Keyframe at " + createKeyframeAtTick)) {
                    ImGui.closeCurrentPopup();
                    ImGui.endPopup();

                    createNewKeyframe(trackIndex, createKeyframeAtTick, keyframeType, keyframeTrack);
                } else {
                    ImGui.endPopup();
                }
            }

            String icon = keyframeType.icon();
            String name = keyframeTrack.customName;
            if (name == null) {
                name = keyframeType.name();
            }
            String nameWithIcon = name;
            if (icon != null) {
                nameWithIcon = icon + " " + name;
            }

            keyframeTrack.animatedOffsetInUi *= animationMultiplier;
            if (Math.abs(keyframeTrack.animatedOffsetInUi) < 1) {
                keyframeTrack.animatedOffsetInUi = 0;
            }

            float trackOffset = repositioningKeyframeTrack == trackIndex ? mouseY - dragStartMouseY : (int) keyframeTrack.animatedOffsetInUi;
            ImGui.setCursorPosX(repositioningKeyframeTrack == trackIndex ? 3 : 2);
            ImGui.setCursorPosY(ImGui.getCursorPosY() + trackOffset);

            if (keyframeTrack.customColour != 0) {
                int colour = keyframeTrack.customColour & 0xFFFFFF;
                colour |= 0x30000000;
                float rectY = y + 3 + trackIndex * lineHeight;
                if (trackIndex == 0) {
                    rectY -= 1;
                }
                drawList.addRectFilled(x + middleX + 1, rectY, x + width,
                        y + 2 + trackIndex * lineHeight + lineHeight, colour);
            }

            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0, 0);
            if (repositioningKeyframeTrack == trackIndex) {
                ImGui.text("\ue945");
            } else {
                ImGui.textDisabled("\ue945");
                if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
                    repositioningKeyframeTrack = trackIndex;
                }
            }
            ImGui.sameLine();
            ImGui.popStyleVar();

            if (keyframeTrack.nameEditField != null) {
                ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0, 0);
                ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0);
                if (icon != null) {
                    ImGui.text(icon + " ");
                    ImGui.sameLine();
                }
                ImGui.setNextItemWidth(120);
                if (keyframeTrack.forceFocusTrack) {
                    keyframeTrack.forceFocusTrack = false;
                    ImGui.setKeyboardFocusHere();
                }
                boolean returnValue = ImGui.inputText("##TrackName", keyframeTrack.nameEditField, ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);
                if (returnValue || ImGui.isItemDeactivated()) {
                    keyframeTrack.customName = ImGuiHelper.getString(keyframeTrack.nameEditField).trim();
                    if (keyframeTrack.customName.isEmpty() || keyframeTrack.customName.equals(keyframeType.name())) {
                        keyframeTrack.customName = null;
                    }
                    keyframeTrack.nameEditField = null;
                }
                ImGui.popStyleVar(2);
            } else {
                if (keyframeTrack.enabled) {
                    if (keyframeTrack.customColour != 0) {
                        ImGui.textColored(keyframeTrack.customColour, nameWithIcon);
                    } else {
                        ImGui.text(nameWithIcon);
                    }
                } else {
                    ImGui.textDisabled(nameWithIcon);
                }
                if (ImGui.isItemClicked(ImGuiMouseButton.Left) && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                    keyframeTrack.nameEditField = ImGuiHelper.createResizableImString(name);
                    keyframeTrack.forceFocusTrack = true;
                }
                if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
                    ImGui.openPopup("##TrackPopup");
                }
            }

            ImGui.sameLine();

            float buttonX = x + middleX - (buttonSize + spacingX) * 3;
            float buttonY = ImGui.getCursorScreenPosY();
            ImGui.setCursorPosX(buttonX - x);
            if (ImGui.invisibleButton("##TrackOptions", buttonSize, buttonSize) || ImGui.isItemClicked(ImGuiMouseButton.Right)) {
                ImGui.openPopup("##TrackPopup");
            }
            drawList.addText(buttonX - 2, buttonY, -1, "\ue5d2");
            ImGuiHelper.tooltip("Open track options");

            ImGui.sameLine();

            buttonX += buttonSize + spacingX;
            ImGui.setCursorPosX(buttonX - x);
            ImGui.invisibleButton("##ToggleEnabled", buttonSize, buttonSize);
            if (ImGui.isItemHovered(trackDisabledButtonDrag ? ImGuiHoveredFlags.AllowWhenBlockedByActiveItem : 0)) {
                if (trackDisabledButtonDrag) {
                    keyframeTrack.enabled = trackDisabledButtonDragValue;
                    editorState.markDirty();
                } else if (ImGui.isMouseClicked(ImGuiMouseButton.Left, false)) {
                    keyframeTrack.enabled = !keyframeTrack.enabled;
                    editorState.markDirty();
                    trackDisabledButtonDragValue = keyframeTrack.enabled;
                    trackDisabledButtonDrag = true;
                }
            }
            if (keyframeTrack.enabled) {
                drawList.addText(buttonX - 2, buttonY, -1, "\ue8f4");
                ImGuiHelper.tooltip("Disable keyframe track");
            } else {
                drawList.addText(buttonX - 2, buttonY, -1, "\ue8f5");
                ImGuiHelper.tooltip("Enable keyframe track");
            }

            if (keyframeTrack.keyframeType.canBeCreatedNormally()) {
                ImGui.sameLine();
                buttonX += buttonSize + spacingX;
                ImGui.setCursorPosX(buttonX - x);
                if (ImGui.invisibleButton("##Add", buttonSize, buttonSize)) {
                    createNewKeyframe(trackIndex, cursorTicks, keyframeType, keyframeTrack);

                    if (keyframeType instanceof CameraKeyframeType && Minecraft.getInstance().player != Minecraft.getInstance().cameraEntity) {
                        ReplayUI.setInfoOverlay("Camera keyframes aren't needed for spectating a player!");
                        Minecraft.getInstance().getConnection().sendUnsignedCommand("spectate");
                    }
                }
                drawList.addText(buttonX - 2, buttonY, -1, "\ue148");
                ImGuiHelper.tooltip("Add keyframe");
            }

            if (ImGui.beginPopup("##CreateKeyframe")) {
                if (createKeyframeWithPopup != null) {
                    hasOpenPopup = true;
                    Keyframe keyframe = createKeyframeWithPopup.render();
                    if (keyframe != null) {
                        upgradeToSceneWrite();
                        editorScene.setKeyframe(trackIndex, createKeyframeWithPopupTick, keyframe);
                        editorState.markDirty();
                        ImGui.closeCurrentPopup();
                    }
                } else {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }

            boolean openTrackColourPopup = false;

            if (ImGui.beginPopup("##TrackPopup")) {
                if (ImGui.menuItem("\ue3c9 Rename")) {
                    keyframeTrack.nameEditField = ImGuiHelper.createResizableImString(name);
                    keyframeTrack.forceFocusTrack = true;
                }
                if (ImGui.menuItem("\ue40a Set Colour")) {
                    openTrackColourPopup = true;
                }
                if (ImGui.menuItem("\ue872 Delete track")) {
                    keyframeTrackToDelete = trackIndex;
                }
                if (ImGui.menuItem("\ue14a Clear Keyframes")) {
                    keyframeTrackToClear = trackIndex;
                }
                ImGui.endPopup();
            }

            if (openTrackColourPopup) {
                ImGui.openPopup("##SetTrackColour");
            }
            if (ImGui.beginPopup("##SetTrackColour")) {
                if (ImGui.button("Reset to Default")) {
                    keyframeTrack.customColour = 0;
                    ImGui.closeCurrentPopup();
                } else {
                    int colour = keyframeTrack.customColour;
                    if (colour == 0) {
                        colour = ImGui.getColorU32(ImGuiCol.Text);
                    }
                    ImVec4 imVec4 = new ImVec4();
                    ImGui.colorConvertU32ToFloat4(colour, imVec4);
                    float[] colourArray = new float[]{imVec4.x, imVec4.y, imVec4.z};
                    if (ImGui.colorPicker3("Track Colour", colourArray)) {
                        keyframeTrack.customColour = ImGui.colorConvertFloat4ToU32(colourArray[0], colourArray[1], colourArray[2], 1.0f);
                    }
                }
                ImGui.endPopup();
            }

            ImGui.setCursorPosY(ImGui.getCursorPosY() - trackOffset);

            ImGui.separator();

            ImGui.popID();
        }

        if (!hasOpenPopup) {
            createKeyframeWithPopup = null;
            createKeyframeWithPopupTick = cursorTicks;
        }
        openCreateKeyframeAtTickTrack = -1;

        if (keyframeTrackToDelete >= 0) {
            upgradeToSceneWrite();

            if (keyframeTrackToDelete < editorScene.keyframeTracks.size()) {
                List<EditorSceneHistoryAction> undo = new ArrayList<>();
                List<EditorSceneHistoryAction> redo = new ArrayList<>();

                KeyframeTrack keyframeTrack = editorScene.keyframeTracks.get(keyframeTrackToDelete);

                undo.add(new EditorSceneHistoryAction.AddTrack(keyframeTrack.keyframeType, keyframeTrackToDelete));
                for (Map.Entry<Integer, Keyframe> entry : keyframeTrack.keyframesByTick.entrySet()) {
                    undo.add(new EditorSceneHistoryAction.SetKeyframe(keyframeTrack.keyframeType, keyframeTrackToDelete, entry.getKey(), entry.getValue().copy()));
                }

                redo.add(new EditorSceneHistoryAction.RemoveTrack(keyframeTrack.keyframeType, keyframeTrackToDelete));

                editorScene.push(new EditorSceneHistoryEntry(undo, redo, "Delete " + keyframeTrack.keyframeType.name() + " track"));
                editorState.markDirty();
                selectedKeyframesList.clear();
            }
        } else if (keyframeTrackToClear >= 0) {
            upgradeToSceneWrite();

            if (keyframeTrackToClear < editorScene.keyframeTracks.size()) {
                List<EditorSceneHistoryAction> undo = new ArrayList<>();
                List<EditorSceneHistoryAction> redo = new ArrayList<>();

                KeyframeTrack keyframeTrack = editorScene.keyframeTracks.get(keyframeTrackToClear);

                for (Map.Entry<Integer, Keyframe> entry : keyframeTrack.keyframesByTick.entrySet()) {
                    undo.add(new EditorSceneHistoryAction.SetKeyframe(keyframeTrack.keyframeType, keyframeTrackToClear, entry.getKey(), entry.getValue().copy()));
                    redo.add(new EditorSceneHistoryAction.RemoveKeyframe(keyframeTrack.keyframeType, keyframeTrackToClear, entry.getKey()));
                }

                editorScene.push(new EditorSceneHistoryEntry(undo, redo, "Clear " + keyframeTrack.keyframeType.name() + " track"));
                editorState.markDirty();
                selectedKeyframesList.clear();
            }
        }

        ImGui.setCursorPosX(8);
        if (ImGui.smallButton("Add Element")) {
            ImGui.openPopup("##AddKeyframeElement");
        }

        ImGui.sameLine();

        boolean openNewScenePopup = false;
        boolean openRenameScenePopup = false;
        boolean openDeleteScenePopup = false;

        List<EditorScene> scenes = editorState.getScenes(editorSceneStamp);

        ImGui.setNextItemWidth(middleX - ImGui.getCursorPosX() - spacingX);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 0);
        if (ImGui.beginCombo("##SceneSwitcher", editorScene.name, ImGuiComboFlags.HeightLargest)) {
            if (ImGui.menuItem("\ue148 New Scene")) {
                openNewScenePopup = true;
            }
            if (ImGui.menuItem("\ue3c9 Rename")) {
                openRenameScenePopup = true;
            }
            if (scenes.size() > 1 && editorScene.keyframeTracks.isEmpty() && ImGui.menuItem("\ue92b Delete Forever")) {
                openDeleteScenePopup = true;
            }

            ImGui.separator();

            for (int i = 0; i < scenes.size(); i++) {
                EditorScene otherScene = scenes.get(i);

                ImGui.pushID(i);
                boolean selected = i == editorState.getSceneIndex();
                if (ImGui.selectable(otherScene.name, selected) && !selected) {
                    upgradeToSceneWrite();
                    editorState.setSceneIndex(i, editorSceneStamp);
                }
                if (selected) ImGui.setItemDefaultFocus();
                ImGui.popID();
            }

            ImGui.endCombo();
        }
        ImGui.popStyleVar();

        // Add a bit of extra space at the bottom
        ImGui.dummy(0, lineHeight / 4);

        if (openNewScenePopup) {
            ImGui.openPopup("##NewScene");
            sceneNameString = ImGuiHelper.createResizableImString("Scene " + (scenes.size() + 1));
        }
        if (ImGui.beginPopup("##NewScene")) {
            ImGui.inputText("Name", sceneNameString);

            if (ImGui.button("Create")) {
                String sceneName = ImGuiHelper.getString(sceneNameString).trim();
                if (!sceneName.isEmpty()) {
                    upgradeToSceneWrite();
                    scenes.add(new EditorScene(sceneName));
                    editorState.setSceneIndex(scenes.size() - 1, editorSceneStamp);
                    editorState.markDirty();
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }

        if (openRenameScenePopup) {
            ImGui.openPopup("##RenameScene");
            sceneNameString = ImGuiHelper.createResizableImString(editorScene.name);
        }
        if (ImGui.beginPopup("##RenameScene")) {
            ImGui.inputText("Name", sceneNameString);

            if (ImGui.button("Rename")) {
                String sceneName = ImGuiHelper.getString(sceneNameString).trim();
                if (!sceneName.isEmpty()) {
                    upgradeToSceneWrite();
                    editorScene.name = sceneName;
                    editorState.markDirty();
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }

        if (openDeleteScenePopup) {
            ImGui.openPopup("##DeleteScene");
        }
        if (ImGui.beginPopup("##DeleteScene")) {
            if (scenes.size() > 1 && editorScene.keyframeTracks.isEmpty()) {
                ImGui.text("Are you sure you want to delete this scene?");
                ImGui.text("This action is PERMANENT");
                ImGui.text("You will not be able to undo this action");

                if (ImGui.button("Delete Forever")) {
                    upgradeToSceneWrite();
                    int sceneIndex = editorState.getSceneIndex();
                    scenes.remove(sceneIndex);
                    if (sceneIndex >= scenes.size()) {
                        editorState.setSceneIndex(scenes.size() - 1, editorSceneStamp);
                    }
                    editorState.markDirty();
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    ImGui.closeCurrentPopup();
                }
            } else {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }

        if (ImGui.beginPopup("##AddKeyframeElement")) {
            for (KeyframeType<?> type : KeyframeRegistry.getTypes()) {
                if (!type.canBeCreatedNormally()) {
                    continue;
                }
                if (ImGui.selectable(type.name())) {
                    upgradeToSceneWrite();
                    List<EditorSceneHistoryAction> undo = new ArrayList<>();
                    List<EditorSceneHistoryAction> redo = new ArrayList<>();

                    int index = editorScene.keyframeTracks.size();
                    undo.add(new EditorSceneHistoryAction.RemoveTrack(type, index));
                    redo.add(new EditorSceneHistoryAction.AddTrack(type, index));

                    editorScene.push(new EditorSceneHistoryEntry(undo, redo, "Create " + type.name() + " track"));
                    editorState.markDirty();
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.endPopup();
        }
    }

    private static void createNewKeyframe(int trackIndex, int tick, KeyframeType<?> keyframeType, KeyframeTrack keyframeTrack) {
        if (!keyframeType.canBeCreatedNormally()) {
            return;
        }

        upgradeToSceneWrite();

        Keyframe keyframe = keyframeType.createDirect();
        if (keyframe != null) {
            editorScene.setKeyframe(trackIndex, tick, keyframe);
            editorState.markDirty();
        } else {
            if (keyframeType == TimelapseKeyframeType.INSTANCE && keyframeTrack.keyframesByTick.isEmpty()) {
                editorScene.setKeyframe(trackIndex, tick, new TimelapseKeyframe(0));
                editorState.markDirty();
            } else {
                createKeyframeWithPopup = keyframeType.createPopup();
                if (createKeyframeWithPopup != null) {
                    ImGui.openPopup("##CreateKeyframe");
                    createKeyframeWithPopupTick = tick;
                }
            }
        }
    }

    private static void renderSeparators(int minorsPerMajor, float x, int middleX, float minorSeparatorWidth, int errorOffset, float width, ImDrawList drawList, float y, int timestampHeight, int middleY, int minTicks, int ticksPerMinor, boolean showSubSeconds, int majorSeparatorHeight, int minorSeparatorHeight) {
        int minor = -minorsPerMajor;
        while (true) {
            float h = x + middleX + minorSeparatorWidth * minor;
            int hi = (int) (h + errorOffset);

            if (hi >= x + width - 1) {
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
        if (cursorX > middleX - 10 && cursorX < width +10) {
            int colour = -1;
            if (cursorTicks < currentReplayTick) {
                colour = 0x80FFFFFF;
            }

            int size = ReplayUI.scaleUi(5);
            drawList.addTriangleFilled(x + cursorX, y + middleY, x + cursorX - size*2, y + timestampHeight + size,
                x + cursorX + size*2, y + timestampHeight + size, colour);
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
