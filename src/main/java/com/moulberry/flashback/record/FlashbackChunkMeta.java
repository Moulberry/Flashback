package com.moulberry.flashback.record;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public class FlashbackChunkMeta {
    public int duration = 0;

    public JsonObject toJson() {
        JsonObject chunkMeta = new JsonObject();
        chunkMeta.addProperty("duration", this.duration);
        return chunkMeta;
    }

    @Nullable
    public static FlashbackChunkMeta fromJson(JsonObject chunkMeta) {
        FlashbackChunkMeta flashbackChunkMeta = new FlashbackChunkMeta();

        if (!chunkMeta.has("duration")) {
            return null;
        }
        flashbackChunkMeta.duration = chunkMeta.get("duration").getAsInt();

        return flashbackChunkMeta;
    }
}
