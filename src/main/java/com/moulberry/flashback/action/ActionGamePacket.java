package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

public class ActionGamePacket implements Action {

    private static final Identifier NAME = Flashback.createIdentifier("action/game_packet");
    public static final ActionGamePacket INSTANCE = new ActionGamePacket();
    private ActionGamePacket() {
    }

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf friendlyByteBuf) {
        replayServer.handleGamePacket(friendlyByteBuf);
    }

}
