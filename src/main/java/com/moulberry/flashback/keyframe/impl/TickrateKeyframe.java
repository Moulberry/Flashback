package com.moulberry.flashback.keyframe.impl;

import com.google.gson.*;
import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.ReplayVisuals;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.spline.CatmullRom;

import java.lang.reflect.Type;

public class TickrateKeyframe extends Keyframe<ReplayServer> {

    private float tickrate;

    public TickrateKeyframe(float tickrate) {
        super(ReplayServer.class);
        this.tickrate = tickrate;
    }

    @Override
    public void apply(ReplayServer replayServer) {
        replayServer.desiredTickRate = this.tickrate;
    }

    @Override
    public void applyInterpolated(ReplayServer replayServer, Keyframe otherGeneric, float amount) {
        if (!(otherGeneric instanceof TickrateKeyframe other)) {
            this.apply(replayServer);
            return;
        }

        float tickrate = Interpolation.linear(this.tickrate, other.tickrate, amount);
        replayServer.desiredTickRate = tickrate;
    }

    @Override
    public void applyInterpolatedSmooth(ReplayServer replayServer, Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount, float lerpAmount, boolean lerpFromRight) {
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        float tickrate = CatmullRom.value(this.tickrate,
            ((TickrateKeyframe)p1).tickrate, ((TickrateKeyframe)p2).tickrate,
            ((TickrateKeyframe)p3).tickrate, time1, time2, time3, amount);

        if (lerpAmount >= 0) {
            float linearTickrate = Interpolation.linear(((TickrateKeyframe)p1).tickrate, ((TickrateKeyframe)p2).tickrate, lerpAmount);

            if (lerpFromRight) {
                tickrate = Interpolation.linear(tickrate, linearTickrate, amount);
            } else {
                tickrate = Interpolation.linear(linearTickrate, tickrate, amount);
            }
        }

        replayServer.desiredTickRate = tickrate;
    }

    public static class TypeAdapter implements JsonSerializer<TickrateKeyframe>, JsonDeserializer<TickrateKeyframe> {
        @Override
        public TickrateKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            float tickrate = jsonObject.get("tickrate").getAsFloat();
            return new TickrateKeyframe(tickrate);
        }

        @Override
        public JsonElement serialize(TickrateKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("tickrate", src.tickrate);
            return jsonObject;
        }
    }
}
