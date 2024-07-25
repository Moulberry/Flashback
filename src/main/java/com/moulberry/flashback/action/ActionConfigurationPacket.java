package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionConfigurationPacket implements Action {

    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/configuration_packet");
    public static final ActionConfigurationPacket INSTANCE = new ActionConfigurationPacket();
    private ActionConfigurationPacket() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf friendlyByteBuf) {
        replayServer.handleConfigurationPacket(friendlyByteBuf);
    }

}
