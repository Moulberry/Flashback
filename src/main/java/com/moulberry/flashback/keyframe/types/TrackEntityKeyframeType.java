package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.combo_options.TrackingBodyPart;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTrackEntity;
import com.moulberry.flashback.keyframe.impl.TrackEntityKeyframe;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

public class TrackEntityKeyframeType implements KeyframeType<TrackEntityKeyframe> {

    public static TrackEntityKeyframeType INSTANCE = new TrackEntityKeyframeType();

    private TrackEntityKeyframeType() {
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangeTrackEntity.class;
    }

    @Override
    public @Nullable String icon() {
        return "\ue55f";
    }

    @Override
    public String name() {
        return "Track Entity";
    }

    @Override
    public String id() {
        return "TRACK_ENTITY";
    }

    @Override
    public @Nullable TrackEntityKeyframe createDirect() {
        return null;
    }

    private static class CameraTrackTargetData {
        float[] positionOffset = new float[]{0.0f, 0.0f, 0.0f};
        float[] viewOffset = new float[]{0.0f, 0.0f, 3.0f};
        float[] cameraTrackYaw = new float[]{0.0f};
        float[] cameraTrackPitch = new float[]{0.0f};
        TrackingBodyPart trackingBodyPart = TrackingBodyPart.HEAD;
        ImString cameraTrackTarget = new ImString();
        UUID validEntityTarget = null;
    }

    @Override
    public KeyframeCreatePopup<TrackEntityKeyframe> createPopup() {
        CameraTrackTargetData data = new CameraTrackTargetData();

        return () -> {
            if (ImGui.inputText("Entity UUID", data.cameraTrackTarget)) {
                data.validEntityTarget = null;
                try {
                    String uuidStr = ImGuiHelper.getString(data.cameraTrackTarget);
                    UUID uuid = UUID.fromString(uuidStr);

                    ClientLevel level = Minecraft.getInstance().level;
                    if (level != null) {
                        Entity entity = level.getEntities().get(uuid);
                        if (entity != null && entity != Minecraft.getInstance().player) {
                            data.validEntityTarget = uuid;
                        }
                    }
                } catch (Exception ignored) {}
            }
            data.trackingBodyPart = ImGuiHelper.enumCombo("Body Part", data.trackingBodyPart);
            ImGuiHelper.inputFloat("Yaw Offset", data.cameraTrackYaw);
            ImGuiHelper.inputFloat("Pitch Offset", data.cameraTrackPitch);
            ImGuiHelper.inputFloat("Position Offset", data.positionOffset);
            ImGuiHelper.inputFloat("View Offset", data.viewOffset);

            if (data.validEntityTarget == null) ImGui.beginDisabled();
            if (ImGui.button("Add")) {
                Vector3d positionOffset = new Vector3d(data.positionOffset[0], data.positionOffset[1], data.positionOffset[2]);
                Vector3d viewOffset = new Vector3d(data.viewOffset[0], data.viewOffset[1], data.viewOffset[2]);
                return new TrackEntityKeyframe(data.validEntityTarget, data.trackingBodyPart, data.cameraTrackYaw[0],
                    data.cameraTrackPitch[0], positionOffset, viewOffset);
            }
            if (data.validEntityTarget == null) ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
