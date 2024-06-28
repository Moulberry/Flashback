package com.moulberry.flashback.mixin.client.replay_server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.function.Function;

@Mixin(PlayerList.class)
public class MixinPlayerList {

    /*
     * Don't change the dimension of replay viewers when spawning them
     */

    @WrapOperation(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Ljava/util/Optional;flatMap(Ljava/util/function/Function;)Ljava/util/Optional;"))
    public Optional<ResourceKey<Level>> placeNewPlayer_flatMap(Optional instance, Function function, Operation<Optional<ResourceKey<Level>>> original,
            @Local(argsOnly = true) ServerPlayer serverPlayer) {
        if (serverPlayer.server instanceof ReplayServer) {
            return Optional.of(serverPlayer.level().dimension());
        }
        return original.call(instance, function);
    }

}
