package com.moulberry.flashback.ext;

import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;

public interface RemotePlayerExt {

    void flashback$extractFirstPersonHandsAndItems(float partialTick, FirstPersonHandsAndItemsRenderState state);

}
