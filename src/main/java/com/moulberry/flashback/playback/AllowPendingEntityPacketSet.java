package com.moulberry.flashback.playback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;

import java.util.Set;

public class AllowPendingEntityPacketSet {

    public static boolean allowPendingEntity(Packet<?> packet) {
        return ALLOW_PENDING_ENTITY.contains(packet.getClass());
    }

    private static final Set<Class<? extends Packet<?>>> ALLOW_PENDING_ENTITY = Set.of(
        ClientboundAddEntityPacket.class,
        ClientboundSetEntityDataPacket.class,
        ClientboundUpdateAttributesPacket.class,
        ClientboundSetEntityMotionPacket.class,
        ClientboundSetEquipmentPacket.class,
        ClientboundUpdateMobEffectPacket.class,
        ClientboundRotateHeadPacket.class,
        ClientboundTeleportEntityPacket.class
    );

}
