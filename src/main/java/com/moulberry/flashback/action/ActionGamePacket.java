package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionGamePacket implements Action {

    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/game_packet");
    public static final ActionGamePacket INSTANCE = new ActionGamePacket();
    private ActionGamePacket() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf friendlyByteBuf) {
        replayServer.handleGamePacket(friendlyByteBuf);
    }

}
