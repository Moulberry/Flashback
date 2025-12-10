package com.moulberry.flashback.mixin.replay_server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.ext.ServerConfigurationPacketListenerImplExt;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.config.SynchronizeRegistriesTask;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Queue;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class MixinServerConfigurationPacketListenerImpl extends ServerCommonPacketListenerImpl implements ServerConfigurationPacketListenerImplExt {

    @Shadow
    @Final
    private Queue<ConfigurationTask> configurationTasks;

    @Shadow
    protected abstract void startNextTask();

    @Shadow
    private @Nullable SynchronizeRegistriesTask synchronizeRegistriesTask;

    @Shadow
    public abstract void returnToWorld();

    public MixinServerConfigurationPacketListenerImpl(MinecraftServer minecraftServer, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraftServer, connection, commonListenerCookie);
    }

    @WrapOperation(method = "handleConfigurationFinished", at = @At(value = "NEW", target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)Lnet/minecraft/server/level/ServerPlayer;"))
    public ServerPlayer newServerPlayer(MinecraftServer minecraftServer, ServerLevel serverLevel, GameProfile gameProfile, ClientInformation clientInformation, Operation<ServerPlayer> original) {
        if (minecraftServer instanceof ReplayServer replayServer) {
            return replayServer.createPlayer(serverLevel, gameProfile, clientInformation);
        } else {
            return original.call(minecraftServer, serverLevel, gameProfile, clientInformation);
        }
    }

    @Override
    public void flashback$startConfiguration(List<Packet<? super ClientConfigurationPacketListener>> initialPackets, List<ConfigurationTask> tasks) {
        for (Packet<? super ClientConfigurationPacketListener> initialPacket : initialPackets) {
            this.send(initialPacket);
        }

        for (ConfigurationTask task : tasks) {
            if (task instanceof SynchronizeRegistriesTask synchronizeRegistriesTask) {
                if (this.synchronizeRegistriesTask != null) {
                    throw new IllegalStateException("Duplicate SynchronizeRegistriesTask");
                }
                this.synchronizeRegistriesTask = synchronizeRegistriesTask;
            }
        }

        this.configurationTasks.addAll(tasks);
        this.returnToWorld();
    }

}
