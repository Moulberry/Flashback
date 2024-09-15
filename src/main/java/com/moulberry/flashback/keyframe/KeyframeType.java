package com.moulberry.flashback.keyframe;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.state.KeyframeTrack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface KeyframeType<T extends Keyframe> {

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
