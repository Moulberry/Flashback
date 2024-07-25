package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionLevelChunkCached implements Action {

    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/level_chunk_cached");
    public static final ActionLevelChunkCached INSTANCE = new ActionLevelChunkCached();
    private ActionLevelChunkCached() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf friendlyByteBuf) {
        replayServer.handleLevelChunkCached(friendlyByteBuf.readVarInt());
    }

}
