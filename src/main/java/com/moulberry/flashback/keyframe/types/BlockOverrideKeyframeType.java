package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeFreeze;
import com.moulberry.flashback.keyframe.impl.BlockOverrideKeyframe;
import com.moulberry.flashback.keyframe.impl.FreezeKeyframe;
import imgui.flashback.ImGui;
import imgui.flashback.type.ImBoolean;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

public class BlockOverrideKeyframeType implements KeyframeType<BlockOverrideKeyframe> {

    public static BlockOverrideKeyframeType INSTANCE = new BlockOverrideKeyframeType();

    private BlockOverrideKeyframeType() {
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return null;
    }

    @Override
    public @Nullable String icon() {
        return "\uea44";
    }

    @Override
    public String name() {
        return I18n.get("flashback.keyframe.block_override");
    }

    @Override
    public String id() {
        return "BLOCK_OVERRIDE";
    }

    @Override
    public boolean allowChangingInterpolationType() {
        return false;
    }

    @Override
    public boolean canBeCreatedNormally() {
        return false;
    }

    @Override
    public @Nullable BlockOverrideKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<BlockOverrideKeyframe> createPopup() {
        return null;
    }
}
