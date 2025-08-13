package com.moulberry.flashback.mixin.playback;

import com.google.common.base.MoreObjects;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.ItemInHandRendererExt;
import com.moulberry.flashback.ext.RemotePlayerExt;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer implements ItemInHandRendererExt {

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    protected abstract void renderArmWithItem(AbstractClientPlayer abstractClientPlayer, float f, float g, InteractionHand interactionHand, float h, ItemStack itemStack, float i, PoseStack poseStack, MultiBufferSource multiBufferSource, int j);

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    private static boolean isChargedCrossbow(ItemStack itemStack) {
        return false;
    }

    @Unique
    private static final int RENDER_MAIN_HAND = 1;
    @Unique
    private static final int RENDER_OFF_HAND = 2;
    @Unique
    private static final int RENDER_BOTH_HANDS = RENDER_MAIN_HAND | RENDER_OFF_HAND;

    @Unique
    private static int evaluateWhichHandsToRender(AbstractClientPlayer player) {
        ItemStack mainStack = player.getMainHandItem();
        ItemStack offStack = player.getOffhandItem();
        boolean isHoldingBow = mainStack.is(Items.BOW) || offStack.is(Items.BOW);
        boolean isHoldingCrossbow = mainStack.is(Items.CROSSBOW) || offStack.is(Items.CROSSBOW);
        if (!isHoldingBow && !isHoldingCrossbow) {
            return RENDER_BOTH_HANDS;
        }
        if (player.isUsingItem()) {
            ItemStack useStack = player.getUseItem();
            InteractionHand interactionHand = player.getUsedItemHand();
            if (!useStack.is(Items.BOW) && !useStack.is(Items.CROSSBOW)) {
                return interactionHand == InteractionHand.MAIN_HAND && isChargedCrossbow(player.getOffhandItem()) ? RENDER_MAIN_HAND : RENDER_BOTH_HANDS;
            } else {
                return interactionHand == InteractionHand.MAIN_HAND ? RENDER_MAIN_HAND : RENDER_OFF_HAND;
            }
        }
        if (isChargedCrossbow(mainStack)) {
            return RENDER_MAIN_HAND;
        }
        return RENDER_BOTH_HANDS;
    }

    @Override
    public void flashback$renderHandsWithItems(float partialTick, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, AbstractClientPlayer clientPlayer, int i) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.hideDuringExport.contains(clientPlayer.getUUID())) {
            return;
        }

        float m;
        float l;
        float g = clientPlayer.getAttackAnim(partialTick);
        InteractionHand interactionHand = MoreObjects.firstNonNull(clientPlayer.swingingArm, InteractionHand.MAIN_HAND);
        float h = Mth.lerp(partialTick, clientPlayer.xRotO, clientPlayer.getXRot());
         int handRenderSelection = evaluateWhichHandsToRender(clientPlayer);
        if (clientPlayer instanceof RemotePlayerExt remotePlayerExt) {
            float xBob = remotePlayerExt.flashback$getXBob(partialTick);
            float yBob = remotePlayerExt.flashback$getYBob(partialTick);
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.wrapDegrees(clientPlayer.getViewXRot(partialTick) - xBob) * 0.1f));
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(clientPlayer.getViewYRot(partialTick) - yBob) * 0.1f));
        }
        if ((handRenderSelection & RENDER_MAIN_HAND) != 0) {
            l = interactionHand == InteractionHand.MAIN_HAND ? g : 0.0f;
            m = 1.0f - Mth.lerp(partialTick, this.oMainHandHeight, this.mainHandHeight);
            renderArmWithItem(clientPlayer, partialTick, h, InteractionHand.MAIN_HAND, l, this.mainHandItem, m, poseStack, bufferSource, i);
        }
        if ((handRenderSelection & RENDER_OFF_HAND) != 0) {
            l = interactionHand == InteractionHand.OFF_HAND ? g : 0.0f;
            m = 1.0f - Mth.lerp(partialTick, this.oOffHandHeight, this.offHandHeight);
            renderArmWithItem(clientPlayer, partialTick, h, InteractionHand.OFF_HAND, l, this.offHandItem, m, poseStack, bufferSource, i);
        }
        bufferSource.endBatch();
    }

    @Inject(method = "renderPlayerArm", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;entityRenderDispatcher:Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;"))
    public void renderPlayerArm(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, float f, float g,
                                HumanoidArm humanoidArm, CallbackInfo ci, @Local LocalRef<AbstractClientPlayer> player) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            player.set(spectatingPlayer);
        }
    }

    @Unique
    private UUID lastSpectatingPlayer = null;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void tick(CallbackInfo ci) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer == null) {
            this.lastSpectatingPlayer = null;
        } else {
            ItemStack newMainHandItem = spectatingPlayer.getMainHandItem();
            ItemStack newOffHandItem = spectatingPlayer.getOffhandItem();

            if (!spectatingPlayer.getUUID().equals(lastSpectatingPlayer)) {
                this.lastSpectatingPlayer = spectatingPlayer.getUUID();
                this.mainHandItem = newMainHandItem;
                this.offHandItem = newOffHandItem;
                this.oMainHandHeight = this.mainHandHeight = 1.0f;
                this.oOffHandHeight = this.offHandHeight = 1.0f;
                ci.cancel();
                return;
            }

            this.oMainHandHeight = this.mainHandHeight;
            this.oOffHandHeight = this.offHandHeight;
            if (ItemStack.matches(this.mainHandItem, newMainHandItem)) {
                this.mainHandItem = newMainHandItem;
            }
            if (ItemStack.matches(this.offHandItem, newOffHandItem)) {
                this.offHandItem = newOffHandItem;
            }
            float str = spectatingPlayer.getAttackStrengthScale(1.0f);
            this.mainHandHeight += Mth.clamp((this.mainHandItem == newMainHandItem ? str * str * str : 0.0f) - this.mainHandHeight, -0.4f, 0.4f);
            this.offHandHeight += Mth.clamp((float)(this.offHandItem == newOffHandItem ? 1 : 0) - this.offHandHeight, -0.4f, 0.4f);
            if (this.mainHandHeight < 0.1f) {
                this.mainHandItem = newMainHandItem;
            }
            if (this.offHandHeight < 0.1f) {
                this.offHandItem = newOffHandItem;
            }
            ci.cancel();
        }
    }

}
