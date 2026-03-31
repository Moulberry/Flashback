package com.moulberry.flashback.mixin.playback;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.moulberry.flashback.Flashback;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerClockManager.class)
public class MixinServerClockManager {

    @WrapWithCondition(method = "modifyClock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
    public boolean modifyClock_broadcast(PlayerList instance, Packet<?> packet) {
        return !Flashback.isInReplay();
    }

}
