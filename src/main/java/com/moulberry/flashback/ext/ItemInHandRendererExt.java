package com.moulberry.flashback.ext;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;

public interface ItemInHandRendererExt {

    void flashback$renderHandsWithItems(float f, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, AbstractClientPlayer localPlayer, int i);

}
