package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public interface FlashbackVoiceChatSound extends CustomPacketPayload {
    Type<FlashbackVoiceChatSound> TYPE = new Type<>(Flashback.createResourceLocation("voice_chat_sound"));
    StreamCodec<FriendlyByteBuf, FlashbackVoiceChatSound> STREAM_CODEC = new FlashbackVoiceChatSoundStreamCodec();

    @Override
    default Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    UUID source();
    short[] samples();
    void writeExtraData(FriendlyByteBuf friendlyByteBuf);

    byte TYPE_STATIC_SOUND = 0;
    byte TYPE_LOCATIONAL_SOUND = 1;
    byte TYPE_ENTITY_SOUND = 2;

    record SoundStatic(UUID source, short[] samples) implements FlashbackVoiceChatSound {
        @Override
        public void writeExtraData(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(TYPE_STATIC_SOUND);
        }
    }

    record SoundLocational(UUID source, short[] samples, Vec3 position, float distance) implements FlashbackVoiceChatSound {
        @Override
        public void writeExtraData(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(TYPE_LOCATIONAL_SOUND);
            friendlyByteBuf.writeVec3(this.position);
            friendlyByteBuf.writeFloat(this.distance);
        }
    }

    record SoundEntity(UUID source, short[] samples, boolean whispering, float distance) implements FlashbackVoiceChatSound {
        @Override
        public void writeExtraData(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(TYPE_ENTITY_SOUND);
            friendlyByteBuf.writeBoolean(this.whispering);
            friendlyByteBuf.writeFloat(this.distance);
        }
    }

    class FlashbackVoiceChatSoundStreamCodec implements StreamCodec<FriendlyByteBuf, FlashbackVoiceChatSound> {
        @Override
        public FlashbackVoiceChatSound decode(FriendlyByteBuf friendlyByteBuf) {
            UUID uuid = friendlyByteBuf.readUUID();

            int sampleCount = friendlyByteBuf.readVarInt();
            short[] samples = new short[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                samples[i] = friendlyByteBuf.readShort();
            }

            byte type = friendlyByteBuf.readByte();

            switch (type) {
                case TYPE_STATIC_SOUND -> {
                    return new SoundStatic(uuid, samples);
                }
                case TYPE_LOCATIONAL_SOUND -> {
                    Vec3 position = friendlyByteBuf.readVec3();
                    float distance = friendlyByteBuf.readFloat();
                    return new SoundLocational(uuid, samples, position, distance);
                }
                case TYPE_ENTITY_SOUND -> {
                    boolean whispering = friendlyByteBuf.readBoolean();
                    float distance = friendlyByteBuf.readFloat();
                    return new SoundEntity(uuid, samples, whispering, distance);
                }
                default -> throw new DecoderException("Unknown voice chat type: " + type);
            }
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackVoiceChatSound packet) {
            friendlyByteBuf.writeUUID(packet.source());

            short[] samples = packet.samples();
            friendlyByteBuf.writeVarInt(samples.length);
            for (short sample : samples) {
                friendlyByteBuf.writeShort(sample);
            }

            packet.writeExtraData(friendlyByteBuf);
        }
    }

}
