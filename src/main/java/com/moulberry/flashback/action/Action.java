package com.moulberry.flashback.action;

import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

public interface Action {

    Identifier name();
    void handle(ReplayServer replayServer, RegistryFriendlyByteBuf friendlyByteBuf);

}
