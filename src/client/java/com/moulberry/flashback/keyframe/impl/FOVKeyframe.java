package com.moulberry.flashback.keyframe.impl;

import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.ReplayVisuals;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.spline.CatmullRom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class FOVKeyframe extends Keyframe<Minecraft> {

    private final int fov;

    public FOVKeyframe(int fov) {
        super(Minecraft.class);
        this.fov = fov;
    }

    @Override
    public void apply(Minecraft minecraft) {
        minecraft.options.fov().set(this.fov);
    }

    @Override
    public void applyInterpolated(Minecraft minecraft, Keyframe otherGeneric, float amount) {
        if (!(otherGeneric instanceof FOVKeyframe other)) {
            this.apply(minecraft);
            return;
        }

        float fov = Interpolation.linear(this.fov, other.fov, amount);
        minecraft.options.fov().set(Math.round(fov));
        ReplayVisuals.overrideFov = fov;
    }

    @Override
    public void applyInterpolatedSmooth(Minecraft minecraft, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount, float lerpAmount, boolean lerpFromRight) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        float fov = CatmullRom.value(this.fov,
            ((FOVKeyframe)p1).fov, ((FOVKeyframe)p2).fov,
            ((FOVKeyframe)p3).fov, time1, time2, time3, amount);

        if (lerpAmount >= 0) {
            float linearFov = Interpolation.linear(((FOVKeyframe)p1).fov, ((FOVKeyframe)p2).fov, lerpAmount);

            if (lerpFromRight) {
                fov = Interpolation.linear(fov, linearFov, amount);
            } else {
                fov = Interpolation.linear(linearFov, fov, amount);
            }
        }

        minecraft.options.fov().set(Math.round(fov));
        ReplayVisuals.overrideFov = fov;
    }

}
