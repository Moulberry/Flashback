package com.moulberry.flashback.state;

import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTickrate;
import com.moulberry.flashback.keyframe.impl.TimelapseKeyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.interpolation.SidedInterpolationType;
import com.moulberry.flashback.keyframe.types.TimelapseKeyframeType;
import imgui.type.ImString;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

public class KeyframeTrack {

    public final KeyframeType<?> keyframeType;
    public TreeMap<Integer, Keyframe> keyframesByTick = new TreeMap<>();
    public boolean enabled = true;
    public String customName = null;
    public int customColour = 0;

    public transient ImString nameEditField = null;
    public transient boolean forceFocusTrack = false;
    public transient float animatedOffsetInUi = 0.0f;

    public KeyframeTrack(KeyframeType<?> keyframeType) {
        this.keyframeType = keyframeType;
    }

    @Nullable
    public KeyframeChange createKeyframeChange(float tick) {
        if (this.keyframeType == TimelapseKeyframeType.INSTANCE) {
            return this.tryApplyKeyframesTimelapse(tick);
        }

        // Skip if empty
        TreeMap<Integer, Keyframe> keyframeTimes = this.keyframesByTick;
        if (keyframeTimes.isEmpty()) {
            return null;
        }

        Map.Entry<Integer, Keyframe> lowerEntry = keyframeTimes.floorEntry((int) tick);
        if (lowerEntry == null) {
            return null;
        }

        Keyframe lowerKeyframe = lowerEntry.getValue();

        if (tick == lowerEntry.getKey()) {
            return lowerKeyframe.createChange();
        }

        SidedInterpolationType leftInterpolation = lowerEntry.getValue().interpolationType().rightSide;

        // Immediately apply hold
        if (leftInterpolation == SidedInterpolationType.HOLD) {
            return lowerKeyframe.createChange();
        }

        // Get next entry, skip if tick is not between two keyframes
        Map.Entry<Integer, Keyframe> ceilEntry = keyframeTimes.ceilingEntry(lowerEntry.getKey() + 1);
        if (ceilEntry == null) {
            if ((int) tick == lowerEntry.getKey()) {
                return lowerKeyframe.createChange();
            }
            return null;
        }

        SidedInterpolationType rightInterpolation = ceilEntry.getValue().interpolationType().leftSide;
        if (rightInterpolation == SidedInterpolationType.HOLD) {
            rightInterpolation = leftInterpolation;
        }

        float amount = (tick - lowerEntry.getKey()) / (ceilEntry.getKey() - lowerEntry.getKey());

        KeyframeChange leftChange = null;
        KeyframeChange rightChange = null;

        if (leftInterpolation == SidedInterpolationType.SMOOTH ||
                rightInterpolation == SidedInterpolationType.SMOOTH) {
            Map.Entry<Integer, Keyframe> beforeEntry = keyframeTimes.floorEntry(lowerEntry.getKey() - 1);
            if (beforeEntry == null || beforeEntry.getValue().interpolationType() == InterpolationType.HOLD) { // don't include the right-side of the hold keyframe
                beforeEntry = lowerEntry;
            }

            Map.Entry<Integer, Keyframe> afterAfterEntry = keyframeTimes.ceilingEntry(ceilEntry.getKey() + 1);
            if (afterAfterEntry == null || ceilEntry.getValue().interpolationType() == InterpolationType.HOLD) { // ceil is to the left of afterAfter
                afterAfterEntry = ceilEntry;
            }

            KeyframeChange smoothChange = beforeEntry.getValue().createSmoothInterpolatedChange(lowerEntry.getValue(), ceilEntry.getValue(), afterAfterEntry.getValue(),
                    beforeEntry.getKey(), lowerEntry.getKey(), ceilEntry.getKey(), afterAfterEntry.getKey(), amount);

            if (leftInterpolation == SidedInterpolationType.SMOOTH) {
                leftChange = smoothChange;
            }
            if (rightInterpolation == SidedInterpolationType.SMOOTH) {
                rightChange = smoothChange;
            }
        }
        if (leftInterpolation == SidedInterpolationType.HERMITE ||
                rightInterpolation == SidedInterpolationType.HERMITE) {
            Integer minKey = lowerEntry.getKey();

            while (minKey != null) {
                Map.Entry<Integer, Keyframe> before = keyframeTimes.floorEntry(minKey - 1);

                if (before == null) {
                    minKey = null;
                    break;
                } else if (before.getValue().interpolationType() == InterpolationType.HOLD) {
                    // don't include the right-side of the hold keyframe, or anything before it
                    break;
                }

                minKey = before.getKey();
            }

            Integer maxKey = ceilEntry.getKey();

            while (maxKey != null) {
                Map.Entry<Integer, Keyframe> after = keyframeTimes.ceilingEntry(maxKey + 1);

                if (after == null) {
                    maxKey = null;
                    break;
                } else if (after.getValue().interpolationType() == InterpolationType.HOLD) {
                    // include the hold keyframe, but not anything after it
                    maxKey = after.getKey();
                    break;
                }

                maxKey = after.getKey();
            }

            Map<Integer, Keyframe> subMap;

            if (minKey != null) {
                if (maxKey != null) {
                    subMap = Maps.subMap(this.keyframesByTick, Range.closed(minKey, maxKey));
                } else {
                    subMap = Maps.subMap(this.keyframesByTick, Range.atLeast(minKey));
                }
            } else if (maxKey != null) {
                subMap = Maps.subMap(this.keyframesByTick, Range.atMost(maxKey));
            } else {
                subMap = this.keyframesByTick;
            }

            KeyframeChange hermiteChange = lowerKeyframe.createHermiteInterpolatedChange(subMap, tick);
            if (leftInterpolation == SidedInterpolationType.HERMITE) {
                leftChange = hermiteChange;
            }
            if (rightInterpolation == SidedInterpolationType.HERMITE) {
                rightChange = hermiteChange;
            }
        }
        if (leftChange == null || rightChange == null) {
            double adjustedAmount = SidedInterpolationType.interpolate(leftInterpolation, rightInterpolation, amount);

            KeyframeChange keyframeChange = lowerKeyframe.createChange();

            if (adjustedAmount != 0.0) {
                KeyframeChange keyframeChangeCeil = ceilEntry.getValue().createChange();
                keyframeChange = KeyframeChange.interpolateSafe(keyframeChange, keyframeChangeCeil, (float) adjustedAmount);
            }

            if (leftChange == null) {
                leftChange = keyframeChange;
            }
            if (rightChange == null) {
                rightChange = keyframeChange;
            }
        }

        return KeyframeChange.interpolateSafe(leftChange, rightChange, amount);
    }

    @Nullable
    private KeyframeChangeTickrate tryApplyKeyframesTimelapse(float tick) {
        // Skip if empty
        TreeMap<Integer, Keyframe> keyframeTimes = this.keyframesByTick;
        if (keyframeTimes.isEmpty()) {
            return null;
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
                return null;
            } else {
                double tickrate = (double) (ceilEntry.getKey() - lowerEntry.getKey()) / (ceilTicks - lowerTicks) * 20;
                return new KeyframeChangeTickrate((float) tickrate);
            }
        }

        return null;
    }

}
