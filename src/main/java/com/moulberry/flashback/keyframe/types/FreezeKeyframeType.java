package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPosition;
import com.moulberry.flashback.keyframe.change.KeyframeChangeFreeze;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.impl.FreezeKeyframe;
import imgui.ImGui;
import imgui.type.ImBoolean;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

public class FreezeKeyframeType implements KeyframeType<FreezeKeyframe> {

    public static FreezeKeyframeType INSTANCE = new FreezeKeyframeType();

    private FreezeKeyframeType() {
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangeFreeze.class;
    }

    @Override
    public @Nullable String icon() {
        return "\ueb3b";
    }

    @Override
    public String name() {
        return I18n.get("flashback.keyframe.freeze");
    }

    @Override
    public String id() {
        return "FREEZE";
    }

    @Override
    public @Nullable FreezeKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<FreezeKeyframe> createPopup() {
        ImBoolean frozen = new ImBoolean();
        int[] delay = new int[]{0};

        return () -> {
            ImGui.checkbox(I18n.get("flashback.frozen"), frozen);
            ImGui.sliderInt(I18n.get("flashback.delay"), delay, 0, 10);
            ImGuiHelper.tooltip(I18n.get("flashback.freeze_delay_tooltip"));

            delay[0] = Math.max(0, Math.min(10, delay[0]));

            if (ImGui.button(I18n.get("flashback.add"))) {
                return new FreezeKeyframe(frozen.get(), delay[0]);
            }
            ImGui.sameLine();
            if (ImGui.button(I18n.get("gui.cancel"))) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
