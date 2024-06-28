package com.moulberry.flashback.keyframe;

public enum KeyframeType {

    CAMERA("Camera"),
    FOV("FOV"),
    SPEED("Speed"),
    TIME_OF_DAY("Time of day");

    public static final KeyframeType[] KEYFRAME_TYPES = values();

    public final String name;

    KeyframeType(String name) {
        this.name = name;
    }

}
