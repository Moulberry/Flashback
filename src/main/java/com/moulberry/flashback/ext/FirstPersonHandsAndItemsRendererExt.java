package com.moulberry.flashback.ext;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface FirstPersonHandsAndItemsRendererExt {

    void flashback$renderHandsWithItems(float frameInterp, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                        PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handState,
                                        AbstractClientPlayer spectatingPlayer, @Nullable Set<InteractionHand> renderableArms);

    void flashback$extractRenderState(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handState,
                                      AbstractClientPlayer spectatingPlayer, float partialTick);

}
