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

    @Nullable Class<? extends KeyframeChange> keyframeChangeType();
    default boolean supportsHandler(KeyframeHandler handler) {
        var clazz = this.keyframeChangeType();
        return clazz != null && handler.supportsKeyframeChange(clazz);
    }

    default boolean allowChangingInterpolationType() {
        return true;
    }
    default boolean allowChangingTimelineTick() {
        return true;
    }
    default boolean neverApplyLastKeyframe() {
        return false;
    }
    default boolean canBeCreatedNormally() {
        return true;
    }

    interface KeyframeCreatePopup<T extends Keyframe> {
        @Nullable T render();
    }

}
