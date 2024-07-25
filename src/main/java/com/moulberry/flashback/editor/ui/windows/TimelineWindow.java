package com.moulberry.flashback.editor.ui.windows;

import com.google.gson.reflect.TypeToken;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.editor.SavedTrack;
import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorStateCache;
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
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class TimelineWindow {

    private static float zoomMinBeforeDrag = 0.0f;
    private static float zoomMaxBeforeDrag = 1.0f;
    private static boolean grabbedZoomBar = false;
    private static boolean grabbedZoomBarResizeLeft = false;
    private static boolean grabbedZoomBarResizeRight = false;
    private static boolean grabbedExportBarResizeLeft = false;
    private static boolean grabbedExportBarResizeRight = false;
    private static boolean grabbedPlayback = false;
    private static boolean grabbedKeyframe = false;
    private static float grabbedKeyframeMouseX = 0;

    @Nullable
    private static Vector2f dragSelectOrigin = null;

    private static final float[] speedKeyframeInput = new float[]{1.0f};
    private static final int[] timeOfDayKeyframeInput = new int[]{6000};

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
    private static Keyframe<?> editingKeyframe = null;

    private static final float[] replayTickSpeeds = new float[]{1.0f, 2.0f, 4.0f, 10.0f, 20.0f, 40.0f, 100.0f, 200.0f, 400.0f};

    private static final int KEYFRAME_SIZE = 10;

    public static void render() {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            return;
        }

        ImGuiHelper.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        boolean timelineVisible = ImGui.begin("Timeline");
        ImGuiHelper.popStyleVar();

        if (timelineVisible) {
            FlashbackMeta metadata = replayServer.getMetadata();
            EditorState editorState = EditorStateCache.get(metadata.replayIdentifier);

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
            minTicks = Math.round(editorState.zoomMin * totalTicks / majorSnap) * majorSnap;

            float minorSeparatorWidth = (timelineWidth / shownTicks) * ticksPerMinor;
            availableTicks = timelineWidth / minorSeparatorWidth * ticksPerMinor;

            float errorTicks = editorState.zoomMin*totalTicks - minTicks;
            int errorOffset = (int)(-errorTicks/ticksPerMinor*minorSeparatorWidth);
            timelineOffset = middleX + errorOffset;

            int cursorTicks = currentReplayTick;
            if (grabbedPlayback) {
                cursorTicks = timelineXToReplayTick(mouseX);
            } else if (replayServer.jumpToTick >= 0) {
                cursorTicks = replayServer.jumpToTick;
            }

            int cursorX = replayTickToTimelineX(cursorTicks);

            zoomBarHeight = 6;
            float zoomBarWidth = width - (middleX+1);
            zoomBarMin = x + middleX+1 + editorState.zoomMin * zoomBarWidth;
            zoomBarMax = x + middleX+1 + editorState.zoomMax * zoomBarWidth;

            zoomBarExpanded = false;
            if (mouseY >= y + height - zoomBarHeight *2 && mouseY <= y + height || grabbedZoomBar) {
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

            if (ImGui.beginPopup("##KeyframePopup")) {
                renderKeyframeOptionsPopup(editorState);
                ImGui.endPopup();
            }

            handleKeyPresses(cursorTicks, editorState, totalTicks);

            boolean leftClicked = ImGui.isMouseClicked(ImGuiMouseButton.Left);
            boolean rightClicked = ImGui.isMouseClicked(ImGuiMouseButton.Right);
            if ((leftClicked || rightClicked) && !ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopup)) {
                handleClick(editorState, replayServer);
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

                        float zoomSize = zoomMaxBeforeDrag - zoomMinBeforeDrag;
                        if (factor < 0) {
                            editorState.zoomMin = Math.max(0, zoomMinBeforeDrag + factor);
                            editorState.zoomMax = editorState.zoomMin + zoomSize;
                        } else if (factor > 0) {
                            editorState.zoomMax = Math.min(1, zoomMaxBeforeDrag + factor);
                            editorState.zoomMin = editorState.zoomMax - zoomSize;
                        }
                    }
                    editorState.dirty = true;
                }
                if (grabbedPlayback) {
                    int desiredTick = timelineXToReplayTick(mouseX);

                    if (desiredTick > currentReplayTick) {
                        replayServer.goToReplayTick(desiredTick);
                    }

                    replayServer.replayPaused = true;
                }
            } else if (!ImGui.isAnyMouseDown()) {
                releaseGrabbed(editorState, replayServer);
            }
        }
        ImGui.end();
    }

    private static void handleKeyPresses(int cursorTicks, EditorState editorState, int totalTicks) {
        boolean pressedIn = ImGui.isKeyPressed(GLFW.GLFW_KEY_I, false);
        boolean pressedOut = ImGui.isKeyPressed(GLFW.GLFW_KEY_I, false);

        boolean ctrlPressed = Minecraft.ON_OSX ? ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_SUPER) : ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL);
        boolean pressedCopy = ctrlPressed && ImGui.isKeyPressed(GLFW.GLFW_KEY_C, false);
        boolean pressedPaste = ctrlPressed && ImGui.isKeyPressed(GLFW.GLFW_KEY_V, false);

        if (pressedIn || pressedOut) {
            int start = -1;
            int end = -1;
            if (ImGui.isKeyPressed(GLFW.GLFW_KEY_I)) {
                start = cursorTicks;
            }
            if (ImGui.isKeyPressed(GLFW.GLFW_KEY_O)) {
                end = cursorTicks;
            }
            editorState.setExportTicks(start, end, totalTicks);
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

                TreeMap<Integer, Keyframe<?>> keyframes = new TreeMap<>();
                for (int tick : selectedKeyframes.keyframeTicks()) {
                    Keyframe<?> keyframe = keyframeTrack.keyframesByTick.get(tick);
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

    private static void handleClick(EditorState editorState, ReplayServer replayServer) {
        releaseGrabbed(editorState, replayServer);
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
                float currentTickRate = replayServer.desiredTickRate;

                for (float replayTickSpeed : replayTickSpeeds) {
                    if (replayTickSpeed >= currentTickRate) {
                        break;
                    }
                    highest = replayTickSpeed;
                }

                replayServer.desiredTickRate = highest;
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
                float currentTickRate = replayServer.desiredTickRate;

                for (int i = replayTickSpeeds.length - 1; i >= 0; i--) {
                    float replayTickSpeed = replayTickSpeeds[i];
                    if (replayTickSpeed <= currentTickRate) {
                        break;
                    }
                    lowest = replayTickSpeed;
                }

                replayServer.desiredTickRate = lowest;
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
            float zoomSize = editorState.zoomMax - editorState.zoomMin;
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
            editorState.dirty = true;

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

                Map.Entry<Integer, Keyframe<?>> floor = keyframeTrack.keyframesByTick.floorEntry(tick);
                Map.Entry<Integer, Keyframe<?>> ceil = keyframeTrack.keyframesByTick.ceilingEntry(tick);

                float floorX = floor == null ? Float.NaN : x + replayTickToTimelineX(floor.getKey());
                float ceilX = ceil == null ? Float.NaN : x + replayTickToTimelineX(ceil.getKey());

                Map.Entry<Integer, Keyframe<?>> closest = Utils.chooseClosest(mouseX, floorX, floor, ceilX, ceil, KEYFRAME_SIZE);
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
                    } else if (rightClicked) {
                        ImGui.openPopup("##KeyframePopup");
                        editingKeyframe = closest.getValue();
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
        if (editingKeyframe == null || selectedKeyframesList.isEmpty()) {
            editingKeyframe = null;
            ImGui.closeCurrentPopup();
            return;
        }

        int[] type = new int[]{editingKeyframe.interpolationType().ordinal()};
        ImGui.setNextItemWidth(160);
        if (ImGuiHelper.combo("Type", type, InterpolationType.NAMES)) {
            InterpolationType interpolationType = InterpolationType.INTERPOLATION_TYPES[type[0]];

            editingKeyframe.interpolationType(interpolationType);

            for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                KeyframeTrack track = editorState.keyframeTracks.get(selectedKeyframes.trackIndex());
                for (int tick : selectedKeyframes.keyframeTicks()) {
                    track.keyframesByTick.get(tick).interpolationType(interpolationType);
                }
            }

            editorState.dirty = true;
        }

        boolean multiple = selectedKeyframesList.size() >= 2 || selectedKeyframesList.getFirst().keyframeTicks().size() >= 2;

        if (ImGui.button(multiple ? "Remove All" : "Remove")) {
            ImGui.closeCurrentPopup();

            for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                KeyframeTrack track = editorState.keyframeTracks.get(selectedKeyframes.trackIndex());
                for (int tick : selectedKeyframes.keyframeTicks()) {
                    track.keyframesByTick.remove(tick);
                }
            }
            editorState.keyframeTracks.removeIf(track -> track.keyframesByTick.isEmpty());

            selectedKeyframesList.clear();
            editingKeyframe = null;
            editorState.dirty = true;
        }
    }

    private static void releaseGrabbed(EditorState editorState, ReplayServer replayServer) {
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
            int grabbedDelta = timelineDeltaToReplayTickDelta(mouseX - grabbedKeyframeMouseX);
            int totalTicks = replayServer.getTotalReplayTicks();

            for (SelectedKeyframes selectedKeyframes : selectedKeyframesList) {
                int trackIndex = selectedKeyframes.trackIndex();
                KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(trackIndex);

                IntList selectedTicks = new IntArrayList(selectedKeyframes.keyframeTicks());

                // Sorting because clamping can result in overlaps
                selectedTicks.sort(mouseX < grabbedKeyframeMouseX ? IntComparators.NATURAL_COMPARATOR : IntComparators.OPPOSITE_COMPARATOR);

                selectedKeyframes.keyframeTicks().clear();

                Int2ObjectMap<Keyframe<?>> newKeyframes = new Int2ObjectOpenHashMap<>();

                for (int i = 0; i < selectedTicks.size(); i++) {
                    int tick = selectedTicks.getInt(i);

                    Keyframe<?> keyframe = keyframeTrack.keyframesByTick.remove(tick);
                    Objects.requireNonNull(keyframe);

                    int newTick = tick + grabbedDelta;
                    newTick = Math.max(0, Math.min(totalTicks, newTick));

                    newKeyframes.put(newTick, keyframe);
                }

                for (Int2ObjectMap.Entry<Keyframe<?>> entry : newKeyframes.int2ObjectEntrySet()) {
                    keyframeTrack.keyframesByTick.put(entry.getIntKey(), entry.getValue());
                    selectedKeyframes.keyframeTicks().add(entry.getIntKey());
                }

            }

//            if (selectedKeyframe != null) {
//                int desiredTick = timelineXToReplayTick(mouseX - selectedKeyframe.mouseClickOffset);
//                if (desiredTick != selectedKeyframe.keyframeTick) {
//                    KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(selectedKeyframe.trackIndex());
//
//
//
//
//                    selectedKeyframe = new SelectedKeyframe(selectedKeyframe.type(), selectedKeyframe.trackIndex(), desiredTick, 0);
//
//                    editorState.dirty = true;
//                }
//            }
            grabbedKeyframe = false;
        }
    }


    private static void renderKeyframes(EditorState editorState, float x, float y, float mouseX, int minTicks, float availableTicks, int totalTicks) {
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();

        ImDrawList drawList = ImGui.getWindowDrawList();
        ImDrawList foregroundDrawList = ImGui.getForegroundDrawList();

        foregroundDrawList.pushClipRect(x + middleX, y, x + width, y + height);

        int grabbedDelta = 0;
        if (grabbedKeyframe) {
            grabbedDelta = timelineDeltaToReplayTickDelta(mouseX - grabbedKeyframeMouseX);

            if (grabbedDelta > 0) {
                ImGuiHelper.drawTooltip("+" + grabbedDelta + " ticks");
            } else if (grabbedDelta < 0) {
                ImGuiHelper.drawTooltip(grabbedDelta + " ticks");
            }
        }

        for (int trackIndex = 0; trackIndex < editorState.keyframeTracks.size(); trackIndex++) {
            KeyframeTrack keyframeTrack = editorState.keyframeTracks.get(trackIndex);

            TreeMap<Integer, Keyframe<?>> keyframeTimes = keyframeTrack.keyframesByTick;

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

                Keyframe<?> keyframe = entry.getValue();

                if (selectedKeyframesForTrack != null && selectedKeyframesForTrack.keyframeTicks().contains(tick)) {
                    int newTick = tick + grabbedDelta;
                    newTick = Math.max(0, Math.min(totalTicks, newTick));

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
            TreeMap<Integer, Keyframe<?>> keyframeTimes = keyframeTrack.keyframesByTick;

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
            drawList.addRectFilled(buttonX + 2, buttonY + buttonSize/2 - 1, buttonX + buttonSize - 2, buttonY + buttonSize/2 + 1, -1);
            drawList.addRectFilled(buttonX + buttonSize/2 - 1, buttonY + 2, buttonX + buttonSize/2 + 1, buttonY + buttonSize - 2, -1);
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
            editorState.keyframeTracks.remove(keyframeTrackToClear);
            editorState.dirty = true;
            selectedKeyframesList.clear();
        }

        ImGui.setCursorPosX(8);
        if (editorState.keyframeTracks.size() < KeyframeType.KEYFRAME_TYPES.length && ImGui.smallButton("Add Element")) {
            ImGui.openPopup("##AddKeyframeElement");
        }
        if (ImGui.beginPopup("##AddKeyframeElement")) {
            for (KeyframeType keyframeType : KeyframeType.KEYFRAME_TYPES) {
                if (ImGui.selectable(keyframeType.name)) {
                    editorState.keyframeTracks.add(new KeyframeTrack(keyframeType));
                    editorState.dirty = true;
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
