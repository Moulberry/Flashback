package com.moulberry.flashback.state;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;

import java.lang.reflect.Type;

public interface EditorSceneHistoryAction {

    void apply(EditorScene editorScene);

    record SetKeyframe(KeyframeType<?> type, int trackIndex, int tick, Keyframe keyframe) implements EditorSceneHistoryAction {
        @Override
        public void apply(EditorScene editorScene) {
            if (this.trackIndex < editorScene.keyframeTracks.size()) {
                KeyframeTrack track = editorScene.keyframeTracks.get(this.trackIndex);
                if (track.keyframeType == this.type) {
                    track.keyframesByTick.put(this.tick, this.keyframe.copy());
                }
            }
        }

        public static class TypeAdapter implements JsonSerializer<SetKeyframe>, JsonDeserializer<SetKeyframe> {
            @Override
            public SetKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                JsonObject jsonObject = json.getAsJsonObject();
                KeyframeType<?> type = context.deserialize(jsonObject.get("keyframe_type"), KeyframeType.class);
                int trackIndex = jsonObject.get("trackIndex").getAsInt();
                int tick = jsonObject.get("tick").getAsInt();
                Keyframe keyframe = context.deserialize(jsonObject.get("keyframe"), Keyframe.class);
                return new SetKeyframe(type, trackIndex, tick, keyframe);
            }

            @Override
            public JsonElement serialize(SetKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("action_type", "set_keyframe");
                jsonObject.add("keyframe_type", context.serialize(src.type));
                jsonObject.addProperty("trackIndex", src.trackIndex);
                jsonObject.addProperty("tick", src.tick);
                jsonObject.add("keyframe", context.serialize(src.keyframe));
                return jsonObject;
            }
        }
    }

    record RemoveKeyframe(KeyframeType<?> type, int trackIndex, int tick) implements EditorSceneHistoryAction {
        @Override
        public void apply(EditorScene editorScene) {
            if (this.trackIndex < editorScene.keyframeTracks.size()) {
                KeyframeTrack track = editorScene.keyframeTracks.get(this.trackIndex);
                if (track.keyframeType == this.type) {
                    track.keyframesByTick.remove(this.tick);
                }
            }
        }

        public static class TypeAdapter implements JsonSerializer<RemoveKeyframe>, JsonDeserializer<RemoveKeyframe> {
            @Override
            public RemoveKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                JsonObject jsonObject = json.getAsJsonObject();
                KeyframeType<?> type = context.deserialize(jsonObject.get("keyframe_type"), KeyframeType.class);
                int trackIndex = jsonObject.get("trackIndex").getAsInt();
                int tick = jsonObject.get("tick").getAsInt();
                return new RemoveKeyframe(type, trackIndex, tick);
            }

            @Override
            public JsonElement serialize(RemoveKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("action_type", "remove_keyframe");
                jsonObject.add("keyframe_type", context.serialize(src.type));
                jsonObject.addProperty("trackIndex", src.trackIndex);
                jsonObject.addProperty("tick", src.tick);
                return jsonObject;
            }
        }
    }

    record AddTrack(KeyframeType<?> type, int trackIndex) implements EditorSceneHistoryAction {
        @Override
        public void apply(EditorScene editorScene) {
            if (this.trackIndex <= editorScene.keyframeTracks.size()) {
                editorScene.keyframeTracks.add(this.trackIndex, new KeyframeTrack(this.type));
            }
        }

        public static class TypeAdapter implements JsonSerializer<AddTrack>, JsonDeserializer<AddTrack> {
            @Override
            public AddTrack deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                JsonObject jsonObject = json.getAsJsonObject();
                KeyframeType<?> type = context.deserialize(jsonObject.get("keyframe_type"), KeyframeType.class);
                int trackIndex = jsonObject.get("trackIndex").getAsInt();
                return new AddTrack(type, trackIndex);
            }

            @Override
            public JsonElement serialize(AddTrack src, Type typeOfSrc, JsonSerializationContext context) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("action_type", "add_track");
                jsonObject.add("keyframe_type", context.serialize(src.type));
                jsonObject.addProperty("trackIndex", src.trackIndex);
                return jsonObject;
            }
        }
    }

    record RemoveTrack(KeyframeType<?> type, int trackIndex) implements EditorSceneHistoryAction {
        @Override
        public void apply(EditorScene editorScene) {
            if (this.trackIndex < editorScene.keyframeTracks.size()) {
                KeyframeTrack keyframeTrack = editorScene.keyframeTracks.get(this.trackIndex);
                if (keyframeTrack.keyframeType == type) {
                    editorScene.keyframeTracks.remove(this.trackIndex);
                }
            }
        }

        public static class TypeAdapter implements JsonSerializer<RemoveTrack>, JsonDeserializer<RemoveTrack> {
            @Override
            public RemoveTrack deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                JsonObject jsonObject = json.getAsJsonObject();
                KeyframeType<?> type = context.deserialize(jsonObject.get("keyframe_type"), KeyframeType.class);
                int trackIndex = jsonObject.get("trackIndex").getAsInt();
                return new RemoveTrack(type, trackIndex);
            }

            @Override
            public JsonElement serialize(RemoveTrack src, Type typeOfSrc, JsonSerializationContext context) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("action_type", "remove_track");
                jsonObject.add("keyframe_type", context.serialize(src.type));
                jsonObject.addProperty("trackIndex", src.trackIndex);
                return jsonObject;
            }
        }
    }

    class TypeAdapter implements JsonSerializer<EditorSceneHistoryAction>, JsonDeserializer<EditorSceneHistoryAction> {
        @Override
        public EditorSceneHistoryAction deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            String type = jsonObject.get("action_type").getAsString();
            return switch (type) {
                case "set_keyframe" -> context.deserialize(json, SetKeyframe.class);
                case "remove_keyframe" -> context.deserialize(json, RemoveKeyframe.class);
                case "add_track" -> context.deserialize(json, AddTrack.class);
                case "remove_track" -> context.deserialize(json, RemoveTrack.class);
                default -> throw new IllegalStateException("Unknown action type: " + type);
            };
        }

        @Override
        public JsonElement serialize(EditorSceneHistoryAction src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject;
            switch (src) {
                case SetKeyframe setKeyframe -> {
                    jsonObject = (JsonObject) context.serialize(setKeyframe);
                    jsonObject.addProperty("action_type", "set_keyframe");
                }
                case RemoveKeyframe removeKeyframe -> {
                    jsonObject = (JsonObject) context.serialize(removeKeyframe);
                    jsonObject.addProperty("action_type", "remove_keyframe");
                }
                case AddTrack addTrack -> {
                    jsonObject = (JsonObject) context.serialize(addTrack);
                    jsonObject.addProperty("action_type", "add_track");
                }
                case RemoveTrack removeTrack -> {
                    jsonObject = (JsonObject) context.serialize(removeTrack);
                    jsonObject.addProperty("action_type", "remove_track");
                }
                default -> throw new IllegalStateException("Unknown action type: " + src.getClass());
            }
            return jsonObject;
        }
    }

}
