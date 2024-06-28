package com.moulberry.flashback.ui.windows;

import com.moulberry.flashback.FlashbackClient;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.Keyframes;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.impl.TickrateKeyframe;
import com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.ui.ImGuiHelper;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiStyleVar;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

public class Timeline {

    private static float zoomMin = 0.5f;
    private static float zoomMax = 1.0f;
    private static float zoomMinBeforeDrag = zoomMin;
    private static float zoomMaxBeforeDrag = zoomMax;
    private static boolean grabbedZoomBar = false;
    private static boolean grabbedZoomBarResizeLeft = false;
    private static boolean grabbedZoomBarResizeRight = false;
    private static boolean grabbedPlayback = false;
    private static boolean grabbedKeyframe = false;

    private static float[] speedKeyframeInput = new float[]{1.0f};
    private static int[] timeOfDayKeyframeInput = new int[]{6000};
    private static float[] fogDistanceKeyframeInput = new float[]{256.0f};

    private record SelectedKeyframe(KeyframeType type, int keyframeTick, float mouseClickOffset) {}
    private static SelectedKeyframe selectedKeyframe = null;

    private static final float[] replayTickSpeeds = new float[]{
         1.0f,
         2.0f,
         4.0f,
         10.0f,
         20.0f,
         40.0f,
         100.0f,
         200.0f,
         400.0f
    };

    private static final int KEYFRAME_SIZE = 10;

