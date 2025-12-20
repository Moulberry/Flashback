package com.moulberry.flashback.mixin.replay_server;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.playback.FakePlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.impl.event.interaction.FakePlayerNetworkHandler;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerList.class)
public class MixinPlayerList {

    @Shadow
    @Final
    private MinecraftServer server;

    @WrapWithCondition(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;[Ljava/lang/Object;)V", remap = false))
    public boolean placeNewPlayer_logInfo(Logger instance, String s, Object[] objects) {
        return !(this.server instanceof ReplayServer);
    }

    @WrapOperation(method = "placeNewPlayer", at = @At(value = "NEW", target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"))
    public ServerGamePacketListenerImpl placeNewPlayer_newServerGamePacketListener(MinecraftServer minecraftServer, Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie, Operation<ServerGamePacketListenerImpl> original) {
        if (serverPlayer instanceof FakePlayer) {
            return new FakePlayerNetworkHandler(serverPlayer);
        }
        return original.call(minecraftServer, connection, serverPlayer, commonListenerCookie);
    }

}
