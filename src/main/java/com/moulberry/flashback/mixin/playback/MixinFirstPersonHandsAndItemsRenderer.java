package com.moulberry.flashback.mixin.playback;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.FirstPersonHandsAndItemsRendererExt;
import com.moulberry.flashback.ext.RemotePlayerExt;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MixinFirstPersonHandsAndItemsRenderer implements FirstPersonHandsAndItemsRendererExt {

    @Shadow
    private Minecraft minecraft;

    @Shadow
    protected abstract void submitArmWithItem(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state,
                                              float partialTicks, float xRot, InteractionHand hand, float attack, ItemStack itemStack,
                                              float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords);

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    public void submitHandsWithItems(float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                     PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        // When spectating another player the hands come from flashback$renderHandsWithItems instead
        if (Flashback.getSpectatingPlayer() != null) {
            ci.cancel();
        }
    }

    @Override
    public void flashback$renderHandsWithItems(float frameInterp, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                               PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state,
                                               AbstractClientPlayer clientPlayer, @Nullable Set<InteractionHand> renderableArms) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.isEntityHidden(clientPlayer)) {
            return;
        }

        if (playerState.avatarRenderState == null) {
            return;
        }

        if (clientPlayer instanceof RemotePlayerExt remotePlayerExt) {
            float xBob = remotePlayerExt.flashback$getXBob(frameInterp);
            float yBob = remotePlayerExt.flashback$getYBob(frameInterp);
            poseStack.rotateDegrees(Axis.XP, Mth.wrapDegrees(clientPlayer.getViewXRot(frameInterp) - xBob) * 0.1f);
            poseStack.rotateDegrees(Axis.YP, Mth.wrapDegrees(clientPlayer.getViewYRot(frameInterp) - yBob) * 0.1f);
        }

        boolean renderMainHand = state.handRenderSelection == null || state.handRenderSelection.renderMainHand;
        boolean renderOffHand = state.handRenderSelection == null || state.handRenderSelection.renderOffHand;
        if (renderableArms != null) {
            renderMainHand &= renderableArms.contains(InteractionHand.MAIN_HAND);
            renderOffHand &= renderableArms.contains(InteractionHand.OFF_HAND);
        }

        AvatarRenderState avatarRenderState = playerState.avatarRenderState;
        float attackValue = avatarRenderState.swingAnimation;
        InteractionHand attackHand = state.attackHand == null ? InteractionHand.MAIN_HAND : state.attackHand;
        float xRot = avatarRenderState.xRot;
        int lightCoords = avatarRenderState.lightCoords;

        if (renderMainHand) {
            float attack = attackHand == InteractionHand.MAIN_HAND ? attackValue : 0.0f;
            float inverseArmHeight = state.mainHandSwapScale * (1.0f - Mth.lerp(frameInterp, state.oldMainHandHeight, state.mainHandHeight));
            this.submitArmWithItem(playerState, state, frameInterp, xRot, InteractionHand.MAIN_HAND, attack,
                state.mainHandItem, inverseArmHeight, poseStack, submitNodeCollector, lightCoords);
        }

        if (renderOffHand) {
            float attack = attackHand == InteractionHand.OFF_HAND ? attackValue : 0.0f;
            float inverseArmHeight = state.offHandSwapScale * (1.0f - Mth.lerp(frameInterp, state.oldOffHandHeight, state.offHandHeight));
            this.submitArmWithItem(playerState, state, frameInterp, xRot, InteractionHand.OFF_HAND, attack,
                state.offHandItem, inverseArmHeight, poseStack, submitNodeCollector, lightCoords);
        }
    }

    /**
     * Populates a render state for the player being spectated, mirroring what LevelExtractor does for the local player.
     */
    @Override
    public void flashback$extractRenderState(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handState,
                                             AbstractClientPlayer clientPlayer, float partialTick) {
        if (!(this.minecraft.getEntityRenderDispatcher().extractEntity(clientPlayer, partialTick) instanceof AvatarRenderState avatarRenderState)) {
            return;
        }

        playerState.hasPlayer = true;
        playerState.avatarRenderState = avatarRenderState;
        this.flashback$extractHands(handState, clientPlayer, avatarRenderState, partialTick);
    }

    @Unique
    private void flashback$extractHands(FirstPersonHandsAndItemsRenderState state, AbstractClientPlayer player,
                                        AvatarRenderState avatarRenderState, float partialTicks) {
        LivingEntity.SwingDescription currentSwing = player.getCurrentSwing();
        state.attackHand = currentSwing == null ? InteractionHand.MAIN_HAND : currentSwing.hand();
        state.viewXRot = player.getViewXRot(partialTicks);
        state.viewYRot = player.getViewYRot(partialTicks);
        state.xBob = player instanceof RemotePlayerExt remotePlayerExt ? remotePlayerExt.flashback$getXBob(partialTicks) : 0.0f;
        state.yBob = player instanceof RemotePlayerExt remotePlayerExt ? remotePlayerExt.flashback$getYBob(partialTicks) : 0.0f;
        state.isScoping = player.isScoping();
        state.useItemRemainingTicks = player.getUseItemRemainingTicks();
        state.handRenderSelection = flashback$evaluateWhichHandsToRender(player);
        state.mainHandItem = player.getMainHandItem();
        state.offHandItem = player.getOffhandItem();
        state.mainHandHeight = 1.0f;
        state.oldMainHandHeight = 1.0f;
        state.offHandHeight = 1.0f;
        state.oldOffHandHeight = 1.0f;

        boolean isMainHandRight = avatarRenderState.mainArm == HumanoidArm.RIGHT;
        ItemDisplayContext mainHandDisplayContext = isMainHandRight ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        ItemDisplayContext offHandDisplayContext = isMainHandRight ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        state.mainHandRenderState.clear();
        state.offHandRenderState.clear();
        this.minecraft.getItemModelResolver().updateForTopItem(state.mainHandRenderState, state.mainHandItem, mainHandDisplayContext, player.level(), player, player.getId() + mainHandDisplayContext.ordinal());
        this.minecraft.getItemModelResolver().updateForTopItem(state.offHandRenderState, state.offHandItem, offHandDisplayContext, player.level(), player, player.getId() + offHandDisplayContext.ordinal());

        state.mainHandUseDuration = state.mainHandItem.getUseDuration(player);
        state.offHandUseDuration = state.offHandItem.getUseDuration(player);
        state.mainHandChargeDuration = CrossbowItem.getChargeDuration(state.mainHandItem, player);
        state.offHandChargeDuration = CrossbowItem.getChargeDuration(state.offHandItem, player);
        state.mainHandSwapScale = this.minecraft.getItemModelResolver().swapAnimationScale(state.mainHandItem);
        state.offHandSwapScale = this.minecraft.getItemModelResolver().swapAnimationScale(state.offHandItem);
        state.hasMainHandMapData = this.flashback$extractMapRenderState(player, state.mainHandItem, state.mainHandMapRenderState);
        state.hasOffHandMapData = this.flashback$extractMapRenderState(player, state.offHandItem, state.offHandMapRenderState);
    }

    @Unique
    private static FirstPersonHandsAndItemsRenderState.HandRenderSelection flashback$evaluateWhichHandsToRender(AbstractClientPlayer player) {
        ItemStack mainHandItem = player.getMainHandItem();
        ItemStack offhandItem = player.getOffhandItem();
        boolean holdsBow = mainHandItem.is(Items.BOW) || offhandItem.is(Items.BOW);
        boolean holdsCrossbow = mainHandItem.is(Items.CROSSBOW) || offhandItem.is(Items.CROSSBOW);
        if (!holdsBow && !holdsCrossbow) {
            return FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_BOTH_HANDS;
        } else if (!player.isUsingItem()) {
            return flashback$isChargedCrossbow(mainHandItem)
                ? FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_MAIN_HAND_ONLY
                : FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_BOTH_HANDS;
        } else {
            ItemStack usedItemStack = player.getUseItem();
            InteractionHand usedHand = player.getUsedItemHand();
            if (!usedItemStack.is(Items.BOW) && !usedItemStack.is(Items.CROSSBOW)) {
                return usedHand == InteractionHand.MAIN_HAND && flashback$isChargedCrossbow(offhandItem)
                    ? FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_MAIN_HAND_ONLY
                    : FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_BOTH_HANDS;
            } else {
                return FirstPersonHandsAndItemsRenderState.HandRenderSelection.onlyForHand(usedHand);
            }
        }
    }

    @Unique
    private static boolean flashback$isChargedCrossbow(ItemStack item) {
        return item.is(Items.CROSSBOW) && CrossbowItem.isCharged(item);
    }

    @Unique
    private boolean flashback$extractMapRenderState(AbstractClientPlayer player, ItemStack itemStack, MapRenderState state) {
        MapId mapId = itemStack.get(DataComponents.MAP_ID);
        MapItemSavedData mapData = mapId == null ? null : MapItem.getSavedData(mapId, player.level());
        if (mapId != null && mapData != null) {
            this.minecraft.getMapRenderer().extractRenderState(mapId, mapData, state);
            return true;
        }
        return false;
    }

}
