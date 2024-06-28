package com.moulberry.flashback.mixin.client.playback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.FlashbackClient;
import com.moulberry.flashback.ReplayVisuals;
import com.moulberry.flashback.ext.ItemInHandRendererExt;
import com.moulberry.flashback.ext.MinecraftExt;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    /*
     * Render item in hand for spectators in a replay
     */

    @Shadow
    @Final
    private Minecraft minecraft;

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    public GameType getPlayerMode(MultiPlayerGameMode instance, Operation<GameType> original) {
        if (FlashbackClient.getSpectatingPlayer() != null) {
            return GameType.SURVIVAL;
        }
        return original.call(instance);
    }

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    public void renderItemInHand_renderHandsWithItems(ItemInHandRenderer instance, float f, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LocalPlayer localPlayer, int i, Operation<Void> original) {
        AbstractClientPlayer spectatingPlayer = FlashbackClient.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            ((ItemInHandRendererExt)instance).flashback$renderHandsWithItems(f, poseStack, bufferSource, spectatingPlayer, i);
        } else {
            original.call(instance, f, poseStack, bufferSource, localPlayer, i);
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V"))
    public void renderLevel_setupCamera(Camera instance, BlockGetter blockGetter, Entity entity, boolean bl, boolean bl2, float f, Operation<Void> original) {
        if (FlashbackClient.isInReplay()) {
            f = ((MinecraftExt)this.minecraft).flashback$getLocalPlayerPartialTick();
        }
        original.call(instance, blockGetter, entity, bl, bl2, f);
    }

    @Inject(method = "tryTakeScreenshotIfNeeded", at = @At("HEAD"), cancellable = true)
    public void tryTakeScreenshotIfNeeded(CallbackInfo ci) {
        if (FlashbackClient.isInReplay()) {
            ci.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    public void getFov(Camera camera, float f, boolean bl, CallbackInfoReturnable<Double> cir) {
        if (FlashbackClient.isInReplay()) {
            if (!bl) {
                cir.setReturnValue(70.0);
                return;
            }

            int fov = this.minecraft.options.fov().get().intValue();
            if (ReplayVisuals.overrideFov > 0.0) {
                if (Math.abs(ReplayVisuals.overrideFov - fov) < 1) {
                    cir.setReturnValue((double) ReplayVisuals.overrideFov);
                    return;
                } else {
                    ReplayVisuals.overrideFov = 0.0f;
                }
            }
            cir.setReturnValue((double) fov);
        }
    }

}
