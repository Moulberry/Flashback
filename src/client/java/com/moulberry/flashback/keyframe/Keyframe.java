package com.moulberry.flashback.keyframe;

import com.moulberry.flashback.keyframe.interpolation.InterpolationType;

public abstract class Keyframe<T> {

    public final Class<T> operandClass;
    private InterpolationType interpolationType = InterpolationType.SMOOTH;

    public Keyframe(Class<T> operandClass) {
        this.operandClass = operandClass;
    }

    public InterpolationType interpolationType() {
        return interpolationType;
    }

    public void interpolationType(InterpolationType interpolationType) {
        this.interpolationType = interpolationType;
    }

    public abstract void apply(T t);
    public abstract void applyInterpolated(T t, Keyframe other, float amount);
    public abstract void applyInterpolatedSmooth(T t, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount,
        float lerpAmount, boolean lerpFromRight);

}
