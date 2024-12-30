package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPosition;
import com.moulberry.flashback.keyframe.change.KeyframeChangeFov;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;
import imgui.type.ImFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class FOVKeyframeType implements KeyframeType<FOVKeyframe> {

    public static FOVKeyframeType INSTANCE = new FOVKeyframeType();

    private FOVKeyframeType() {
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangeFov.class;
    }

    @Override
    public @Nullable String icon() {
        return "\ue3af";
    }

    @Override
    public String name() {
        return "FOV";
    }

    @Override
    public String id() {
        return "FOV";
    }

    @Override
    public @Nullable FOVKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<FOVKeyframe> createPopup() {
        float[] fovKeyframeInput = new float[]{Minecraft.getInstance().options.fov().get()};
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideFov) {
            fovKeyframeInput[0] = editorState.replayVisuals.overrideFovAmount;
        }

        return () -> {
            ImGui.sliderFloat("FOV", fovKeyframeInput, 1f, 110f);
            if (ImGui.button("Add")) {
                return new FOVKeyframe(fovKeyframeInput[0]);
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
