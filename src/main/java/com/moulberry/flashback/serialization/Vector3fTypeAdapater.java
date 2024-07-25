package com.moulberry.flashback.serialization;

import com.google.gson.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.lang.reflect.Type;

public class Vector3fTypeAdapater implements JsonSerializer<Vector3f>, JsonDeserializer<Vector3f>  {
    @Override
    public Vector3f deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonArray jsonArray = json.getAsJsonArray();
        if (jsonArray.size() != 3) {
            throw new IllegalStateException("Error deserializing Vector3f, expected array to have length 3, was length " + jsonArray.size() + " instead");
        }
        float x = jsonArray.get(0).getAsFloat();
        float y = jsonArray.get(1).getAsFloat();
        float z = jsonArray.get(2).getAsFloat();

        return new Vector3f(x, y, z);
    }

    @Override
    public JsonElement serialize(Vector3f src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(src.x);
        jsonArray.add(src.y);
        jsonArray.add(src.z);
        return jsonArray;
    }

}
