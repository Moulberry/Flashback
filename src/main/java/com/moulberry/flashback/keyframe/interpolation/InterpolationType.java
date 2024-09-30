package com.moulberry.flashback.keyframe.interpolation;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.ComboOption;
import com.moulberry.flashback.configuration.FlashbackConfig;

public enum InterpolationType implements ComboOption {

    SMOOTH(SidedInterpolationType.SMOOTH, SidedInterpolationType.SMOOTH, "Smooth"),
    LINEAR(SidedInterpolationType.LINEAR, SidedInterpolationType.LINEAR, "Linear"),
    EASE_IN(SidedInterpolationType.EASE, SidedInterpolationType.LINEAR, "Ease In (Linear Out)"),
    EASE_OUT(SidedInterpolationType.LINEAR, SidedInterpolationType.EASE, "Ease Out (Linear In)"),
    EASE_IN_OUT(SidedInterpolationType.EASE, SidedInterpolationType.EASE, "Ease In/Out"),
    HOLD(SidedInterpolationType.HOLD, SidedInterpolationType.HOLD, "Hold");

    public static final InterpolationType[] INTERPOLATION_TYPES = values();

    public static InterpolationType getDefault() {
        FlashbackConfig config = Flashback.getConfig();
        if (config != null && config.defaultInterpolationType != null) {
            return config.defaultInterpolationType;
        }
        return InterpolationType.SMOOTH;
    }

    public static String[] NAMES = new String[] {
        "Smooth",
        "Linear",
        "Ease In (Linear Out)",
        "Ease Out (Linear In)",
        "Ease In/Out",
        "Hold"
    };

    public final SidedInterpolationType leftSide;
    public final SidedInterpolationType rightSide;
    public final String name;

    InterpolationType(SidedInterpolationType leftSide, SidedInterpolationType rightSide, String name) {
        this.leftSide = leftSide;
        this.rightSide = rightSide;
        this.name = name;
    }

    @Override
    public String text() {
        return this.name;
    }
}
