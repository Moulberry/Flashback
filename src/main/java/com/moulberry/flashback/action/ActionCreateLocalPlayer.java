package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionCreateLocalPlayer implements Action {

    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/create_local_player");
    public static final ActionCreateLocalPlayer INSTANCE = new ActionCreateLocalPlayer();
    private ActionCreateLocalPlayer() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf friendlyByteBuf) {
        replayServer.handleCreateLocalPlayer(friendlyByteBuf);
    }

}
