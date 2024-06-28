package com.moulberry.flashback.ext;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;

import java.util.List;

public interface ServerGamePacketListenerImplExt {

    void flashback$switchToConfigWithTasks(List<Packet<? super ClientConfigurationPacketListener>> initialPackets, List<ConfigurationTask> tasks);

}
