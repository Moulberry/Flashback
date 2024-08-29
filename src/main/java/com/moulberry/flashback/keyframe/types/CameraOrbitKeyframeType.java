package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
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
       ImFloat cameraOrbitDistance = new ImFloat(8.0f);
       ImFloat cameraOrbitYaw = new ImFloat(0.0f);
       ImFloat cameraOrbitPitch = new ImFloat(0.0f);

       Entity entity = Minecraft.getInstance().cameraEntity;
       if (entity != null) {
           Vec3 eyePosition = entity.getEyePosition();
           cameraOrbitCenter[0] = (float) eyePosition.x;
           cameraOrbitCenter[1] = (float) eyePosition.y;
           cameraOrbitCenter[2] = (float) eyePosition.z;
       }

        return () -> {
            ImGui.inputFloat3("Position", cameraOrbitCenter);
            ImGui.inputFloat("Distance", cameraOrbitDistance);
            ImGui.inputFloat("Yaw", cameraOrbitYaw);
            ImGui.inputFloat("Pitch", cameraOrbitPitch);

            if (ImGui.button("Add")) {
                Vector3d center = new Vector3d(cameraOrbitCenter[0], cameraOrbitCenter[1], cameraOrbitCenter[2]);
                return new CameraOrbitKeyframe(center, cameraOrbitDistance.get(), cameraOrbitYaw.get(), cameraOrbitPitch.get());
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
