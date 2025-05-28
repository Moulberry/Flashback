package com.moulberry.flashback.keyframe.impl;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeFreeze;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.SpeedKeyframeType;
import imgui.ImGui;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class BlockOverrideKeyframe extends Keyframe {

    private static final long MIN_POSITION_LONG = BlockPos.asLong(-33554432, -2048, -33554432);
    static {
        if (MIN_POSITION_LONG != 0b1000000000000000000000000010000000000000000000000000100000000000L) {
            throw new RuntimeException("BlockPos representation changed!");
        }
    }
    public static final BlockState EMPTY_STATE = Blocks.VOID_AIR.defaultBlockState();

    public Long2ObjectMap<PalettedContainer<BlockState>> blocks = new Long2ObjectOpenHashMap<>();
    private long lastPos = MIN_POSITION_LONG;
    private PalettedContainer<BlockState> lastContainer = null;

    public BlockOverrideKeyframe() {
        this.interpolationType(InterpolationType.LINEAR);
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return SpeedKeyframeType.INSTANCE;
    }

    public void setBlock(int x, int y, int z, BlockState blockState) {
        long chunk = BlockPos.asLong(x >> 4, y >> 4, z >> 4);
        if (chunk != lastPos) {
            lastPos = chunk;
            lastContainer = blocks.computeIfAbsent(chunk, l -> new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, EMPTY_STATE, PalettedContainer.Strategy.SECTION_STATES));
        }
        lastContainer.set(x & 0xF, y & 0xF, z & 0xF, blockState);
    }

    @Override
    public Keyframe copy() {
        BlockOverrideKeyframe keyframe = new BlockOverrideKeyframe();
        for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry : this.blocks.long2ObjectEntrySet()) {
            keyframe.blocks.put(entry.getLongKey(), entry.getValue().copy());
        }
        return keyframe;
    }

    @Override
    public KeyframeChange createChange() {
        return null;
    }

    @Override
    public KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount) {
        return null;
    }

    @Override
    public KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float amount) {
        return null;
    }

    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(
        Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, EMPTY_STATE
    );

    public static class TypeAdapter implements JsonSerializer<BlockOverrideKeyframe>, JsonDeserializer<BlockOverrideKeyframe> {
        @Override
        public BlockOverrideKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();

            BlockOverrideKeyframe blockOverrideKeyframe = new BlockOverrideKeyframe();
            JsonObject chunks = jsonObject.getAsJsonObject("chunks");
            for (String key : chunks.keySet()) {
                long pos = Long.parseLong(key);
                Optional<Pair<PalettedContainer<BlockState>, JsonElement>> result = BLOCK_STATE_CODEC.decode(JsonOps.COMPRESSED, chunks.get(key)).result();
                if (result.isPresent()) {
                    PalettedContainer<BlockState> container = result.get().getFirst();
                    blockOverrideKeyframe.blocks.put(pos, container);
                }
            }
            return blockOverrideKeyframe;
        }

        @Override
        public JsonElement serialize(BlockOverrideKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject chunks = new JsonObject();
            for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> entry : src.blocks.long2ObjectEntrySet()) {
                Optional<JsonElement> result = BLOCK_STATE_CODEC.encodeStart(JsonOps.COMPRESSED, entry.getValue()).result();
                if (result.isPresent()) {
                    chunks.add(entry.getLongKey()+"", result.get());
                }
            }

            JsonObject jsonObject = new JsonObject();
            jsonObject.add("chunks", chunks);
            jsonObject.addProperty("type", "block_override");
            return jsonObject;
        }
    }
}
