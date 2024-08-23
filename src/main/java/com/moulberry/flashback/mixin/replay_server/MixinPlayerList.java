package com.moulberry.flashback.mixin.replay_server;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.function.Function;

@Mixin(PlayerList.class)
public class MixinPlayerList {

    /*
     * Don't change the dimension of replay viewers when spawning them
     */

    @Shadow
    @Final
    private MinecraftServer server;

    @WrapOperation(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Ljava/util/Optional;flatMap(Ljava/util/function/Function;)Ljava/util/Optional;"))
    public Optional<ResourceKey<Level>> placeNewPlayer_flatMap(Optional instance, Function function, Operation<Optional<ResourceKey<Level>>> original,
            @Local(argsOnly = true) ServerPlayer serverPlayer) {
        if (serverPlayer.server instanceof ReplayServer) {
            return Optional.of(serverPlayer.level().dimension());
        }
        return original.call(instance, function);
    }

    @WrapWithCondition(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;[Ljava/lang/Object;)V", remap = false))
    public boolean placeNewPlayer_logInfo(Logger instance, String s, Object[] objects) {
        return !(this.server instanceof ReplayServer);
    }

}
