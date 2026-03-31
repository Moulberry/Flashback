package com.moulberry.flashback.mixin.replay_server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mixin(ServerEntity.class)
public class MixinServerEntity {

    /*
     * Force update interval to be 1 on a replay server, sending updates as soon as possible
     */

    @Shadow public List<SynchedEntityData.DataValue<?>> trackedDataValues;

    @Shadow public Entity entity;

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static int init_modifyUpdateInterval(int updateInterval, @Local(argsOnly = true) ServerLevel level) {
        if (updateInterval < 20 && level != null && level.getServer() instanceof ReplayServer) {
            return 1;
        }
        return updateInterval;
    }

    /*
     * Fix a bug where hand animations will be wrong due to incorrect packet order
     */
    @WrapOperation(method = "addPairing", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerEntity;sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V"))
    public void addPairing_sendPairingData(ServerEntity instance, ServerPlayer serverPlayer, Consumer<Packet<ClientGamePacketListener>> consumer, Operation<Void> original) {
        if (Flashback.isInReplay()) {
            List<ClientboundSetEntityDataPacket> delayed = new ArrayList<>();

            original.call(instance, serverPlayer, (Consumer<Packet<ClientGamePacketListener>>) packet -> {
                if (packet instanceof ClientboundSetEntityDataPacket setEntityDataPacket) {
                    delayed.add(setEntityDataPacket);
                } else {
                    consumer.accept(packet);
                }
            });

            for (ClientboundSetEntityDataPacket setEntityDataPacket : delayed) {
                consumer.accept(setEntityDataPacket);
            }
        } else {
            original.call(instance, serverPlayer, consumer);
        }
    }

}
