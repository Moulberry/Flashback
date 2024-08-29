package com.moulberry.flashback.keyframe.interpolation;

public enum InterpolationType {

    SMOOTH(SidedInterpolationType.SMOOTH, SidedInterpolationType.SMOOTH),
    LINEAR(SidedInterpolationType.LINEAR, SidedInterpolationType.LINEAR),
    EASE_IN(SidedInterpolationType.EASE, SidedInterpolationType.LINEAR),
    EASE_OUT(SidedInterpolationType.LINEAR, SidedInterpolationType.EASE),
    EASE_IN_OUT(SidedInterpolationType.EASE, SidedInterpolationType.EASE),
    HOLD(SidedInterpolationType.HOLD, SidedInterpolationType.HOLD);

    public static InterpolationType DEFAULT = SMOOTH;

    public static final InterpolationType[] INTERPOLATION_TYPES = values();

    public static String[] NAMES = new String[] {
        "Smooth",
        "Linear",
        "Ease In (Linear Out)",
        "Ease Out (Linear In)",
        "Ease In/Out",
        "Hold"
    };

    public SidedInterpolationType leftSide;
    public SidedInterpolationType rightSide;

    InterpolationType(SidedInterpolationType leftSide, SidedInterpolationType rightSide) {
        this.leftSide = leftSide;
        this.rightSide = rightSide;
    }

}
