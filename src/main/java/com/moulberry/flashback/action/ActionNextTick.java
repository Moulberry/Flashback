package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

public class ActionNextTick implements Action {

    private static final Identifier NAME = Flashback.createIdentifier("action/next_tick");
    public static final ActionNextTick INSTANCE = new ActionNextTick();
    private ActionNextTick() {
    }

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf friendlyByteBuf) {
        replayServer.handleNextTick();
    }

}
