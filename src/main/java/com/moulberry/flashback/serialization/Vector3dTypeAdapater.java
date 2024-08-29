package com.moulberry.flashback.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.lang.reflect.Type;

public class Vector3dTypeAdapater implements JsonSerializer<Vector3d>, JsonDeserializer<Vector3d>  {
    @Override
    public Vector3d deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonArray jsonArray = json.getAsJsonArray();
        if (jsonArray.size() != 3) {
            throw new IllegalStateException("Error deserializing Vector3f, expected array to have length 3, was length " + jsonArray.size() + " instead");
        }
        double x = jsonArray.get(0).getAsDouble();
        double y = jsonArray.get(1).getAsDouble();
        double z = jsonArray.get(2).getAsDouble();

        return new Vector3d(x, y, z);
    }

    @Override
    public JsonElement serialize(Vector3d src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(src.x);
        jsonArray.add(src.y);
        jsonArray.add(src.z);
        return jsonArray;
    }

}
