package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPosition;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTimeOfDay;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.impl.TickrateKeyframe;
import com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

public class TimeOfDayKeyframeType implements KeyframeType<TimeOfDayKeyframe> {

    public static TimeOfDayKeyframeType INSTANCE = new TimeOfDayKeyframeType();

    private TimeOfDayKeyframeType() {
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangeTimeOfDay.class;
    }

    @Override
    public @Nullable String icon() {
        return "\ue518";
    }

    @Override
    public String name() {
        return I18n.get("flashback.keyframe.time_of_day");
    }

    @Override
    public String id() {
        return "TIME_OF_DAY";
    }

    @Override
    public @Nullable TimeOfDayKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<TimeOfDayKeyframe> createPopup() {
        int[] timeOfDayKeyframeInput = new int[1];
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideTimeOfDay >= 0) {
            timeOfDayKeyframeInput[0] = (int) editorState.replayVisuals.overrideTimeOfDay;
        } else {
            timeOfDayKeyframeInput[0] = (int)(Minecraft.getInstance().level.getDayTime() % 24000);
        }

        return () -> {
            ImGuiHelper.inputInt(I18n.get("flashback.time"), timeOfDayKeyframeInput);
            if (ImGui.button(I18n.get("flashback.add"))) {
                return new TimeOfDayKeyframe(timeOfDayKeyframeInput[0]);
            }
            ImGui.sameLine();
            if (ImGui.button(I18n.get("gui.cancel"))) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
