package com.moulberry.flashback.keyframe.impl;

import com.google.common.collect.Maps;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.moulberry.flashback.combo_options.TrackingBodyPart;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTrackEntity;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.TrackEntityKeyframeType;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.spline.Hermite;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class TrackEntityKeyframe extends Keyframe {

    public UUID target;
    public TrackingBodyPart trackingBodyPart;
    public float yawOffset;
    public float pitchOffset;
    public final Vector3d positionOffset;
    public final Vector3d viewOffset;

    public TrackEntityKeyframe(UUID target, TrackingBodyPart trackingBodyPart, float yawOffset, float pitchOffset, Vector3d positionOffset, Vector3d viewOffset) {
        this(target, trackingBodyPart, yawOffset, pitchOffset, positionOffset, viewOffset, InterpolationType.getDefault());
    }

    public TrackEntityKeyframe(UUID target, TrackingBodyPart trackingBodyPart, float yawOffset, float pitchOffset, Vector3d positionOffset, Vector3d viewOffset, InterpolationType interpolationType) {
        this.target = target;
        this.trackingBodyPart = trackingBodyPart;
        this.yawOffset = yawOffset;
        this.pitchOffset = pitchOffset;
        this.positionOffset = positionOffset;
        this.viewOffset = viewOffset;
        this.interpolationType(interpolationType);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return TrackEntityKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return new TrackEntityKeyframe(this.target, this.trackingBodyPart, this.yawOffset, this.pitchOffset,
            new Vector3d(this.positionOffset), new Vector3d(this.viewOffset), this.interpolationType());
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        ImString target = new ImString(this.target.toString());
        target.inputData.isResizable = true;
        if (ImGui.inputText("Entity UUID", target)) {
            try {
                String uuidStr = ImGuiHelper.getString(target);
                UUID uuid = UUID.fromString(uuidStr);

                ClientLevel level = Minecraft.getInstance().level;
                if (level != null) {
                    Entity entity = level.getEntities().get(uuid);
                    if (entity != null && entity != Minecraft.getInstance().player) {
                        update.accept(keyframe -> ((TrackEntityKeyframe)keyframe).target = uuid);
                    }
                }
            } catch (Exception ignored) {}
        }

        TrackingBodyPart trackingBodyPart = ImGuiHelper.enumCombo("Body Part", this.trackingBodyPart);
        if (trackingBodyPart != this.trackingBodyPart) {
            update.accept(keyframe -> ((TrackEntityKeyframe)keyframe).trackingBodyPart = trackingBodyPart);
        }
        float[] input = new float[]{this.yawOffset};
        if (ImGuiHelper.inputFloat("Yaw Offset", input)) {
            if (input[0] != this.yawOffset) {
                update.accept(keyframe -> ((TrackEntityKeyframe)keyframe).yawOffset = input[0]);
            }
        }
        input[0] = this.pitchOffset;
        if (ImGuiHelper.inputFloat("Pitch Offset", input)) {
            if (input[0] != this.pitchOffset) {
                update.accept(keyframe -> ((TrackEntityKeyframe)keyframe).pitchOffset = input[0]);
            }
        }
        float[] positionOffset = new float[]{(float) this.positionOffset.x, (float) this.positionOffset.y, (float) this.positionOffset.z};
        if (ImGuiHelper.inputFloat("Position Offset", positionOffset)) {
            if (positionOffset[0] != this.positionOffset.x) {
                update.accept(keyframe -> ((TrackEntityKeyframe)keyframe).positionOffset.x = positionOffset[0]);
            }
            if (positionOffset[1] != this.positionOffset.y) {
                update.accept(keyframe -> ((TrackEntityKeyframe)keyframe).positionOffset.y = positionOffset[1]);
            }
            if (positionOffset[2] != this.positionOffset.z) {
                update.accept(keyframe -> ((TrackEntityKeyframe)keyframe).positionOffset.z = positionOffset[2]);
            }
        }
        float[] viewOffset = new float[]{(float) this.viewOffset.x, (float) this.viewOffset.y, (float) this.viewOffset.z};
        if (ImGuiHelper.inputFloat("View Offset", viewOffset)) {
            if (viewOffset[0] != this.viewOffset.x) {
                update.accept(keyframe -> ((TrackEntityKeyframe)keyframe).viewOffset.x = viewOffset[0]);
            }
            if (viewOffset[1] != this.viewOffset.y) {
                update.accept(keyframe -> ((TrackEntityKeyframe)keyframe).viewOffset.y = viewOffset[1]);
            }
            if (viewOffset[2] != this.viewOffset.z) {
                update.accept(keyframe -> ((TrackEntityKeyframe)keyframe).viewOffset.z = viewOffset[2]);
            }
        }
    }

    @Override
    public KeyframeChange createChange() {
        return new KeyframeChangeTrackEntity(this.target, this.trackingBodyPart, this.yawOffset, this.pitchOffset, this.positionOffset, this.viewOffset);
    }

    @Override
    public KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount) {
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        UUID target = amount < 0.5 ? ((TrackEntityKeyframe)p1).target : ((TrackEntityKeyframe)p2).target;
        TrackingBodyPart trackingBodyPart = amount < 0.5 ? ((TrackEntityKeyframe)p1).trackingBodyPart : ((TrackEntityKeyframe)p2).trackingBodyPart;

        // Note: we don't use CatmullRom#degrees because we want to allow multiple rotations in a single orbit
        float yawOffset = CatmullRom.value(this.yawOffset, ((TrackEntityKeyframe)p1).yawOffset, ((TrackEntityKeyframe)p2).yawOffset,
            ((TrackEntityKeyframe)p3).yawOffset, time1, time2, time3, amount);
        float pitchOffset = CatmullRom.value(this.pitchOffset, ((TrackEntityKeyframe)p1).pitchOffset, ((TrackEntityKeyframe)p2).pitchOffset,
            ((TrackEntityKeyframe)p3).pitchOffset, time1, time2, time3, amount);

        Vector3d positionOffset = CatmullRom.position(this.positionOffset,
            ((TrackEntityKeyframe)p1).positionOffset, ((TrackEntityKeyframe)p2).positionOffset,
            ((TrackEntityKeyframe)p3).positionOffset, time1, time2, time3, amount);
        Vector3d viewOffset = CatmullRom.position(this.viewOffset,
            ((TrackEntityKeyframe)p1).viewOffset, ((TrackEntityKeyframe)p2).viewOffset,
            ((TrackEntityKeyframe)p3).viewOffset, time1, time2, time3, amount);

        return new KeyframeChangeTrackEntity(target, trackingBodyPart, yawOffset, pitchOffset, positionOffset, viewOffset);
    }

    @Override
    public KeyframeChange createHermiteInterpolatedChange(Map<Integer, Keyframe> keyframes, float amount) {
        float lowestTickDelta = Float.MAX_VALUE;
        UUID target = null;
        TrackingBodyPart trackingBodyPart = null;
        for (Map.Entry<Integer, Keyframe> entry : keyframes.entrySet()) {
            float tickDelta = Math.abs(entry.getKey() - amount);
            if (tickDelta < lowestTickDelta) {
                lowestTickDelta = tickDelta;
                target = ((TrackEntityKeyframe)entry.getValue()).target;
                trackingBodyPart = ((TrackEntityKeyframe)entry.getValue()).trackingBodyPart;
            }
        }


        // Note: we don't use Hermite#degrees because we want to allow multiple rotations in a single orbit
        float yawOffset = (float) Hermite.value(Maps.transformValues(keyframes, k -> (double) ((TrackEntityKeyframe)k).yawOffset), amount);
        float pitchOffset = (float) Hermite.value(Maps.transformValues(keyframes, k -> (double) ((TrackEntityKeyframe)k).pitchOffset), amount);

        Vector3d positionOffset = Hermite.position(Maps.transformValues(keyframes, k -> ((TrackEntityKeyframe)k).positionOffset), amount);
        Vector3d viewOffset = Hermite.position(Maps.transformValues(keyframes, k -> ((TrackEntityKeyframe)k).viewOffset), amount);

        return new KeyframeChangeTrackEntity(target, trackingBodyPart, yawOffset, pitchOffset, positionOffset, viewOffset);
    }

    public static class TypeAdapter implements JsonSerializer<TrackEntityKeyframe>, JsonDeserializer<TrackEntityKeyframe> {
        @Override
        public TrackEntityKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            UUID target = context.deserialize(jsonObject.get("target"), UUID.class);
            TrackingBodyPart bodyPart = context.deserialize(jsonObject.get("bodyPart"), TrackingBodyPart.class);
            float yawOffset = jsonObject.get("yawOffset").getAsFloat();
            float pitchOffset = jsonObject.get("pitchOffset").getAsFloat();
            Vector3d positionOffset = context.deserialize(jsonObject.get("positionOffset"), Vector3d.class);
            Vector3d viewOffset = context.deserialize(jsonObject.get("viewOffset"), Vector3d.class);
            InterpolationType interpolationType = context.deserialize(jsonObject.get("interpolation_type"), InterpolationType.class);
            if (positionOffset == null) positionOffset = new Vector3d();
            if (viewOffset == null) viewOffset = new Vector3d();
            return new TrackEntityKeyframe(target, bodyPart, yawOffset, pitchOffset, positionOffset, viewOffset, interpolationType);
        }

        @Override
        public JsonElement serialize(TrackEntityKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("target", context.serialize(src.target));
            jsonObject.add("bodyPart", context.serialize(src.trackingBodyPart));
            jsonObject.addProperty("yawOffset", src.yawOffset);
            jsonObject.addProperty("pitchOffset", src.pitchOffset);
            jsonObject.add("positionOffset", context.serialize(src.positionOffset));
            jsonObject.add("viewOffset", context.serialize(src.viewOffset));
            jsonObject.addProperty("type", "track_entity");
            jsonObject.add("interpolation_type", context.serialize(src.interpolationType()));
            return jsonObject;
        }
    }

}
