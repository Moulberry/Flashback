package com.moulberry.flashback.visuals;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.PositionAndAngle;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import com.moulberry.flashback.playback.ReplayServer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class AccurateEntityPositionHandler {

    private static Int2ObjectMap<List<PositionAndAngle>> currentData = null;
    private static Int2ObjectMap<List<PositionAndAngle>> pendingData = null;

    public static void tick() {
        currentData = pendingData;
        pendingData = null;
    }

    public static void update(FlashbackAccurateEntityPosition data) {
        if (pendingData == null) {
            pendingData = new Int2ObjectOpenHashMap<>();
        }
        pendingData.put(data.entityId(), data.positionAndAngles());
    }

    public static void apply(ClientLevel level, float partialTick) {
        if (currentData == null) {
            return;
        }

        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null && level != null) {
            for (Int2ObjectMap.Entry<List<PositionAndAngle>> entry : currentData.int2ObjectEntrySet()) {
                Entity entity = level.getEntity(entry.getIntKey());
                if (entity == null) {
                    continue;
                }

                List<PositionAndAngle> positionAndAngles = entry.getValue();
                float amount = partialTick * (positionAndAngles.size() - 1);

                int floorAmount = (int) amount;
                PositionAndAngle floorPosition = positionAndAngles.get(floorAmount);

                int ceilAmount = floorAmount + 1;

                if (ceilAmount >= positionAndAngles.size()) {
                    entity.setYHeadRot(floorPosition.yaw());
                    entity.moveTo(floorPosition.x(), floorPosition.y(), floorPosition.z(), floorPosition.yaw(), floorPosition.pitch());
                    if (entity instanceof LivingEntity livingEntity) {
                        livingEntity.lerpSteps = 0;
                        livingEntity.lerpHeadSteps = 0;
                        livingEntity.yHeadRotO = livingEntity.yHeadRot;
                    }
                } else {
                    PositionAndAngle ceilPosition = positionAndAngles.get(ceilAmount);
                    float partialAmount = amount - floorAmount;

                    double x = floorPosition.x() + (ceilPosition.x() - floorPosition.x()) * partialAmount;
                    double y = floorPosition.y() + (ceilPosition.y() - floorPosition.y()) * partialAmount;
                    double z = floorPosition.z() + (ceilPosition.z() - floorPosition.z()) * partialAmount;
                    float yaw = floorPosition.yaw() + Mth.wrapDegrees(ceilPosition.yaw() - floorPosition.yaw()) * partialAmount;
                    float pitch = floorPosition.pitch() + Mth.wrapDegrees(ceilPosition.pitch() - floorPosition.pitch()) * partialAmount;

                    entity.setYHeadRot(Mth.wrapDegrees(yaw));
                    entity.moveTo(x, y, z, Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch));
                    if (entity instanceof LivingEntity livingEntity) {
                        livingEntity.lerpSteps = 0;
                        livingEntity.lerpHeadSteps = 0;
                        livingEntity.yHeadRotO = livingEntity.yHeadRot;
                    }
                }

            }
        }
    }

}
