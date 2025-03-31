package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPosition;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPositionOrbit;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import imgui.ImGui;
import imgui.type.ImFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class CameraOrbitKeyframeType implements KeyframeType<CameraOrbitKeyframe> {

    public static CameraOrbitKeyframeType INSTANCE = new CameraOrbitKeyframeType();

    private CameraOrbitKeyframeType() {
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangeCameraPositionOrbit.class;
    }

    @Override
    public @Nullable String icon() {
        return "\ue577";
    }

    @Override
    public String name() {
        return "Camera Orbit";
    }

    @Override
    public String id() {
        return "CAMERA_ORBIT";
    }

    @Override
    public @Nullable CameraOrbitKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<CameraOrbitKeyframe> createPopup() {
       float[] cameraOrbitCenter = new float[]{0.0f, 0.0f, 0.0f};
       float[] cameraOrbitDistance = new float[]{8.0f};
       float[] cameraOrbitYaw = new float[]{0.0f};
       float[] cameraOrbitPitch = new float[]{0.0f};

       Entity entity = Minecraft.getInstance().cameraEntity;
       if (entity != null) {
           Vec3 eyePosition = entity.getEyePosition();
           cameraOrbitCenter[0] = (float) eyePosition.x;
           cameraOrbitCenter[1] = (float) eyePosition.y;
           cameraOrbitCenter[2] = (float) eyePosition.z;
       }

        return () -> {
            ImGuiHelper.inputFloat("Position", cameraOrbitCenter);
            ImGuiHelper.inputFloat("Distance", cameraOrbitDistance);
            ImGuiHelper.inputFloat("Yaw", cameraOrbitYaw);
            ImGuiHelper.inputFloat("Pitch", cameraOrbitPitch);

            if (ImGui.button("Add")) {
                Vector3d center = new Vector3d(cameraOrbitCenter[0], cameraOrbitCenter[1], cameraOrbitCenter[2]);
                return new CameraOrbitKeyframe(center, cameraOrbitDistance[0], cameraOrbitYaw[0], cameraOrbitPitch[0]);
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
