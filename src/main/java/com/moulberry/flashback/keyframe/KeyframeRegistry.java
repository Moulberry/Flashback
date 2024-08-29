package com.moulberry.flashback.keyframe;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class KeyframeRegistry {

    private static final List<KeyframeType<?>> types = new ArrayList<>();
    private static final Map<String, KeyframeType<?>> typesById = new HashMap<>();
    private static final Set<Class<?>> typesRegistered = new HashSet<>();

    public static List<KeyframeType<?>> getTypes() {
        return types;
    }

    public static void register(KeyframeType<?> type) {
        if (!typesRegistered.add(type.getClass())) {
            return;
        }

        typesById.put(type.id(), type);
        types.add(type);
    }

    public static class TypeAdapter implements JsonSerializer<KeyframeType<?>>, JsonDeserializer<KeyframeType<?>> {
        @Override
        public KeyframeType<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            String id = json.getAsString();
            return Objects.requireNonNull(typesById.get(id));
        }

        @Override
        public JsonElement serialize(KeyframeType<?> src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.id());
        }
    }

}
