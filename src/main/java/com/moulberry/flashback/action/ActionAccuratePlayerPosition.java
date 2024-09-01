package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionAccuratePlayerPosition implements Action {

    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/accurate_player_position_optional");
    public static final ActionAccuratePlayerPosition INSTANCE = new ActionAccuratePlayerPosition();
    private ActionAccuratePlayerPosition() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf friendlyByteBuf) {
        replayServer.handleAccuratePlayerPosition(friendlyByteBuf);
    }

}
