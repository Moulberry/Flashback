package com.moulberry.flashback.record;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class FlashbackMeta {

    public String name = "Unnamed";
    public LinkedHashMap<String, FlashbackChunkMeta> chunks = new LinkedHashMap<>();
    public int totalTicks = -1;
    public UUID replayIdentifier = UUID.randomUUID();

    public JsonObject toJson() {
        JsonObject meta = new JsonObject();
        meta.addProperty("name", this.name);

        meta.addProperty("uuid", this.replayIdentifier.toString());

        if (this.totalTicks > 0) {
            meta.addProperty("total_ticks", this.totalTicks);
        }

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

        // UUID
        if (!meta.has("uuid")) {
            return null;
        }
        flashbackMeta.replayIdentifier = UUID.fromString(meta.get("uuid").getAsString());

        // Total ticks
        if (meta.has("total_ticks")) {
            flashbackMeta.totalTicks = meta.get("total_ticks").getAsInt();
        }

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
