package com.moulberry.flashback.record;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class FlashbackMeta {

    public String name = "Whatever";
    public LinkedHashMap<String, FlashbackChunkMeta> chunks = new LinkedHashMap<>();

    public JsonObject toJson() {
        JsonObject meta = new JsonObject();
        meta.addProperty("name", this.name);

        JsonObject chunksJson = new JsonObject();
        for (Map.Entry<String, FlashbackChunkMeta> entry : this.chunks.entrySet()) {
            chunksJson.add(entry.getKey(), entry.getValue().toJson());
        }
        meta.add("chunks", chunksJson);

        return meta;
    }

    @Nullable
    public static FlashbackMeta fromJson(JsonObject meta) {
        FlashbackMeta flashbackMeta = new FlashbackMeta();

        // Name
        if (!meta.has("name")) {
            return null;
        }
        flashbackMeta.name = meta.get("name").getAsString();

        // Chunks
        if (!meta.has("chunks")) {
            return null;
        }
        JsonObject chunksJson = meta.getAsJsonObject("chunks");
        for (Map.Entry<String, JsonElement> entry : chunksJson.entrySet()) {
            FlashbackChunkMeta chunkMeta = FlashbackChunkMeta.fromJson(entry.getValue().getAsJsonObject());
            if (chunkMeta == null) {
                return null;
            }
            flashbackMeta.chunks.put(entry.getKey(), chunkMeta);
        }

        return flashbackMeta;
    }

}
