package com.moulberry.flashback.mixin.replay_server;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class MixinServerLoginPacketListenerImpl {

    @Shadow
    @Final
    MinecraftServer server;

    @Shadow
    abstract void startClientVerification(GameProfile gameProfile);

    @Shadow
    @Nullable
    String requestedUsername;

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    public void handleHello(ServerboundHelloPacket serverboundHelloPacket, CallbackInfo ci) {
        if (this.server instanceof ReplayServer) {
            this.requestedUsername = ReplayServer.REPLAY_VIEWER_NAME;
            UUID replayViewerUUID = UUID.nameUUIDFromBytes(serverboundHelloPacket.name().getBytes(StandardCharsets.UTF_8));
            GameProfile gameProfile = new GameProfile(replayViewerUUID, ReplayServer.REPLAY_VIEWER_NAME);
            gameProfile.getProperties().put("IsReplayViewer", new Property("IsReplayViewer", "True"));
            this.startClientVerification(gameProfile);
            ci.cancel();
        }
    }

}
