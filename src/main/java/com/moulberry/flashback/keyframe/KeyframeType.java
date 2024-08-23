package com.moulberry.flashback.keyframe;

public enum KeyframeType {

    CAMERA("Camera"),
    CAMERA_ORBIT("Camera Orbit"),
    FOV("FOV"),
    SPEED("Speed"),
    TIMELAPSE("Timelapse"),
    TIME_OF_DAY("Time of day"),
    CAMERA_SHAKE("Camera Shake");

    public static final KeyframeType[] KEYFRAME_TYPES = values();

    public final String name;

    KeyframeType(String name) {
        this.name = name;
    }

}
