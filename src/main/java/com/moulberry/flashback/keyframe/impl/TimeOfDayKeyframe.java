package com.moulberry.flashback.keyframe.impl;

import com.google.gson.*;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.ReplayVisuals;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.spline.CatmullRom;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

import java.lang.reflect.Type;

public class TimeOfDayKeyframe extends Keyframe<Minecraft> {

    private final int time;

    public TimeOfDayKeyframe(int time) {
        super(Minecraft.class);
        this.time = time;
    }

    private void setTime(Minecraft minecraft, int time) {
        time = time % 24000;
        if (time < 0) {
            time += 24000;
        }

        ReplayVisuals.overrideTimeOfDay = time;
    }

    @Override
    public void apply(Minecraft minecraft) {
        this.setTime(minecraft, this.time);
    }

    @Override
    public void applyInterpolated(Minecraft minecraft, Keyframe otherGeneric, float amount) {
        if (!(otherGeneric instanceof TimeOfDayKeyframe other)) {
            this.apply(minecraft);
            return;
        }

        int time = Math.round(Interpolation.linear(this.time, other.time, amount));
        this.setTime(minecraft, time);
    }

    @Override
    public void applyInterpolatedSmooth(Minecraft minecraft, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount, float lerpAmount, boolean lerpFromRight) {
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

        this.setTime(minecraft, Math.round(timeOfDay));
    }


    public static class TypeAdapter implements JsonSerializer<TimeOfDayKeyframe>, JsonDeserializer<TimeOfDayKeyframe> {
        @Override
        public TimeOfDayKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            int timeOfDay = jsonObject.get("time").getAsInt();
            return new TimeOfDayKeyframe(timeOfDay);
        }

        @Override
        public JsonElement serialize(TimeOfDayKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("time", src.time);
            return jsonObject;
        }
    }

}
