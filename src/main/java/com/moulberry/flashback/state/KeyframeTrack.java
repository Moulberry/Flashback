package com.moulberry.flashback.state;

import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.keyframe.handler.ReplayServerKeyframeHandler;
import com.moulberry.flashback.keyframe.impl.TimelapseKeyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.interpolation.SidedInterpolationType;
import com.moulberry.flashback.keyframe.types.TimelapseKeyframeType;

import java.util.Map;
import java.util.TreeMap;

public class KeyframeTrack {

    public final KeyframeType<?> keyframeType;
    public TreeMap<Integer, Keyframe> keyframesByTick = new TreeMap<>();
    public boolean enabled = true;

    public KeyframeTrack(KeyframeType<?> keyframeType) {
        this.keyframeType = keyframeType;
    }

    public boolean tryApplyKeyframes(KeyframeHandler t, float tick) {
        if (!t.supportedKeyframes().contains(this.keyframeType)) {
            return false;
        }

        if (this.keyframeType == TimelapseKeyframeType.INSTANCE) {
            return this.tryApplyKeyframesTimelapse(t, tick);
        }

        // Skip if empty
        TreeMap<Integer, Keyframe> keyframeTimes = this.keyframesByTick;
        if (keyframeTimes.isEmpty()) {
            return false;
        }

        Map.Entry<Integer, Keyframe> lowerEntry = keyframeTimes.floorEntry((int) tick);
        if (lowerEntry == null) {
            return false;
        }

        Keyframe lowerKeyframe = lowerEntry.getValue();

        if (tick == lowerEntry.getKey()) {
            lowerKeyframe.apply(t);
            return true;
        }

        SidedInterpolationType leftInterpolation = lowerEntry.getValue().interpolationType().rightSide;

        // Immediately apply hold
        if (leftInterpolation == SidedInterpolationType.HOLD) {
            lowerKeyframe.apply(t);
            return true;
        }

        // Get next entry, skip if tick is not between two keyframes
        Map.Entry<Integer, Keyframe> ceilEntry = keyframeTimes.ceilingEntry(lowerEntry.getKey() + 1);
        if (ceilEntry == null) {
            if ((int) tick == lowerEntry.getKey()) {
                lowerKeyframe.apply(t);
                return true;
            } else if (t.alwaysApplyLastKeyframe()) {
                lowerKeyframe.apply(t);
            }
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
            Map.Entry<Integer, Keyframe> beforeEntry = keyframeTimes.floorEntry(lowerEntry.getKey() - 1);
            if (beforeEntry == null || beforeEntry.getValue().interpolationType() == InterpolationType.HOLD) { // don't include the right-side of the hold keyframe
                beforeEntry = lowerEntry;
            }

            Map.Entry<Integer, Keyframe> afterAfterEntry = keyframeTimes.ceilingEntry(ceilEntry.getKey() + 1);
            if (afterAfterEntry == null || ceilEntry.getValue().interpolationType() == InterpolationType.HOLD) { // ceil is to the left of afterAfter
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

            beforeEntry.getValue().applyInterpolatedSmooth(t, lowerEntry.getValue(), ceilEntry.getValue(), afterAfterEntry.getValue(),
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

    private boolean tryApplyKeyframesTimelapse(KeyframeHandler t, float tick) {
        // Skip if empty
        TreeMap<Integer, Keyframe> keyframeTimes = this.keyframesByTick;
        if (keyframeTimes.isEmpty()) {
            return false;
        }

        Map.Entry<Integer, Keyframe> lowerEntry = keyframeTimes.floorEntry((int) tick);
        Map.Entry<Integer, Keyframe> ceilEntry = keyframeTimes.ceilingEntry(((int) tick) + 1);

        if (ceilEntry == null && lowerEntry != null && lowerEntry.getKey() == (int) tick) {
            ceilEntry = lowerEntry;
            lowerEntry = keyframeTimes.floorEntry((int) tick - 1);
        }

        if (lowerEntry != null && ceilEntry != null) {
            int lowerTicks = ((TimelapseKeyframe) lowerEntry.getValue()).ticks;
            int ceilTicks = ((TimelapseKeyframe) ceilEntry.getValue()).ticks;

            if (ceilTicks <= lowerTicks) {
                ReplayUI.setInfoOverlayShort("Unable to timelapse. Right keyframe's time must be greater than left keyframe's time");
                t.applyTickrate(20);
            } else {
                double tickrate = (double) (ceilEntry.getKey() - lowerEntry.getKey()) / (ceilTicks - lowerTicks) * 20;
                t.applyTickrate((float) tickrate);
            }

            return true;
        }

        return false;
    }

}
