package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.Utils;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPosition;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTickrate;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.impl.TimelapseKeyframe;
import imgui.ImGui;
import imgui.type.ImString;
import org.jetbrains.annotations.Nullable;

public class TimelapseKeyframeType implements KeyframeType<TimelapseKeyframe> {

    public static TimelapseKeyframeType INSTANCE = new TimelapseKeyframeType();

    private TimelapseKeyframeType() {
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangeTickrate.class;
    }

    @Override
    public @Nullable String icon() {
        return "\ue422";
    }

    @Override
    public String name() {
        return "Timelapse";
    }

    @Override
    public String id() {
        return "TIMELAPSE";
    }

    @Override
    public boolean allowChangingInterpolationType() {
        return false;
    }

    @Override
    public boolean allowChangingTimelineTick() {
        return false;
    }

    @Override
    public boolean neverApplyLastKeyframe() {
        return true;
    }

    @Override
    public @Nullable TimelapseKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<TimelapseKeyframe> createPopup() {
        ImString timelapseKeyframeInput = ImGuiHelper.createResizableImString("1s");
        timelapseKeyframeInput.inputData.allowedChars = "0123456789tsmh.";

        return () -> {
            ImGui.inputText("Time", timelapseKeyframeInput);
            if (ImGui.button("Add")) {
                return new TimelapseKeyframe(Utils.stringToTime(ImGuiHelper.getString(timelapseKeyframeInput)));
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
