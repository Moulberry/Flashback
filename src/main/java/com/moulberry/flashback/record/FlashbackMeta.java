package com.moulberry.flashback.record;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moulberry.flashback.FlashbackGson;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class FlashbackMeta {

    public UUID replayIdentifier = UUID.randomUUID();
    public String name = "Unnamed";
    public String versionString = null;
    public String worldName = null;
    public String bobbyWorldName = null;
    public int dataVersion = 0;
    public int protocolVersion = 0;

    public TreeMap<Integer, ReplayMarker> replayMarkers = new TreeMap<>();

    public int totalTicks = -1;
    public LinkedHashMap<String, FlashbackChunkMeta> chunks = new LinkedHashMap<>();

    public Map<String, File> distantHorizonPaths = new HashMap<>();

    public JsonObject toJson() {
        JsonObject meta = new JsonObject();
        meta.addProperty("uuid", this.replayIdentifier.toString());
        meta.addProperty("name", this.name);

        if (this.versionString != null) {
            meta.addProperty("version_string", this.versionString);
        }
        if (this.worldName != null) {
            meta.addProperty("world_name", this.worldName);
        }
        if (this.dataVersion != 0) {
            meta.addProperty("data_version", this.dataVersion);
        }
        if (this.protocolVersion != 0) {
            meta.addProperty("protocol_version", this.protocolVersion);
        }
        if (this.bobbyWorldName != null) {
            meta.addProperty("bobby_world_name", this.bobbyWorldName);
        }

        if (this.totalTicks > 0) {
            meta.addProperty("total_ticks", this.totalTicks);
        }

        if (!this.replayMarkers.isEmpty()) {
            JsonObject jsonMarkers = new JsonObject();
            for (Map.Entry<Integer, ReplayMarker> entry : this.replayMarkers.entrySet()) {
                jsonMarkers.add(""+entry.getKey(), FlashbackGson.COMPRESSED.toJsonTree(entry.getValue()));
            }
            meta.add("markers", jsonMarkers);
        }

        // Distant horizons
        if (!this.distantHorizonPaths.isEmpty()) {
            JsonObject distantHorizonPaths = new JsonObject();
            for (Map.Entry<String, File> entry : this.distantHorizonPaths.entrySet()) {
                distantHorizonPaths.addProperty(entry.getKey(), entry.getValue().getPath());
            }
            meta.add("distantHorizonPaths", distantHorizonPaths);
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

        // UUID
        if (!meta.has("uuid")) {
            return null;
        }
        flashbackMeta.replayIdentifier = UUID.fromString(meta.get("uuid").getAsString());

        // Name
        if (!meta.has("name")) {
            return null;
        }
        flashbackMeta.name = meta.get("name").getAsString();

        if (meta.has("version_string")) {
            flashbackMeta.versionString = meta.get("version_string").getAsString();
        }
        if (meta.has("world_name")) {
            flashbackMeta.worldName = meta.get("world_name").getAsString();
        }
        if (meta.has("data_version")) {
            flashbackMeta.dataVersion = meta.get("data_version").getAsInt();
        }
        if (meta.has("protocol_version")) {
            flashbackMeta.protocolVersion = meta.get("protocol_version").getAsInt();
        }
        if (meta.has("bobby_world_name")) {
            flashbackMeta.bobbyWorldName = meta.get("bobby_world_name").getAsString();
        }

        // Total ticks
        if (meta.has("total_ticks")) {
            flashbackMeta.totalTicks = meta.get("total_ticks").getAsInt();
        }

        if (meta.has("markers")) {
            JsonObject markers = meta.getAsJsonObject("markers");
            for (Map.Entry<String, JsonElement> entry : markers.entrySet()) {
                try {
                    int tick = Integer.parseInt(entry.getKey());
                    flashbackMeta.replayMarkers.put(tick, FlashbackGson.COMPRESSED.fromJson(entry.getValue(), ReplayMarker.class));
                } catch (Exception ignored) {}
            }
        }

        // Distant horizons
        if (meta.has("distantHorizonPaths")) {
            JsonObject distantHorizonPaths = meta.getAsJsonObject("distantHorizonPaths");
            for (Map.Entry<String, JsonElement> entry : distantHorizonPaths.entrySet()) {
                flashbackMeta.distantHorizonPaths.put(entry.getKey(), new File(entry.getValue().getAsString()));
            }
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
