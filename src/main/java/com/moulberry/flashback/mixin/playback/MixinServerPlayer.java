package com.moulberry.flashback.mixin.playback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.playback.FlashbackFakePlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public class MixinServerPlayer {

    @Shadow
    @Final
    private MinecraftServer server;

    @WrapOperation(method = {"tick", "doTick"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    public boolean tick_isContainerStillValid(AbstractContainerMenu instance, Player player, Operation<Boolean> original) {
        return this.server instanceof ReplayServer || player instanceof FlashbackFakePlayer || original.call(instance, player);
    }

}
