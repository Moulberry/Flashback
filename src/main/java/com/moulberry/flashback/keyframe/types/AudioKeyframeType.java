package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.Utils;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTickrate;
import com.moulberry.flashback.keyframe.impl.TimelapseKeyframe;
import imgui.ImGui;
import imgui.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

public class AudioKeyframeType implements KeyframeType<TimelapseKeyframe> {

    public static AudioKeyframeType INSTANCE = new AudioKeyframeType();

    private AudioKeyframeType() {
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangeTickrate.class;
    }

    @Override
    public @Nullable String icon() {
        return "\ue422"; // todo nobuild: icon
    }

    @Override
    public String name() {
        return I18n.get("flashback.keyframe.audio");
    }

    @Override
    public String id() {
        return "AUDIO";
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
    public @Nullable TimelapseKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<TimelapseKeyframe> createPopup() {
        AsyncFileDialogs.openFileDialog(FabricLoader.getInstance().get)
        ImString timelapseKeyframeInput = ImGuiHelper.createResizableImString("1s");
        timelapseKeyframeInput.inputData.allowedChars = "0123456789tsmh.";

        return () -> {
            ImGui.inputText(I18n.get("flashback.time"), timelapseKeyframeInput);
            if (ImGui.button(I18n.get("flashback.add"))) {
                return new TimelapseKeyframe(Utils.stringToTime(ImGuiHelper.getString(timelapseKeyframeInput)));
            }
            ImGui.sameLine();
            if (ImGui.button(I18n.get("gui.cancel"))) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
