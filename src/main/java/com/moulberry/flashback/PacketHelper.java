package com.moulberry.flashback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;

public class PacketHelper {

    public static boolean shouldIgnoreEntity(Entity entity) {
        return entity == null || entity.isRemoved() || entity instanceof EnderDragonPart || entity.getType().clientTrackingRange() <= 0;
    }

    public static ClientboundTeleportEntityPacket createTeleportForUnknown(int id, double x, double y, double z, byte yRot, byte xRot, boolean onGround) {
        try {
            ClientboundTeleportEntityPacket packet = (ClientboundTeleportEntityPacket) UnsafeWrapper.UNSAFE.allocateInstance(ClientboundTeleportEntityPacket.class);
            packet.id = id;
            packet.x = x;
            packet.y = y;
            packet.z = z;
            packet.yRot = yRot;
            packet.xRot = xRot;
            packet.onGround = onGround;
            return packet;
        } catch (Exception e) {
            throw SneakyThrow.sneakyThrow(e);
        }
    }

    public static Packet<ClientGamePacketListener> createAddEntity(Entity entity) {
        ServerEntity serverEntity = null;

        // Try to construct ServerEntity with dummy values
        try {
            serverEntity = new ServerEntity(null, entity, 1, false, packet -> {});
        } catch (Exception e) {}

        // Error while trying to construct, possibly mod incompatibility? Try bypassing the constructor
        if (serverEntity == null) {
            try {
                serverEntity = (ServerEntity) UnsafeWrapper.UNSAFE.allocateInstance(ServerEntity.class);
                serverEntity.positionCodec = new VecDeltaCodec();
                serverEntity.lastPassengers = new ArrayList<>();
                serverEntity.broadcast = packet -> {};
                serverEntity.entity = entity;
                serverEntity.positionCodec.setBase(entity.trackingPosition());
                serverEntity.lastSentMovement = entity.getDeltaMovement();
                serverEntity.lastSentYRot = Mth.floor((entity.getYRot() * 256.0f / 360.0f));
                serverEntity.lastSentXRot = Mth.floor((entity.getXRot() * 256.0f / 360.0f));
                serverEntity.lastSentYHeadRot = Mth.floor((entity.getYHeadRot() * 256.0f / 360.0f));
                serverEntity.wasOnGround = entity.onGround();
                serverEntity.trackedDataValues = entity.getEntityData().getNonDefaultValues();
            } catch (Exception e) {}
        }

        try {
            return entity.getAddEntityPacket(serverEntity);
        } catch (Exception e) {}

        return createAddEntity(entity, 0);
    }

    public static ClientboundAddEntityPacket createAddEntity(Entity entity, int data) {
        return new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(),
                entity.getXRot(), entity.getYRot(), entity.getType(), data, entity.getDeltaMovement(), entity.getYHeadRot());
    }

}
