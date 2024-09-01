package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FlashbackRemoteExperience(int entityId, float experienceProgress, int totalExperience, int experienceLevel) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteExperience> TYPE = new Type<>(Flashback.createResourceLocation("remote_experience"));

    public static final StreamCodec<FriendlyByteBuf, FlashbackRemoteExperience> STREAM_CODEC = new FlashbackRemoteExperienceStreamCodec();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class FlashbackRemoteExperienceStreamCodec implements StreamCodec<FriendlyByteBuf, FlashbackRemoteExperience> {
        @Override
        public FlashbackRemoteExperience decode(FriendlyByteBuf friendlyByteBuf) {
            int entityId = friendlyByteBuf.readVarInt();
            float experienceProgress = friendlyByteBuf.readFloat();
            int totalExperience = friendlyByteBuf.readVarInt();
            int experienceLevel = friendlyByteBuf.readVarInt();
            return new FlashbackRemoteExperience(entityId, experienceProgress, totalExperience, experienceLevel);
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackRemoteExperience remoteHotbarSlot) {
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.entityId);
            friendlyByteBuf.writeFloat(remoteHotbarSlot.experienceProgress);
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.totalExperience);
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.experienceLevel);
        }
    }

}
