package com.moulberry.flashback.state;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.interpolation.SidedInterpolationType;

import java.util.Map;
import java.util.TreeMap;

public class KeyframeTrack {

    public final KeyframeType keyframeType;
    public TreeMap<Integer, Keyframe<?>> keyframesByTick = new TreeMap<>();
    public boolean enabled = true;

    public KeyframeTrack(KeyframeType keyframeType) {
        this.keyframeType = keyframeType;
    }

    @SuppressWarnings("unchecked")
    public <T> boolean tryApplyKeyframes(T t, float tick) {
        // Skip if empty
        TreeMap<Integer, Keyframe<?>> keyframeTimes = this.keyframesByTick;
        if (keyframeTimes.isEmpty()) {
            return false;
        }

        Map.Entry<Integer, Keyframe<?>> lowerEntry = keyframeTimes.floorEntry((int) tick);
        if (lowerEntry == null) {
            return false;
        }

        if (lowerEntry.getValue().operandClass != t.getClass()) {
            return false;
        }

        Keyframe<T> lowerKeyframe = (Keyframe<T>) lowerEntry.getValue();

        SidedInterpolationType leftInterpolation = lowerEntry.getValue().interpolationType().rightSide;

        // Immediately apply hold
        if (leftInterpolation == SidedInterpolationType.HOLD) {
            lowerKeyframe.apply(t);
            return true;
        }

        // Get next entry, skip if tick is not between two keyframes
        Map.Entry<Integer, Keyframe<?>> ceilEntry = keyframeTimes.ceilingEntry(lowerEntry.getKey() + 1);
        if (ceilEntry == null) {
            return false;
        }

        SidedInterpolationType rightInterpolation = ceilEntry.getValue().interpolationType().leftSide;
        if (rightInterpolation == SidedInterpolationType.HOLD) {
            rightInterpolation = leftInterpolation;
        }

        float amount = (tick - lowerEntry.getKey()) / (ceilEntry.getKey() - lowerEntry.getKey());

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

            float lerpAmount;
            boolean lerpFromRight;
            if (leftInterpolation != SidedInterpolationType.SMOOTH) {
                lerpAmount = SidedInterpolationType.interpolate(leftInterpolation, leftInterpolation, amount);
                lerpFromRight = false;
            } else if (rightInterpolation != SidedInterpolationType.SMOOTH) {
                lerpAmount = SidedInterpolationType.interpolate(rightInterpolation, rightInterpolation, amount);
                lerpFromRight = true;
            } else {
                lerpAmount = -1;
                lerpFromRight = false;
            }

            ((Keyframe<T>)beforeEntry.getValue()).applyInterpolatedSmooth(t, lowerEntry.getValue(), ceilEntry.getValue(), afterAfterEntry.getValue(),
                    beforeEntry.getKey(), lowerEntry.getKey(), ceilEntry.getKey(), afterAfterEntry.getKey(), amount, lerpAmount, lerpFromRight);
        } else {
            amount = SidedInterpolationType.interpolate(leftInterpolation, rightInterpolation, amount);

            if (amount == 0.0) {
                lowerKeyframe.apply(t);
            } else {
                lowerKeyframe.applyInterpolated(t, ceilEntry.getValue(), amount);
            }
        }

        return true;
    }

}
