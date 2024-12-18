package com.moulberry.flashback;

import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.state.EditorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.function.Consumer;

public class EnhancedFlight {

    public static float getFlightSpeed(LocalPlayer player, FlashbackConfig config) {
        float momentum = (float) Math.sqrt(config.flightMomentum);
        float sprintMult = Mth.lerp(momentum, 2.5f, 2.0f);
        float baseMult = Mth.lerp(momentum, 10f, 1.0f);

        float sprintMultiplier = Minecraft.getInstance().options.keySprint.isDown() || player.isSprinting() ? sprintMult : 1f;
        return player.getAbilities().getFlyingSpeed() * sprintMultiplier * baseMult;
    }

    public static void doFlight(LocalPlayer player, FlashbackConfig config, Vec3 movementInput, float flyingSpeed, Consumer<Vec3> superTravel) {
        boolean cameraDirection = config.flightCameraDirection;

        boolean originalHasNoGravity = player.isNoGravity();
        double oldX = player.getX();
        double oldY = player.getY();
        double oldZ = player.getZ();

        if (cameraDirection) {
            double sin = -Math.sin(Math.toRadians(player.getXRot()));
            double cos = Math.cos(Math.toRadians(player.getXRot()));

            movementInput = new Vec3(movementInput.x, sin*movementInput.z, cos*movementInput.z);

            player.setNoGravity(true);
        }

        float movementY = 0;
        if (player.input.keyPresses.shift()) movementY -= 1;
        if (player.input.keyPresses.jump()) movementY += 1;
        if (movementY != 0) {
            player.move(MoverType.SELF, new Vec3(0, flyingSpeed * movementY * 0.98, 0));

            if (movementY < 0 && !Minecraft.getInstance().gameMode.isAlwaysFlying()) {
                double expectedY = oldY + flyingSpeed * movementY * 0.98;
                if (Math.abs(player.getY() - expectedY) > 1E-5) {
                    player.setOnGround(true);
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
        }

        double beforeY = player.getDeltaMovement().y;
        superTravel.accept(movementInput);

        double momentum = Math.cbrt(config.flightMomentum);
        Vector3f deltaMovement = player.getDeltaMovement().toVector3f();

        if (config.flightLockX) {
            player.setPos(oldX, player.getY(), player.getZ());
            deltaMovement.x = 0.0f;
        }
        if (config.flightLockY) {
            player.setPos(player.getX(), oldY, player.getZ());
            deltaMovement.y = 0.0f;
        }
        if (config.flightLockZ) {
            player.setPos(player.getX(), player.getY(), oldZ);
            deltaMovement.z = 0.0f;
        }

        if (cameraDirection) {
            player.setDeltaMovement(deltaMovement.x * momentum, deltaMovement.y * 0.92857 * momentum, deltaMovement.z * momentum);
        } else {
            player.setDeltaMovement(deltaMovement.x * momentum, beforeY * 0.6 * momentum, deltaMovement.z * momentum);
        }

        if (cameraDirection && movementY >= 0) {
            player.setOnGround(false);
        }

        player.setNoGravity(originalHasNoGravity);
        player.resetFallDistance();
        if (player.isFallFlying()) player.stopFallFlying();
    }

}
