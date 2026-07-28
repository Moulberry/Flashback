package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

public class ActionRealTimeClock implements Action {

    private static final Identifier NAME = Flashback.createIdentifier("action/real_time_clock_optional");
    public static final ActionRealTimeClock INSTANCE = new ActionRealTimeClock();
    private ActionRealTimeClock() {
    }

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf friendlyByteBuf) {
        byte delta = friendlyByteBuf.readByte();

        if (delta == 0) {
            replayServer.setTimeRtc(friendlyByteBuf.readLong());
        } else {
            replayServer.updateTimeRtc(delta);
        }
    }

}
