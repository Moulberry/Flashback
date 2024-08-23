package com.moulberry.flashback;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraShakeKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.impl.TickrateKeyframe;
import com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe;
import com.moulberry.flashback.keyframe.impl.TimelapseKeyframe;
import com.moulberry.flashback.serialization.QuaternionTypeAdapater;
import com.moulberry.flashback.serialization.Vector3fTypeAdapater;
import com.moulberry.flashback.state.EditorStateHistoryAction;
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
            .registerTypeAdapter(CameraOrbitKeyframe.class, new CameraOrbitKeyframe.TypeAdapter())
            .registerTypeAdapter(FOVKeyframe.class, new FOVKeyframe.TypeAdapter())
            .registerTypeAdapter(CameraShakeKeyframe.class, new CameraShakeKeyframe.TypeAdapter())
            .registerTypeAdapter(TickrateKeyframe.class, new TickrateKeyframe.TypeAdapter())
            .registerTypeAdapter(TimelapseKeyframe.class, new TimelapseKeyframe.TypeAdapter())
            .registerTypeAdapter(TimeOfDayKeyframe.class, new TimeOfDayKeyframe.TypeAdapter())
            .registerTypeAdapter(Keyframe.class, new Keyframe.TypeAdapter())

            .registerTypeAdapter(EditorStateHistoryAction.SetKeyframe.class, new EditorStateHistoryAction.SetKeyframe.TypeAdapter())
            .registerTypeAdapter(EditorStateHistoryAction.RemoveKeyframe.class, new EditorStateHistoryAction.RemoveKeyframe.TypeAdapter())
            .registerTypeAdapter(EditorStateHistoryAction.AddTrack.class, new EditorStateHistoryAction.AddTrack.TypeAdapter())
            .registerTypeAdapter(EditorStateHistoryAction.RemoveTrack.class, new EditorStateHistoryAction.RemoveTrack.TypeAdapter())
            .registerTypeAdapter(EditorStateHistoryAction.class, new EditorStateHistoryAction.TypeAdapter());
    }

}
