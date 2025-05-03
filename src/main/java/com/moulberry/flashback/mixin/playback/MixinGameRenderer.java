package com.moulberry.flashback.mixin.playback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.ext.ItemInHandRendererExt;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import com.moulberry.flashback.visuals.CameraRotation;
import com.moulberry.flashback.visuals.ReplayVisuals;
import com.moulberry.flashback.visuals.ShaderManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {


    @WrapOperation(method = "render", at=@At(value = "FIELD", target = "Lnet/minecraft/client/Options;pauseOnLostFocus:Z"))
    public boolean getPauseOnLostFocus(Options instance, Operation<Boolean> original) {
        if (ReplayUI.isActive() || Flashback.EXPORT_JOB != null) {
            return false;
        }
        return original.call(instance);
    }

    /*
     * Render item in hand for spectators in a replay
     */

    @Shadow
    @Final
    Minecraft minecraft;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", remap = false, ordinal = 0), cancellable = true)
    public void render_noGui(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().noGui()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    public GameType getPlayerMode(MultiPlayerGameMode instance, Operation<GameType> original) {
        if (Flashback.getSpectatingPlayer() != null) {
            return GameType.SURVIVAL;
        }
        return original.call(instance);
    }

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    public void renderItemInHand_renderHandsWithItems(ItemInHandRenderer instance, float f, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LocalPlayer localPlayer, int i, Operation<Void> original) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            Entity entity = this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity();
            float frozenPartialTick = this.minecraft.level.tickRateManager().isEntityFrozen(entity) ? 1.0f : f;
            ((ItemInHandRendererExt)instance).flashback$renderHandsWithItems(frozenPartialTick, poseStack, bufferSource, spectatingPlayer, i);
        } else {
            original.call(instance, f, poseStack, bufferSource, localPlayer, i);
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V"))
    public void renderLevel_setupCamera(Camera instance, BlockGetter blockGetter, Entity entity, boolean bl, boolean bl2, float f, Operation<Void> original) {
        if (Flashback.isInReplay()) {
            f = ((MinecraftExt)this.minecraft).flashback$getLocalPlayerPartialTick(f);
        }
        original.call(instance, blockGetter, entity, bl, bl2, f);
    }

    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void getNightVisionScale(LivingEntity livingEntity, float f, CallbackInfoReturnable<Float> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideNightVision) {
            cir.setReturnValue(1.0f);
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;rotation()Lorg/joml/Quaternionf;"))
    public Quaternionf renderLevel(Camera instance, Operation<Quaternionf> original) {
        return CameraRotation.modifyViewQuaternion(original.call(instance));
    }

    @Inject(method = "tryTakeScreenshotIfNeeded", at = @At("HEAD"), cancellable = true)
    public void tryTakeScreenshotIfNeeded(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    public void getFov(Camera camera, float f, boolean bl, CallbackInfoReturnable<Double> cir) {
        if (Flashback.isInReplay()) {
            if (!bl) {
                cir.setReturnValue(70.0);
                return;
            }
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && editorState.replayVisuals.overrideFov) {
                cir.setReturnValue((double) editorState.replayVisuals.overrideFovAmount);
            } else {
                int fov = this.minecraft.options.fov().get().intValue();
                cir.setReturnValue((double) fov);
            }
        }
    }

}
