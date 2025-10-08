package com.moulberry.flashback.mixin.replay_server;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PrepareSpawnTask.Ready.class)
public class MixinPrepareSpawnTask {

    @Shadow @Final private ServerLevel spawnLevel;

    /*
     * Prevent replay viewer being sent to default spawn dimension/location
     */

    @WrapOperation(method = "spawn", at = @At(value = "NEW", target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)Lnet/minecraft/server/level/ServerPlayer;"))
    public ServerPlayer newServerPlayer(MinecraftServer minecraftServer, ServerLevel serverLevel, GameProfile gameProfile, ClientInformation clientInformation, Operation<ServerPlayer> original) {
        if (minecraftServer instanceof ReplayServer replayServer) {
            return replayServer.createPlayer(serverLevel, gameProfile, clientInformation);
        } else {
            return original.call(minecraftServer, serverLevel, gameProfile, clientInformation);
        }
    }

    @WrapWithCondition(method = "spawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;snapTo(Lnet/minecraft/world/phys/Vec3;FF)V"))
    public boolean spawn_snapTo(ServerPlayer instance, Vec3 position, float pitch, float yaw) {
        return !(this.spawnLevel.getServer() instanceof ReplayServer);
    }

}
