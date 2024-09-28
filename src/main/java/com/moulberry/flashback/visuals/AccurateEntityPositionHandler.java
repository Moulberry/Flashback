package com.moulberry.flashback.visuals;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.PositionAndAngle;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import com.moulberry.flashback.playback.ReplayServer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3d;

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

    @Nullable
    public static Vector2f getAccurateRotation(Entity entity, float partialTick) {
        if (currentData != null && currentData.containsKey(entity.getId())) {
            List<PositionAndAngle> positionAndAngles = currentData.get(entity.getId());
            float amount = partialTick * (positionAndAngles.size() - 1);

            int floorAmount = (int) amount;
            PositionAndAngle floorPosition = positionAndAngles.get(floorAmount);

            int ceilAmount = floorAmount + 1;

            if (ceilAmount >= positionAndAngles.size()) {
                return new Vector2f(floorPosition.pitch(), floorPosition.yaw());
            } else {
                PositionAndAngle ceilPosition = positionAndAngles.get(ceilAmount);
                float partialAmount = amount - floorAmount;

                float yaw = floorPosition.yaw() + Mth.wrapDegrees(ceilPosition.yaw() - floorPosition.yaw()) * partialAmount;
                float pitch = floorPosition.pitch() + Mth.wrapDegrees(ceilPosition.pitch() - floorPosition.pitch()) * partialAmount;

                return new Vector2f(Mth.wrapDegrees(pitch), Mth.wrapDegrees(yaw));
            }
        }
        return null;
    }

    @Nullable
    public static Vector3d getAccuratePosition(Entity entity, float partialTick) {
        if (entity.isPassenger() || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return null;
        }

        if (currentData != null && currentData.containsKey(entity.getId())) {
            List<PositionAndAngle> positionAndAngles = currentData.get(entity.getId());
            float amount = partialTick * (positionAndAngles.size() - 1);

            int floorAmount = (int) amount;
            PositionAndAngle floorPosition = positionAndAngles.get(floorAmount);

            int ceilAmount = floorAmount + 1;

            if (ceilAmount >= positionAndAngles.size()) {
                return new Vector3d(floorPosition.x(), floorPosition.y(), floorPosition.z());
            } else {
                PositionAndAngle ceilPosition = positionAndAngles.get(ceilAmount);
                float partialAmount = amount - floorAmount;

                double x = floorPosition.x() + (ceilPosition.x() - floorPosition.x()) * partialAmount;
                double y = floorPosition.y() + (ceilPosition.y() - floorPosition.y()) * partialAmount;
                double z = floorPosition.z() + (ceilPosition.z() - floorPosition.z()) * partialAmount;

                return new Vector3d(x, y, z);
            }
        }
        return null;
    }

    public static void apply(ClientLevel level, DeltaTracker deltaTracker) {
        if (currentData == null || level == null) {
            return;
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);

        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
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
                    applyPosition(entity, floorPosition.x(), floorPosition.y(), floorPosition.z(), floorPosition.yaw(), floorPosition.pitch());
                } else {
                    PositionAndAngle ceilPosition = positionAndAngles.get(ceilAmount);
                    float partialAmount = amount - floorAmount;

                    double x = floorPosition.x() + (ceilPosition.x() - floorPosition.x()) * partialAmount;
                    double y = floorPosition.y() + (ceilPosition.y() - floorPosition.y()) * partialAmount;
                    double z = floorPosition.z() + (ceilPosition.z() - floorPosition.z()) * partialAmount;
                    float yaw = floorPosition.yaw() + Mth.wrapDegrees(ceilPosition.yaw() - floorPosition.yaw()) * partialAmount;
                    float pitch = floorPosition.pitch() + Mth.wrapDegrees(ceilPosition.pitch() - floorPosition.pitch()) * partialAmount;

                    applyPosition(entity, x, y, z,  Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch));
                }

            }
        }
    }

    private static void applyPosition(Entity entity, double x, double y, double z, float yaw, float pitch) {
        if (!entity.isPassenger() && Minecraft.getInstance().cameraEntity == entity && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            entity.moveTo(x, y, z, yaw, pitch);
        }

        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.setYHeadRot(yaw);
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.lerpHeadSteps = 0;
            livingEntity.yHeadRotO = livingEntity.yHeadRot;
            livingEntity.yRotO = livingEntity.getYRot();
            livingEntity.xRotO = livingEntity.getXRot();
        }
    }

}
