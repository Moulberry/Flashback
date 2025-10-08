package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPosition;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public class CameraKeyframeType implements KeyframeType<CameraKeyframe> {

    public static CameraKeyframeType INSTANCE = new CameraKeyframeType();

    private CameraKeyframeType() {
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangeCameraPosition.class;
    }

    @Override
    public @Nullable String icon() {
        return "\ue04b";
    }

    @Override
    public String name() {
        return I18n.get("flashback.keyframe.camera");
    }

    @Override
    public String id() {
        return "CAMERA";
    }

    @Override
    public @Nullable CameraKeyframe createDirect() {
        Entity entity = Minecraft.getInstance().getCameraEntity();
        if (entity != null) {
            return new CameraKeyframe(entity);
        }
        entity = Minecraft.getInstance().player;
        if (entity != null) {
            return new CameraKeyframe(entity);
        }
        return null;
    }

    @Override
    public KeyframeCreatePopup<CameraKeyframe> createPopup() {
        return null;
    }
}
