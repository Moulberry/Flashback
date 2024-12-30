package com.moulberry.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import net.minecraft.util.Mth;
import org.apache.commons.math3.analysis.interpolation.HermiteInterpolator;
import org.joml.Math;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Map;

public class Interpolation {

    public static double linear(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    public static float linear(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    public static float linearAngle(float from, float to, float amount) {
        float delta = Mth.wrapDegrees(to - from);
        return from + Math.max(-180, Math.min(180, delta * amount));
    }

    public static double linearAngle(double from, double to, double amount) {
        double delta = Mth.wrapDegrees(to - from);
        return from + Math.max(-180, Math.min(180, delta * amount));
    }

}
