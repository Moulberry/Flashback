package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.Interpolation;
import com.moulberry.flashback.combo_options.TrackingBodyPart;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.UUID;

public record KeyframeChangeTrackEntity(UUID target, TrackingBodyPart trackingBodyPart, float yawOffset, float pitchOffset,
                                        Vector3d positionOffset, Vector3d viewOffset) implements KeyframeChange {

    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        Minecraft minecraft = keyframeHandler.getMinecraft();
        if (minecraft == null) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntities().get(this.target);
        if (entity == null) {
            return;
        }

        float partialTick = minecraft.timer.getGameTimeDeltaPartialTick(true);

        Vec3 limbPosition;
        float yaw;
        float pitch;
        switch (this.trackingBodyPart) {
            case HEAD -> {
                limbPosition = entity.getEyePosition(partialTick);
                if (entity instanceof LivingEntity livingEntity) {
                    yaw = Mth.rotLerp(partialTick, livingEntity.yHeadRotO, livingEntity.yHeadRot);
                    pitch = livingEntity.getViewXRot(partialTick);
                } else {
                    yaw = entity.getYHeadRot();
                    pitch = entity.getXRot();
                }
            }
            case BODY -> {
                limbPosition = entity.getPosition(partialTick).add(0, entity.getBbHeight() * 0.5f, 0);
                if (entity instanceof LivingEntity livingEntity) {
                    yaw = Mth.rotLerp(partialTick, livingEntity.yBodyRotO, livingEntity.yBodyRot);
                } else {
                    yaw = entity.getYRot();
                }
                pitch = 0.0f;
            }
            case ROOT -> {
                limbPosition = entity.getPosition(partialTick);
                yaw = 0.0f;
                pitch = 0.0f;
            }
            default -> throw new UnsupportedOperationException();
        }

        pitch *= (float) Math.cos(Math.toRadians(this.yawOffset));
        yaw += this.yawOffset;
        pitch += this.pitchOffset;

        Vector3d offset = new Vector3d(this.viewOffset);
        offset.rotateX(Math.toRadians(-pitch));
        offset.rotateY(Math.toRadians(180-yaw));

        Vector3d position = new Vector3d(
            limbPosition.x + this.positionOffset.x + offset.x,
            limbPosition.y + this.positionOffset.y + offset.y,
            limbPosition.z + this.positionOffset.z + offset.z
        );

        LocalPlayer player = minecraft.player;
        if (player != null) {
            position.y -= player.getEyeHeight();
        }
        keyframeHandler.applyCameraPosition(position, yaw, pitch, 0.0f);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange to, double amount) {
        KeyframeChangeTrackEntity other = (KeyframeChangeTrackEntity) to;
        return new KeyframeChangeTrackEntity(
            amount < 0.5 ? this.target : other.target,
            amount < 0.5 ? this.trackingBodyPart : other.trackingBodyPart,
            (float) Interpolation.linear(this.yawOffset, other.yawOffset, amount),
            (float) Interpolation.linear(this.pitchOffset, other.pitchOffset, amount),
            this.positionOffset.lerp(other.positionOffset, amount, new Vector3d()),
            this.viewOffset.lerp(other.viewOffset, amount, new Vector3d())
        );
    }
}
