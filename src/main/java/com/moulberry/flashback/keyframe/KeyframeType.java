package com.moulberry.flashback.keyframe;

import org.jetbrains.annotations.Nullable;

public interface KeyframeType<T extends Keyframe> {

    default @Nullable String icon() {
        return null;
    }
    String name();
    String id();
    @Nullable T createDirect();
    @Nullable KeyframeCreatePopup<T> createPopup();

    default boolean allowChangingInterpolationType() {
        return true;
    }
    default boolean allowChangingTimelineTick() {
        return true;
    }

    interface KeyframeCreatePopup<T extends Keyframe> {
        @Nullable T render();
    }

}
