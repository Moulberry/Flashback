package com.moulberry.flashback.keyframe;

import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import org.jetbrains.annotations.Nullable;

public interface KeyframeType<T extends Keyframe> {

    default @Nullable String icon() {
        return null;
    }
    String name();
    String id();
    @Nullable T createDirect();
    @Nullable KeyframeCreatePopup<T> createPopup();

    Class<? extends KeyframeChange> keyframeChangeType();
    default boolean supportsHandler(KeyframeHandler handler) {
        return handler.supportsKeyframeChange(this.keyframeChangeType());
    }

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
