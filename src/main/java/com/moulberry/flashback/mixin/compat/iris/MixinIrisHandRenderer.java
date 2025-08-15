package com.moulberry.flashback.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.ItemInHandRendererExt;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.irisshaders.iris.pathways.HandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumSet;
import java.util.Objects;

@IfModLoaded("iris")
@Pseudo
@Mixin(value = HandRenderer.class)
public abstract class MixinIrisHandRenderer {

    @Shadow
    public abstract boolean isHandTranslucent(InteractionHand hand);

    @Unique
    private static final InteractionHand[] HANDS = InteractionHand.values();

    @Inject(method = "isHandTranslucent", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    public void isHandTransluent(InteractionHand hand, CallbackInfoReturnable<Boolean> cir) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            var item = spectatingPlayer.getItemBySlot(hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND).getItem();

            if (item instanceof BlockItem blockItem) {
                cir.setReturnValue(ItemBlockRenderTypes.getChunkRenderType(blockItem.getBlock().defaultBlockState()) == RenderType.translucent());
            } else {
                cir.setReturnValue(false);
            }
        }
    }

    @WrapOperation(method = "canRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"), require = 0)
    public GameType getPlayerMode(MultiPlayerGameMode instance, Operation<GameType> original) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            var playerInfo = spectatingPlayer.getPlayerInfo();
            if (playerInfo == null) {
                return GameType.SURVIVAL;
            }
            return playerInfo.getGameMode();
        }
        return original.call(instance);
    }

    @WrapOperation(method = "renderSolid", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"), require = 0)
    public void renderSolid_renderHandsWithItems(ItemInHandRenderer instance, float f, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LocalPlayer localPlayer, int i, Operation<Void> original) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            EnumSet<InteractionHand> renderableArms = EnumSet.noneOf(InteractionHand.class);
            for (InteractionHand hand : HANDS) {
                if (!this.isHandTranslucent(hand)) renderableArms.add(hand);
            }
            float frozenPartialTick = Minecraft.getInstance().level.tickRateManager().isEntityFrozen(spectatingPlayer) ? 1.0f : f;
            ((ItemInHandRendererExt)instance).flashback$renderHandsWithItems(frozenPartialTick, poseStack, bufferSource, spectatingPlayer, i, renderableArms);
        } else {
            original.call(instance, f, poseStack, bufferSource, localPlayer, i);
        }
    }

    @WrapOperation(method = "renderTranslucent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"), require = 0)
    public void renderTranslucent_renderHandsWithItems(ItemInHandRenderer instance, float f, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LocalPlayer localPlayer, int i, Operation<Void> original) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            EnumSet<InteractionHand> renderableArms = EnumSet.noneOf(InteractionHand.class);
            for (InteractionHand hand : HANDS) {
                if (this.isHandTranslucent(hand)) renderableArms.add(hand);
            }
            float frozenPartialTick = Minecraft.getInstance().level.tickRateManager().isEntityFrozen(spectatingPlayer) ? 1.0f : f;
            ((ItemInHandRendererExt)instance).flashback$renderHandsWithItems(frozenPartialTick, poseStack, bufferSource, spectatingPlayer, i, renderableArms);
        } else {
            original.call(instance, f, poseStack, bufferSource, localPlayer, i);
        }
    }

}
