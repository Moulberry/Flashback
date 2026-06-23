package com.moulberry.flashback.mixin.playback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.ItemInHandRendererExt;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    /*
     * Render item in hand for spectators in a replay
     */

    @Shadow
    @Final
    Minecraft minecraft;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V", remap = false, ordinal = 0), cancellable = true)
    public void render_noGui(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().noGui()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    public GameType getPlayerMode(MultiPlayerGameMode instance, Operation<GameType> original) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            return Objects.requireNonNullElse(spectatingPlayer.gameMode(), GameType.SURVIVAL);
        }
        return original.call(instance);
    }

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    public void renderItemInHand_submitHandsWithItems(ItemInHandRenderer instance, final float frameInterp, final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector, final LocalPlayer player, final int lightCoords, Operation<Void> original
    ) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            Entity entity = this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity();
            float frozenPartialTick = this.minecraft.level.tickRateManager().isEntityFrozen(entity) ? 1.0f : frameInterp;
            ((ItemInHandRendererExt)instance).flashback$renderHandsWithItems(frozenPartialTick, poseStack, submitNodeCollector, spectatingPlayer, lightCoords, null);
        } else {
            original.call(instance, frameInterp, poseStack, submitNodeCollector, player, lightCoords);
        }
    }

    @Inject(method = "tryTakeScreenshotIfNeeded", at = @At("HEAD"), cancellable = true)
    public void tryTakeScreenshotIfNeeded(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    public void shouldRenderBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        if (Flashback.isInReplay()) {
            var cameraEntity = Minecraft.getInstance().getCameraEntity();
            if (cameraEntity == this.minecraft.player && cameraEntity.isSpectator()) {
                cir.setReturnValue(false);
            }
        }
    }

}
