package com.moulberry.flashback.mixin.replay_server;

import com.moulberry.flashback.ext.ServerConfigurationPacketListenerImplExt;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.config.SynchronizeRegistriesTask;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

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

    public MixinServerConfigurationPacketListenerImpl(MinecraftServer minecraftServer, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraftServer, connection, commonListenerCookie);
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
        this.startNextTask();
    }

}
