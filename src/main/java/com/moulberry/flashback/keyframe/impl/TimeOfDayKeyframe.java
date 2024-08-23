package com.moulberry.flashback.keyframe.impl;

import com.google.gson.*;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.spline.CatmullRom;
import imgui.ImGui;
import imgui.type.ImInt;

import java.lang.reflect.Type;
import java.util.function.Consumer;

public class TimeOfDayKeyframe extends Keyframe {

    public int time;

    public TimeOfDayKeyframe(int time) {
        this(time, InterpolationType.DEFAULT);
    }

    public TimeOfDayKeyframe(int time, InterpolationType interpolationType) {
        this.time = time;
        this.interpolationType(interpolationType);
    }

    @Override
    public Keyframe copy() {
        return new TimeOfDayKeyframe(this.time, this.interpolationType());
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        ImGui.setNextItemWidth(160);
        ImInt input = new ImInt(this.time);
        if (ImGui.inputInt("Time", input, 0)) {
            if (this.time != input.get()) {
                update.accept(keyframe -> ((TimeOfDayKeyframe)keyframe).time = input.get());
            }
        }
    }

    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        keyframeHandler.applyTimeOfDay(this.time);
    }

    @Override
    public void applyInterpolated(KeyframeHandler keyframeHandler, Keyframe otherGeneric, float amount) {
        if (!(otherGeneric instanceof TimeOfDayKeyframe other)) {
            this.apply(keyframeHandler);
            return;
        }

        int time = Math.round(Interpolation.linear(this.time, other.time, amount));
        keyframeHandler.applyTimeOfDay(time);
    }

    @Override
    public void applyInterpolatedSmooth(KeyframeHandler keyframeHandler, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount, float lerpAmount, boolean lerpFromRight) {
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        float timeOfDay = CatmullRom.value(this.time,
            ((TimeOfDayKeyframe)p1).time, ((TimeOfDayKeyframe)p2).time,
            ((TimeOfDayKeyframe)p3).time, time1, time2, time3, amount);

        if (lerpAmount >= 0) {
            float linearTimeOfDay = Interpolation.linear(((TimeOfDayKeyframe)p1).time, ((TimeOfDayKeyframe)p2).time, lerpAmount);

            if (lerpFromRight) {
                timeOfDay = Interpolation.linear(timeOfDay, linearTimeOfDay, amount);
            } else {
                timeOfDay = Interpolation.linear(linearTimeOfDay, timeOfDay, amount);
            }
        }

        keyframeHandler.applyTimeOfDay(Math.round(timeOfDay));
    }


    public static class TypeAdapter implements JsonSerializer<TimeOfDayKeyframe>, JsonDeserializer<TimeOfDayKeyframe> {
        @Override
        public TimeOfDayKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            int timeOfDay = jsonObject.get("time").getAsInt();
            InterpolationType interpolationType = context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            return new TimeOfDayKeyframe(timeOfDay, interpolationType);
        }

        @Override
        public JsonElement serialize(TimeOfDayKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("time", src.time);
            jsonObject.addProperty("type", "time");
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType()));
            return jsonObject;
        }
    }

}
