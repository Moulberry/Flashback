package com.moulberry.flashback.mixin.packet_raw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.IgnoredPacketSet;
import com.moulberry.flashback.record.Recorder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PacketDecoder.class)
public class MixinPacketDecoder {

    @Shadow
    @Final
    private ProtocolInfo<?> protocolInfo;

    @WrapOperation(method = "decode", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
    public Object decode_decode(StreamCodec<?, ?> instance, Object input, Operation<Object> original) {
        Recorder recorder = Flashback.RECORDER;
        if (recorder == null || this.protocolInfo.flow() != PacketFlow.CLIENTBOUND || !(input instanceof ByteBuf inputByteBuf)) {
            return original.call(instance, input);
        }

        int readableBytes = inputByteBuf.readableBytes();
        int readerIndex = inputByteBuf.readerIndex();

        Object output = original.call(instance, input);

        if (output instanceof Packet<?> packet
            && !(packet instanceof ClientboundLevelChunkWithLightPacket)
            && !IgnoredPacketSet.isIgnored(packet)
        ) {
            byte[] bytes = new byte[readableBytes];
            inputByteBuf.getBytes(readerIndex, bytes);
            recorder.trackRawPacketBytes(packet, bytes);
        }
        return output;
    }

}
