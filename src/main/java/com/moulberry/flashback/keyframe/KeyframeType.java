package com.moulberry.flashback.keyframe;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.state.KeyframeTrack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// todo: WIP DON'T COMMIT
public interface KeyframeType<T extends Keyframe> {

    /*
    CAMERA("Camera"),
    CAMERA_ORBIT("Camera Orbit"),
    FOV("FOV"),
    SPEED("Speed"),
    TIMELAPSE("Timelapse"),
    TIME_OF_DAY("Time of day"),
    CAMERA_SHAKE("Camera Shake");
     */

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
