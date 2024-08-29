package com.moulberry.flashback.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import java.lang.reflect.Type;

public class QuaterniondTypeAdapater implements JsonSerializer<Quaterniond>, JsonDeserializer<Quaterniond>  {
    @Override
    public Quaterniond deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonArray jsonArray = json.getAsJsonArray();
        if (jsonArray.size() != 4) {
            throw new IllegalStateException("Error deserializing Quaterniond, expected array to have length 4, was length " + jsonArray.size() + " instead");
        }
        double x = jsonArray.get(0).getAsDouble();
        double y = jsonArray.get(1).getAsDouble();
        double z = jsonArray.get(2).getAsDouble();
        double w = jsonArray.get(3).getAsDouble();
        return new Quaterniond(x, y, z, w);
    }

    @Override
    public JsonElement serialize(Quaterniond src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(src.x);
        jsonArray.add(src.y);
        jsonArray.add(src.z);
        jsonArray.add(src.w);
        return jsonArray;
    }

}
