package com.moulberry.flashback;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;

public class PacketHelper {

    public static ClientboundAddEntityPacket createAddEntity(Entity entity) {
        return createAddEntity(entity, 0);
    }

    public static ClientboundAddEntityPacket createAddEntity(Entity entity, int data) {
        return new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(),
                entity.getXRot(), entity.getYRot(), entity.getType(), data, entity.getDeltaMovement(), entity.getYHeadRot());
    }

}
