package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

public class CameraKeyframeType implements KeyframeType<CameraKeyframe> {

    public static CameraKeyframeType INSTANCE = new CameraKeyframeType();

    private CameraKeyframeType() {
    }

    @Override
    public String name() {
        return "Camera";
    }

    @Override
    public String id() {
        return "CAMERA";
    }

    @Override
    public @Nullable CameraKeyframe createDirect() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            return new CameraKeyframe(player);
        }
        return null;
    }

    @Override
    public KeyframeCreatePopup<CameraKeyframe> createPopup() {
        return null;
    }
}
