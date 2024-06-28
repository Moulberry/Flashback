package com.moulberry.flashback.mixin.client.playback;

import com.google.common.base.MoreObjects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.FlashbackClient;
import com.moulberry.flashback.ext.ItemInHandRendererExt;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

    @Override
    public void flashback$renderHandsWithItems(float partialTick, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, AbstractClientPlayer clientPlayer, int i) {
        float m;
        float l;
        float g = clientPlayer.getAttackAnim(partialTick);
        InteractionHand interactionHand = MoreObjects.firstNonNull(clientPlayer.swingingArm, InteractionHand.MAIN_HAND);
        float h = Mth.lerp(partialTick, clientPlayer.xRotO, clientPlayer.getXRot());
        // todo: hand selection
        // ItemInHandRenderer.HandRenderSelection handRenderSelection = evaluateWhichHandsToRender(clientPlayer);
        // todo: bob
//        float j = Mth.lerp((float)f, (float)clientPlayer.xBobO, (float)clientPlayer.xBob);
//        float k = Mth.lerp((float)f, (float)clientPlayer.yBobO, (float)clientPlayer.yBob);
//        poseStack.mulPose(Axis.XP.rotationDegrees((clientPlayer.getViewXRot(f) - j) * 0.1f));
//        poseStack.mulPose(Axis.YP.rotationDegrees((clientPlayer.getViewYRot(f) - k) * 0.1f));
        if (true) {
            l = interactionHand == InteractionHand.MAIN_HAND ? g : 0.0f;
            m = 1.0f - Mth.lerp(partialTick, this.oMainHandHeight, this.mainHandHeight);
            renderArmWithItem(clientPlayer, partialTick, h, InteractionHand.MAIN_HAND, l, this.mainHandItem, m, poseStack, bufferSource, i);
        }
        if (true) {
            l = interactionHand == InteractionHand.OFF_HAND ? g : 0.0f;
            m = 1.0f - Mth.lerp(partialTick, this.oOffHandHeight, this.offHandHeight);
            renderArmWithItem(clientPlayer, partialTick, h, InteractionHand.OFF_HAND, l, this.offHandItem, m, poseStack, bufferSource, i);
        }
        bufferSource.endBatch();
    }

    // todo: change first person FOV when pulling bows, etc.

    // todo: show arm (need to replace usage of LocalPlayer in renderPlayerArm

    @Unique
    private UUID lastSpectatingPlayer = null;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void tick(CallbackInfo ci) {
        AbstractClientPlayer spectatingPlayer = FlashbackClient.getSpectatingPlayer();
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
            if (false /*clientPlayer.isHandsBusy()*/) {
                this.mainHandHeight = Mth.clamp((float)(this.mainHandHeight - 0.4f), (float)0.0f, (float)1.0f);
                this.offHandHeight = Mth.clamp((float)(this.offHandHeight - 0.4f), (float)0.0f, (float)1.0f);
            } else {
                // todo: attack strength
                float f = 1.0f;//spectatingPlayer.getAttackStrengthScale(1.0f);
                this.mainHandHeight += Mth.clamp((this.mainHandItem == newMainHandItem ? f * f * f : 0.0f) - this.mainHandHeight, -0.4f, 0.4f);
                this.offHandHeight += Mth.clamp((float)(this.offHandItem == newOffHandItem ? 1 : 0) - this.offHandHeight, -0.4f, 0.4f);
            }
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
