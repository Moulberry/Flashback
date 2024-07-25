package com.moulberry.flashback;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.impl.TickrateKeyframe;
import com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe;
import com.moulberry.flashback.serialization.QuaternionTypeAdapater;
import com.moulberry.flashback.serialization.Vector3fTypeAdapater;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class FlashbackGson {

    public static Gson PRETTY = build().setPrettyPrinting().create();
    public static Gson COMPRESSED = build().create();

    private static GsonBuilder build() {
        return new GsonBuilder()
                .registerTypeAdapter(Vector3f.class, new Vector3fTypeAdapater())
                .registerTypeAdapter(Quaternionf.class, new QuaternionTypeAdapater())
                .registerTypeAdapter(CameraKeyframe.class, new CameraKeyframe.TypeAdapter())
                .registerTypeAdapter(FOVKeyframe.class, new FOVKeyframe.TypeAdapter())
                .registerTypeAdapter(TickrateKeyframe.class, new TickrateKeyframe.TypeAdapter())
                .registerTypeAdapter(TimeOfDayKeyframe.class, new TimeOfDayKeyframe.TypeAdapter())
                .registerTypeAdapter(Keyframe.class, new Keyframe.TypeAdapter());
    }

}
