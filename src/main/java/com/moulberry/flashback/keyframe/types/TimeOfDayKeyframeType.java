package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.impl.TickrateKeyframe;
import com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;
import imgui.type.ImInt;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

public class TimeOfDayKeyframeType implements KeyframeType<TimeOfDayKeyframe> {

    public static TimeOfDayKeyframeType INSTANCE = new TimeOfDayKeyframeType();

    private TimeOfDayKeyframeType() {
    }

    @Override
    public String name() {
        return "Time of day";
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
        ImInt timeOfDayKeyframeInput = new ImInt();
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideTimeOfDay >= 0) {
            timeOfDayKeyframeInput.set((int) editorState.replayVisuals.overrideTimeOfDay);
        } else {
            timeOfDayKeyframeInput.set((int)(Minecraft.getInstance().level.getDayTime() % 24000));
        }

        return () -> {
            ImGui.inputInt("Time", timeOfDayKeyframeInput, 0);
            if (ImGui.button("Add")) {
                return new TimeOfDayKeyframe(timeOfDayKeyframeInput.get());
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