    public static void render() {
        ReplayServer replayServer = FlashbackClient.getReplayServer();
        if (replayServer == null) {
            return;
        }

        ImGuiHelper.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        boolean timelineVisible = ImGui.begin("Timeline");
        ImGuiHelper.popStyleVar();

        if (timelineVisible) {
            ImDrawList drawList = ImGui.getWindowDrawList();

            float maxX = ImGui.getWindowContentRegionMaxX();
            float maxY = ImGui.getWindowContentRegionMaxY();
            float minX = ImGui.getWindowContentRegionMinX();
            float minY = ImGui.getWindowContentRegionMinY();

            float x = ImGui.getWindowPosX() + minX;
            float y = ImGui.getWindowPosY() + minY;
            float width = maxX - minX;
            float height = maxY - minY;

            if (width < 1 || height < 1) {
                ImGui.end();
                return;
            }

            if (selectedKeyframe != null) {
                TreeMap<Integer, Keyframe<?>> keyframeTimes = Keyframes.keyframes.get(selectedKeyframe.type);
                if (keyframeTimes == null) {
                    selectedKeyframe = null;
                } else if (keyframeTimes.get(selectedKeyframe.keyframeTick) == null) {
                    selectedKeyframe = null;
                }
            }

            float mouseX = ImGui.getMousePosX();
            float mouseY = ImGui.getMousePosY();

            int currentReplayTick = replayServer.getReplayTick();
            int totalTicks = Math.max(replayServer.getTotalReplayTicks(), currentReplayTick);

            int middleX = 240;

            float availableWidth = width - middleX;
            float shownTicks = Math.round((zoomMax - zoomMin) * totalTicks);
            int targetMajorSize = 60;

            float targetTicksPerMajor = 1f / (availableWidth / shownTicks / targetMajorSize);
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
            int minTicks = Math.round(zoomMin * totalTicks / majorSnap) * majorSnap;
            float minorSeparatorWidth = (availableWidth / shownTicks) * ticksPerMinor;

            float error = zoomMin*totalTicks - minTicks;
            int errorOffset = (int)(-error/ticksPerMinor*minorSeparatorWidth);

            float availableTicks = availableWidth / minorSeparatorWidth * ticksPerMinor;

            int cursorTicks = currentReplayTick;
            if (grabbedPlayback) {
                cursorTicks = calculatePlaybackTarget(mouseX, middleX, errorOffset, width, availableTicks, minTicks);
            } else if (replayServer.jumpToTick >= 0) {
                cursorTicks = replayServer.jumpToTick;
            }

            int cursorX = middleX + (int)((cursorTicks - minTicks)/availableTicks*(width-middleX)) + errorOffset;
            int minorSeparatorHeight = 10;
            int majorSeparatorHeight = minorSeparatorHeight * 2;
            int timestampHeight = 20;
            int middleY = timestampHeight+majorSeparatorHeight;

            renderKeyframeElements(replayServer, x, y + middleY, cursorTicks, middleX);

            float zoomBarWidth = width - (middleX+1);
            float zoomBarMin = x+middleX+1 + zoomMin*zoomBarWidth;
            float zoomBarMax = x+middleX+1 + zoomMax*zoomBarWidth;

            int zoomBarHeight = 6;
            boolean zoomBarExpanded = false;
            if (mouseY >= y+height-zoomBarHeight*2 && mouseY <= y+height || grabbedZoomBar) {
                zoomBarHeight *= 2;
                zoomBarExpanded = true;
            }
            boolean zoomBarHovered = mouseY >= y+height-zoomBarHeight && mouseY <= y+height &&
                mouseX >= x+zoomBarMin && mouseX <= x+zoomBarMax;

            drawList.pushClipRect(x+middleX, y, x+width, y+height);

            renderKeyframes(x, y + middleY, mouseX, minTicks, availableTicks, middleX, width, errorOffset);
            drawSeparators(minorsPerMajor, x, middleX, minorSeparatorWidth, errorOffset, width, drawList, y, timestampHeight, middleY, minTicks, ticksPerMinor, showSubSeconds, majorSeparatorHeight, minorSeparatorHeight);
            drawPlaybackHead(cursorX, x, middleX, width, cursorTicks, currentReplayTick, drawList, y, middleY, timestampHeight, height, zoomBarHeight);

            drawList.popClipRect();

            // Timeline end line
            if (zoomMax >= 1.0) {
                drawList.addLine(x+width-2, y+timestampHeight, x+width-2, y+height-zoomBarHeight, -1);
            }
            // Middle divider (x)
            drawList.addLine(x+middleX, y+timestampHeight, x+middleX, y+height, -1);

            // Middle divider (y)
            drawList.addLine(0, y+middleY, width, y+middleY, -1);

            // Zoom Bar
            if (zoomBarExpanded) {
                drawList.addRectFilled(x+middleX+1, y+height-zoomBarHeight, x+width, y+height, 0xFF404040, zoomBarHeight);
            }
            if (zoomBarHovered || grabbedZoomBar) {
                drawList.addRectFilled(x+zoomBarMin+zoomBarHeight/2f, y+height-zoomBarHeight, x+zoomBarMax-zoomBarHeight/2f, y+height, -1, zoomBarHeight);

                // Left/right resize
                drawList.addCircleFilled(x+zoomBarMin+zoomBarHeight/2f, y+height-zoomBarHeight/2f, zoomBarHeight/2f, 0xffaaaa00);
                drawList.addCircleFilled(x+zoomBarMax-zoomBarHeight/2f, y+height-zoomBarHeight/2f, zoomBarHeight/2f, 0xffaaaa00);
            } else {
                drawList.addRectFilled(x+zoomBarMin, y+height-zoomBarHeight, x+zoomBarMax, y+height, -1, zoomBarHeight);
            }

            // Pause/play button
            int controlSize = 24;
            int controlsY = (int) y + middleY/2 - controlSize /2;

            // Skip backwards
            int skipBackwardsX = (int) x + middleX/6 - controlSize/2;
            drawList.addTriangleFilled(skipBackwardsX + controlSize/3, controlsY + controlSize/2,
                skipBackwardsX + controlSize, controlsY,
                skipBackwardsX + controlSize, controlsY + controlSize, -1);
            drawList.addRectFilled(skipBackwardsX, controlsY,
                skipBackwardsX + controlSize/3, controlsY+controlSize, -1);

            // Slow down
            int slowDownX = (int) x + middleX*2/6 - controlSize/2;
            drawList.addTriangleFilled(slowDownX, controlsY + controlSize/2,
                slowDownX + controlSize/2, controlsY,
                slowDownX + controlSize/2, controlsY+controlSize, -1);
            drawList.addTriangleFilled(slowDownX + controlSize/2, controlsY + controlSize/2,
                slowDownX + controlSize, controlsY,
                slowDownX + controlSize, controlsY + controlSize, -1);

            int pauseX = (int) x + middleX/2 - controlSize /2;
            if (replayServer.replayPaused) {
                // Play button
                drawList.addTriangleFilled(pauseX + controlSize/12, controlsY,
                    pauseX + controlSize, controlsY + controlSize/2,
                    pauseX + controlSize/12, controlsY + controlSize,
                    -1);
            } else {
                // Pause button
                drawList.addRectFilled(pauseX, controlsY,
                    pauseX + controlSize/3, controlsY + controlSize, -1);
                drawList.addRectFilled(pauseX + controlSize*2/3, controlsY,
                    pauseX + controlSize, controlsY + controlSize, -1);
            }

            // Fast-forward
            int fastForwardsX = (int) x + middleX*4/6 - controlSize/2;
            drawList.addTriangleFilled(fastForwardsX, controlsY,
                fastForwardsX + controlSize/2, controlsY + controlSize/2,
                fastForwardsX, controlsY+controlSize, -1);
            drawList.addTriangleFilled(fastForwardsX + controlSize/2, controlsY,
                fastForwardsX + controlSize, controlsY + controlSize/2,
                fastForwardsX + controlSize/2, controlsY + controlSize, -1);

            // Skip forward
            int skipForwardsX = (int) x + middleX*5/6 - controlSize/2;
            drawList.addTriangleFilled(skipForwardsX, controlsY,
                skipForwardsX + controlSize*2/3, controlsY + controlSize/2,
                skipForwardsX, controlsY + controlSize, -1);
            drawList.addRectFilled(skipForwardsX + controlSize*2/3, controlsY,
                skipForwardsX + controlSize, controlsY+controlSize, -1);


            boolean hoveredControls = mouseY > controlsY && mouseY < controlsY + controlSize;
            boolean hoveredSkipBackwards = hoveredControls && mouseX >= skipBackwardsX && mouseX <= skipBackwardsX+controlSize;
            boolean hoveredSlowDown = hoveredControls && mouseX >= slowDownX && mouseX <= slowDownX+controlSize;
            boolean hoveredPause = hoveredControls && mouseX >= pauseX && mouseX <= pauseX+controlSize;
            boolean hoveredFastForwards = hoveredControls && mouseX >= fastForwardsX && mouseX <= fastForwardsX+controlSize;
            boolean hoveredSkipForwards = hoveredControls && mouseX >= skipForwardsX && mouseX <= skipForwardsX+controlSize;

            if (hoveredSkipBackwards) {
                ImGuiHelper.drawTooltip("Skip backwards");
            } else if (hoveredSlowDown) {
                ImGuiHelper.drawTooltip("Slow down\n(Current speed: " + (replayServer.desiredTickRate/20f) + "x)");
            } else if (hoveredPause) {
                if (replayServer.replayPaused) {
                    ImGuiHelper.drawTooltip("Start replay");
                } else {
                    ImGuiHelper.drawTooltip("Pause replay");
                }
            } else if (hoveredFastForwards) {
                ImGuiHelper.drawTooltip("Fast-forwards\n(Current speed: " + (replayServer.desiredTickRate/20f) + "x)");
            } else if (hoveredSkipForwards) {
                ImGuiHelper.drawTooltip("Skip forwards");
            }

            renderKeyframeOptionsPopup();

            boolean leftClicked = ImGui.isMouseClicked(ImGuiMouseButton.Left);
            boolean rightClicked = ImGui.isMouseClicked(ImGuiMouseButton.Right);
            if ((leftClicked || rightClicked) && !ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopup)) {
                releaseGrabbed(mouseX, middleX, errorOffset, width, availableTicks, minTicks, replayServer);
                selectedKeyframe = null;

                if (hoveredControls) {
                    // Skip backwards
                    if (hoveredSkipBackwards) {
                        replayServer.goToReplayTick(0);
                    }

                    // Slow down
                    if (hoveredSlowDown) {
                        float highest = replayTickSpeeds[0];
                        float currentTickRate = replayServer.desiredTickRate;

                        for (float replayTickSpeed : replayTickSpeeds) {
                            if (replayTickSpeed >= currentTickRate) {
                                break;
                            }
                            highest = replayTickSpeed;
                        }

                        replayServer.desiredTickRate = highest;
                    }

                    // Pause button
                    if (hoveredPause) {
                        replayServer.replayPaused = !replayServer.replayPaused;
                    }

                    // Fast-forward
                    if (hoveredFastForwards) {
                        float lowest = replayTickSpeeds[replayTickSpeeds.length - 1];
                        float currentTickRate = replayServer.desiredTickRate;

                        for (int i = replayTickSpeeds.length - 1; i >= 0; i--) {
                            float replayTickSpeed = replayTickSpeeds[i];
                            if (replayTickSpeed <= currentTickRate) {
                                break;
                            }
                            lowest = replayTickSpeed;
                        }

                        replayServer.desiredTickRate = lowest;
                    }

                    // Skip forward
                    if (hoveredSkipForwards) {
                        replayServer.goToReplayTick(totalTicks);
                    }
                }

                if (mouseY > y && mouseY < y+middleY) {
                    // Timeline
                    if (mouseX > x+middleX && mouseX < x+width) {
                        replayServer.replayPaused = true;
                        grabbedPlayback = leftClicked;
                    }
                }

                // Tracks
                if (mouseY > y+middleY && mouseY < y+height && mouseX > x+middleX && mouseX < x+width) {
                    float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();

                    int trackIndex = (int) Math.max(0, Math.floor((mouseY - (y+middleY+2))/lineHeight));
                    for (Map.Entry<KeyframeType, TreeMap<Integer, Keyframe<?>>> entry : Keyframes.keyframes.entrySet()) {
                        if (trackIndex == 0) {
                            TreeMap<Integer, Keyframe<?>> treeMap = entry.getValue();
                            int tick = calculatePlaybackTarget(mouseX, middleX, errorOffset, width, availableTicks, minTicks);

                            Map.Entry<Integer, Keyframe<?>> floor = treeMap.floorEntry(tick);
                            Map.Entry<Integer, Keyframe<?>> ceil = treeMap.ceilingEntry(tick);

                            float floorDistance = Float.MAX_VALUE;

                            if (floor != null) {
                                float keyframeX = x + middleX + (floor.getKey() - minTicks)/availableTicks*(width-middleX) + errorOffset;
                                floorDistance = Math.abs(mouseX - keyframeX);
                                if (floorDistance < KEYFRAME_SIZE) {
                                    selectedKeyframe = new SelectedKeyframe(entry.getKey(), floor.getKey(), mouseX - keyframeX);
                                    grabbedKeyframe = leftClicked;
                                }
                            }

                            if (ceil != null) {
                                float keyframeX = x + middleX + (ceil.getKey() - minTicks)/availableTicks*(width-middleX) + errorOffset;
                                float ceilDistance = Math.abs(mouseX - keyframeX);
                                if (ceilDistance < floorDistance && ceilDistance < KEYFRAME_SIZE) {
                                    selectedKeyframe = new SelectedKeyframe(entry.getKey(), ceil.getKey(), mouseX - keyframeX);
                                    grabbedKeyframe = leftClicked;
                                }
                            }

                            break;
                        } else {
                            trackIndex -= 1;
                        }
                    }

                    if (rightClicked && selectedKeyframe != null) {
                        ImGui.openPopup("##KeyframePopup");
                    }
                }

                if (zoomBarHovered) {
                    if (mouseX <= x+zoomBarMin+zoomBarHeight) {
                        grabbedZoomBarResizeLeft = leftClicked;
                    } else if (mouseX >= x+zoomBarMax-zoomBarHeight) {
                        grabbedZoomBarResizeRight = leftClicked;
                    }
                    grabbedZoomBar = leftClicked;
                    zoomMinBeforeDrag = zoomMin;
                    zoomMaxBeforeDrag = zoomMax;
                } else if (zoomBarExpanded && mouseX >= x+middleX && mouseX <= x+width) {
                    float zoomSize = zoomMax - zoomMin;
                    float targetZoom = (mouseX - (x+middleX))/(x+width - (x+middleX));
                    zoomMin = targetZoom - zoomSize/2f;
                    zoomMax = targetZoom + zoomSize/2f;
                    if (zoomMax > 1.0f) {
                        zoomMax = 1.0f;
                        zoomMin = zoomMax - zoomSize;
                    } else if (zoomMin < 0.0f) {
                        zoomMin = 0.0f;
                        zoomMax = zoomMin + zoomSize;
                    }

                    grabbedZoomBar = leftClicked;
                    zoomMinBeforeDrag = zoomMin;
                    zoomMaxBeforeDrag = zoomMax;
                }
            } else if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
                if (zoomBarWidth > 1f && grabbedZoomBar) {
                    float dx = ImGui.getMouseDragDeltaX();
                    float factor = dx / zoomBarWidth;

                    if (grabbedZoomBarResizeLeft) {
                        ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
                        zoomMin = Math.max(0, Math.min(zoomMax-0.01f, zoomMinBeforeDrag + factor));
                    } else if (grabbedZoomBarResizeRight) {
                        ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
                        zoomMax = Math.max(zoomMin+0.01f, Math.min(1, zoomMaxBeforeDrag + factor));
                    } else {
                        ImGui.setMouseCursor(ImGuiMouseCursor.Hand);

                        float zoomSize = zoomMaxBeforeDrag - zoomMinBeforeDrag;
                        if (factor < 0) {
                            zoomMin = Math.max(0, zoomMinBeforeDrag + factor);
                            zoomMax = zoomMin + zoomSize;
                        } else if (factor > 0) {
                            zoomMax = Math.min(1, zoomMaxBeforeDrag + factor);
                            zoomMin = zoomMax - zoomSize;
                        }
                    }
                }
                if (grabbedPlayback) {
                    int desiredTick = calculatePlaybackTarget(mouseX, middleX, errorOffset, width, availableTicks, minTicks);

                    if (desiredTick > currentReplayTick) {
                        replayServer.goToReplayTick(desiredTick);
                    }

                    replayServer.replayPaused = true;
                }
            } else if (!ImGui.isAnyMouseDown()) {
                releaseGrabbed(mouseX, middleX, errorOffset, width, availableTicks, minTicks, replayServer);
            }
        }
        ImGui.end();
    }

    private static void renderKeyframeOptionsPopup() {
        if (ImGui.beginPopup("##KeyframePopup")) {
            if (selectedKeyframe == null) {
                ImGui.closeCurrentPopup();
                ImGui.endPopup();
                return;
            }

            var keyframeTimes = Keyframes.keyframes.get(selectedKeyframe.type);
            if (keyframeTimes == null) {
                ImGui.closeCurrentPopup();
                ImGui.endPopup();
                return;
            }

            Keyframe<?> keyframe = keyframeTimes.get(selectedKeyframe.keyframeTick);
            if (keyframe == null) {
                ImGui.closeCurrentPopup();
                ImGui.endPopup();
                return;
            }

            int[] type = new int[]{keyframe.interpolationType().ordinal()};
            ImGui.setNextItemWidth(160);
            if (ImGuiHelper.combo("Type", type, InterpolationType.NAMES)) {
                keyframe.interpolationType(InterpolationType.INTERPOLATION_TYPES[type[0]]);
            }

            if (ImGui.button("Remove") && selectedKeyframe != null) {
                keyframeTimes.remove(selectedKeyframe.keyframeTick);
                selectedKeyframe = null;
            }

            ImGui.endPopup();
        }
    }

    private static void releaseGrabbed(float mouseX, int middleX, int errorOffset, float width, float availableTicks, int minTicks, ReplayServer replayServer) {
        grabbedZoomBar = false;
        grabbedZoomBarResizeLeft = false;
        grabbedZoomBarResizeRight = false;

        if (grabbedPlayback) {
            int desiredTick = calculatePlaybackTarget(mouseX, middleX, errorOffset, width, availableTicks, minTicks);
            replayServer.goToReplayTick(desiredTick);
            replayServer.replayPaused = true;
            grabbedPlayback = false;
        }
        if (grabbedKeyframe) {
            if (selectedKeyframe != null) {
                int desiredTick = calculatePlaybackTarget(mouseX - selectedKeyframe.mouseClickOffset, middleX, errorOffset, width, availableTicks, minTicks);
                if (desiredTick != selectedKeyframe.keyframeTick) {
                    var keyframeTimes = Keyframes.keyframes.get(selectedKeyframe.type);
                    if (keyframeTimes == null) {
                        selectedKeyframe = null;
                    } else {
                        Keyframe<?> keyframe = keyframeTimes.remove(selectedKeyframe.keyframeTick);
                        if (keyframe == null) {
                            selectedKeyframe = null;
                        } else {
                            Keyframes.keyframes.get(selectedKeyframe.type).put(desiredTick, keyframe);
                            selectedKeyframe = new SelectedKeyframe(selectedKeyframe.type, desiredTick, 0);
                        }
                    }
                }
            }
            grabbedKeyframe = false;
        }
    }


    private static void renderKeyframes(float x, float y, float mouseX, int minTicks, float availableTicks, int middleX, float width, int errorOffset) {
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();

        ImDrawList drawList = ImGui.getWindowDrawList();

        int index = 0;
        for (Map.Entry<KeyframeType, TreeMap<Integer, Keyframe<?>>> entry : Keyframes.keyframes.entrySet()) {
            TreeMap<Integer, Keyframe<?>> keyframeTimes = entry.getValue();

            for (int i = minTicks; i <= minTicks + availableTicks; i++) {
                Keyframe<?> keyframe = keyframeTimes.get(i);
                if (keyframe != null) {
                    int keyframeX = middleX + (int)((i - minTicks)/availableTicks*(width-middleX)) + errorOffset;

                    float midX = x + keyframeX;
                    float midY = y + 2 + (index+0.5f) * lineHeight;

                    if (selectedKeyframe != null && selectedKeyframe.type == entry.getKey() && selectedKeyframe.keyframeTick == i) {
                        continue;
                    }

                    drawKeyframe(drawList, keyframe.interpolationType(), midX, midY, -1);
                }
            }

            if (selectedKeyframe != null && selectedKeyframe.type == entry.getKey()) {
                Keyframe<?> keyframe = keyframeTimes.get(selectedKeyframe.keyframeTick);
                if (keyframe != null) {
                    int showKeyframeAt = selectedKeyframe.keyframeTick;
                    if (grabbedKeyframe) {
                        showKeyframeAt = calculatePlaybackTarget(mouseX - selectedKeyframe.mouseClickOffset, middleX, errorOffset, width, availableTicks, minTicks);
                    }
                    int keyframeX = middleX + (int)((showKeyframeAt - minTicks)/availableTicks*(width-middleX)) + errorOffset;

                    float midX = x + keyframeX;
                    float midY = y + 2 + (index+0.5f) * lineHeight;
                    drawKeyframe(drawList, keyframe.interpolationType(), midX, midY, 0xFF0000FF);
                }
            }

            index += 1;
        }
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

    private static void renderKeyframeElements(ReplayServer replayServer, float x, float y, int cursorTicks, int middleX) {
        ImGui.setCursorScreenPos(x + 8, y + 6);

        KeyframeType keyframeTypeToClear = null;

        ImDrawList drawList = ImGui.getWindowDrawList();

        float buttonSize = ImGui.getTextLineHeight();
        float spacingX = ImGui.getStyle().getItemSpacingX();

        for (Map.Entry<KeyframeType, TreeMap<Integer, Keyframe<?>>> entry : Keyframes.keyframes.entrySet()) {
            KeyframeType keyframeType = entry.getKey();
            TreeMap<Integer, Keyframe<?>> keyframeTimes = entry.getValue();

            ImGui.pushID(keyframeType.ordinal());

            ImGui.setCursorPosX(8);
            ImGui.text(keyframeType.name);

            ImGui.sameLine();

            float addX = middleX - buttonSize - spacingX - buttonSize - spacingX;
            float buttonY = ImGui.getCursorScreenPosY();
            ImGui.setCursorPosX(addX);
            if (ImGui.button("##Add", buttonSize, buttonSize)) {
                Keyframe<?> keyframe = switch (keyframeType) {
                    case CAMERA -> new CameraKeyframe(Minecraft.getInstance().player);
                    case FOV -> new FOVKeyframe(Minecraft.getInstance().options.fov().get());
                    case SPEED -> {
                        speedKeyframeInput[0] = replayServer.desiredTickRate / 20.0f;
                        ImGui.openPopup("##EnterSpeed");
                        yield null;
                    }
                    case TIME_OF_DAY -> {
                        timeOfDayKeyframeInput[0] = (int)(Minecraft.getInstance().level.getDayTime() % 24000);
                        ImGui.openPopup("##EnterTime");
                        yield null;
                    }
                };
                if (keyframe != null) {
                    keyframeTimes.put(cursorTicks, keyframe);
                }
            }
            drawList.addRectFilled(addX + 2, buttonY + buttonSize/2 - 1, addX + buttonSize - 2, buttonY + buttonSize/2 + 1, -1);
            drawList.addRectFilled(addX + buttonSize/2 - 1, buttonY + 2, addX + buttonSize/2 + 1, buttonY + buttonSize - 2, -1);
            ImGuiHelper.tooltip("Add keyframe");

            if (ImGui.beginPopup("##EnterSpeed")) {
                ImGui.sliderFloat("Speed", speedKeyframeInput, 0.1f, 10f);
                if (ImGui.button("Add")) {
                    keyframeTimes.put(cursorTicks, new TickrateKeyframe(speedKeyframeInput[0] * 20.0f));
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }
            if (ImGui.beginPopup("##EnterTime")) {
                ImGui.sliderInt("Time", timeOfDayKeyframeInput, 0, 24000);
                if (ImGui.button("Add")) {
                    keyframeTimes.put(cursorTicks, new TimeOfDayKeyframe(timeOfDayKeyframeInput[0]));
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
                if (ImGui.button("Cancel")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }

            ImGui.sameLine();

            float clearX = middleX - buttonSize - spacingX;
            if (ImGui.button("##Clear", buttonSize, buttonSize)) {
                keyframeTypeToClear = keyframeType;
            }
            drawList.addRectFilled(clearX + 2, buttonY + buttonSize/2 - 1, clearX + buttonSize - 2, buttonY + buttonSize/2 + 1, -1);
            ImGuiHelper.tooltip("Remove all keyframes");

            ImGui.separator();

            ImGui.popID();
        }

        if (keyframeTypeToClear != null) {
            Keyframes.keyframes.remove(keyframeTypeToClear);
        }

        ImGui.setCursorPosX(8);
        if (Keyframes.keyframes.size() < KeyframeType.KEYFRAME_TYPES.length && ImGui.smallButton("Add Element")) {
            ImGui.openPopup("##AddKeyframeElement");
        }
        if (ImGui.beginPopup("##AddKeyframeElement")) {
            for (KeyframeType keyframeType : KeyframeType.KEYFRAME_TYPES) {
                if (!Keyframes.keyframes.containsKey(keyframeType)) {
                    if (ImGui.selectable(keyframeType.name)) {
                        Keyframes.keyframes.put(keyframeType, new TreeMap<>());
                        ImGui.closeCurrentPopup();
                    }
                }
            }
            ImGui.endPopup();
        }
    }

    private static void drawSeparators(int minorsPerMajor, float x, int middleX, float minorSeparatorWidth, int errorOffset, float width, ImDrawList drawList, float y, int timestampHeight, int middleY, int minTicks, int ticksPerMinor, boolean showSubSeconds, int majorSeparatorHeight, int minorSeparatorHeight) {
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

    private static void drawPlaybackHead(int cursorX, float x, int middleX, float width, int cursorTicks, int currentReplayTick, ImDrawList drawList, float y, int middleY, int timestampHeight, float height, int zoomBarHeight) {
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

    private static int calculatePlaybackTarget(float mouseX, int middleX, int errorOffset, float width, float availableTicks, int minTicks) {
        float relativeX = mouseX - middleX - errorOffset;
        float amount = Math.max(0, Math.min(1, relativeX/(width - middleX)));
        int numTicks = Math.round(amount* availableTicks);
        int desiredTick = minTicks + numTicks;
        return desiredTick;
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
