package com.moulberry.flashback.ext;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface ItemInHandRendererExt {

    void flashback$renderHandsWithItems(float f, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, AbstractClientPlayer localPlayer, int i, @Nullable Set<InteractionHand> renderable);

}
