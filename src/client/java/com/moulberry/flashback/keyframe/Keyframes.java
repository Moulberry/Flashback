package com.moulberry.flashback.keyframe;

import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.interpolation.SidedInterpolationType;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class Keyframes {

    public static final EnumMap<KeyframeType, TreeMap<Integer, Keyframe<?>>> keyframes = new EnumMap<>(KeyframeType.class);

    @SuppressWarnings("unchecked")
    public static <T> void applyKeyframes(T t, float tick) {
        for (Map.Entry<KeyframeType, TreeMap<Integer, Keyframe<?>>> entry : Keyframes.keyframes.entrySet()) {
            TreeMap<Integer, Keyframe<?>> keyframeTimes = entry.getValue();
            if (keyframeTimes.isEmpty()) {
                continue;
            }

            Map.Entry<Integer, Keyframe<?>> lowerEntry = keyframeTimes.floorEntry((int) tick);
            if (lowerEntry == null) {
                continue;
            }

            if (lowerEntry.getValue().operandClass != t.getClass()) {
                continue;
            }
            Keyframe<T> lowerKeyframe = (Keyframe<T>) lowerEntry.getValue();

            SidedInterpolationType leftInterpolation = lowerEntry.getValue().interpolationType().rightSide;
            if (leftInterpolation == SidedInterpolationType.HOLD) {
                lowerKeyframe.apply(t);
                continue;
            }

            Map.Entry<Integer, Keyframe<?>> ceilEntry = keyframeTimes.ceilingEntry((int) tick);
            if (ceilEntry == null) {
                continue;
            }

            if (Objects.equals(lowerEntry.getKey(), ceilEntry.getKey())) {
                lowerKeyframe.apply(t);
                continue;
            }

            float amount = (tick - lowerEntry.getKey()) / (ceilEntry.getKey() - lowerEntry.getKey());

            SidedInterpolationType rightInterpolation = ceilEntry.getValue().interpolationType().leftSide;
            if (rightInterpolation == SidedInterpolationType.HOLD) {
                rightInterpolation = leftInterpolation;
            }

            boolean isUsingSmooth = leftInterpolation == SidedInterpolationType.SMOOTH ||
                rightInterpolation == SidedInterpolationType.SMOOTH;

            if (isUsingSmooth) {
                Map.Entry<Integer, Keyframe<?>> beforeEntry = keyframeTimes.floorEntry(lowerEntry.getKey() - 1);
                if (beforeEntry == null) {
                    beforeEntry = lowerEntry;
                }

                Map.Entry<Integer, Keyframe<?>> afterAfterEntry = keyframeTimes.ceilingEntry(ceilEntry.getKey() + 1);
                if (afterAfterEntry == null) {
                    afterAfterEntry = ceilEntry;
                }

                float lerpAmount = -1f;
                boolean lerpFromRight = false;
                if (leftInterpolation != SidedInterpolationType.SMOOTH) {
                    lerpAmount = SidedInterpolationType.interpolate(leftInterpolation, leftInterpolation, amount);
                    lerpFromRight = false;
                } else if (rightInterpolation != SidedInterpolationType.SMOOTH) {
                    lerpAmount = SidedInterpolationType.interpolate(rightInterpolation, rightInterpolation, amount);
                    lerpFromRight = true;
                }

                ((Keyframe)beforeEntry.getValue()).applyInterpolatedSmooth(t, lowerEntry.getValue(), ceilEntry.getValue(), afterAfterEntry.getValue(),
                    beforeEntry.getKey(), lowerEntry.getKey(), ceilEntry.getKey(), afterAfterEntry.getKey(), amount, lerpAmount, lerpFromRight);
            } else {
                amount = SidedInterpolationType.interpolate(leftInterpolation, rightInterpolation, amount);
                if (amount == 0.0) {
                    lowerKeyframe.apply(t);
                } else {
                    lowerKeyframe.applyInterpolated(t, ceilEntry.getValue(), amount);
                }
            }

        }
    }

}
