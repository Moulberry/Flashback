package com.moulberry.flashback.mixin.playback;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.RemotePlayerExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RemotePlayer.class)
public class MixinRemotePlayer extends AbstractClientPlayer implements RemotePlayerExt {

    @Unique
    private boolean wasSwinging = false;

    @Unique
    private float xBobO = 0.0f;
    @Unique
    private float xBob = 0.0f;
    @Unique
    private float yBobO = 0.0f;
    @Unique
    private float yBob = 0.0f;
    @Unique
    private Vec3 lastPosition = null;

    @Unique
    private ItemStack mainHandItem = ItemStack.EMPTY;
    @Unique
    private ItemStack offHandItem = ItemStack.EMPTY;
    @Unique
    private float mainHandHeight;
    @Unique
    private float oMainHandHeight;
    @Unique
    private float offHandHeight;
    @Unique
    private float oOffHandHeight;

    @Unique
    private Vec3 lastBoatPosition = null;
    @Unique
    private double lastBoatSpeed = 0.0f;

    private MixinRemotePlayer(ClientLevel clientLevel, GameProfile gameProfile) {
        super(clientLevel, gameProfile);
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    public void aiStep(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            if (!this.wasSwinging && this.isSwinging()) {
                this.resetAttackStrengthTicker();
            }
            this.wasSwinging = this.isSwinging();

            this.xBobO = xBob;
            this.xBob += Mth.wrapDegrees(this.getXRot() - this.xBob) * 0.5f;
            this.yBobO = yBob;
            this.yBob += Mth.wrapDegrees(this.getYRot() - this.yBob) * 0.5f;

            if (this.lastPosition != null && this.avatarState().getInterpolatedWalkDistance(0) == this.avatarState().getInterpolatedWalkDistance(1)) {
                double dx = this.lastPosition.x - this.position().x;
                double dz = this.lastPosition.z - this.position().z;
                this.addWalkedDistance((float) Math.sqrt(dx*dx + dz*dz) * 0.6f);
            }
            this.lastPosition = this.position();

            // Update held item
            this.oMainHandHeight = this.mainHandHeight;
            this.oOffHandHeight = this.offHandHeight;
            ItemStack nextMainHand = this.getMainHandItem();
            ItemStack nextOffHand = this.getOffhandItem();
            if (shouldInstantlyReplaceVisibleItem(this.mainHandItem, nextMainHand)) {
                this.mainHandItem = nextMainHand;
            }

            if (shouldInstantlyReplaceVisibleItem(this.offHandItem, nextOffHand)) {
                this.offHandItem = nextOffHand;
            }

            boolean handsBusy = false;
            if (this.getControlledVehicle() instanceof AbstractBoat boat) {
                Vec3 boatPosition = boat.position();

                if (this.lastBoatPosition != null) {
                    double boatSpeed = boatPosition.distanceToSqr(this.lastBoatPosition);

                    handsBusy = this.lastBoatSpeed < boatSpeed || (this.lastBoatSpeed == boatSpeed && boatSpeed > 0);

                    this.lastBoatSpeed = boatSpeed;
                }

                this.lastBoatPosition = boatPosition;
            }

            if (handsBusy) {
                this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4F, 0.0F, 1.0F);
                this.offHandHeight = Mth.clamp(this.offHandHeight - 0.4F, 0.0F, 1.0F);
            } else {
                float attackAnim = this.getItemSwapScale(1.0F);
                float mainHandTargetHeight = this.mainHandItem != nextMainHand ? 0.0F : attackAnim * attackAnim * attackAnim;
                float offHandTargetHeight = this.offHandItem != nextOffHand ? 0.0F : 1.0F;
                this.mainHandHeight += Mth.clamp(mainHandTargetHeight - this.mainHandHeight, -0.4F, 0.4F);
                this.offHandHeight += Mth.clamp(offHandTargetHeight - this.offHandHeight, -0.4F, 0.4F);
            }

            if (this.mainHandHeight < 0.1F) {
                this.mainHandItem = nextMainHand;
            }

            if (this.offHandHeight < 0.1F) {
                this.offHandItem = nextOffHand;
            }
        }
    }

    @Unique
    private static boolean shouldInstantlyReplaceVisibleItem(final ItemStack currentlyVisibleItem, final ItemStack expectedItem) {
        if (ItemStack.matchesIgnoringComponents(currentlyVisibleItem, expectedItem, DataComponentType::ignoreSwapAnimation)) {
            return true;
        } else {
            return !Minecraft.getInstance().getItemModelResolver().shouldPlaySwapAnimation(expectedItem);
        }
    }

    @Unique
    private static boolean isChargedCrossbow(final ItemStack item) {
        return item.is(Items.CROSSBOW) && CrossbowItem.isCharged(item);
    }

    @Unique
    private static FirstPersonHandsAndItemsRenderState.HandRenderSelection evaluateWhichHandsToRender(final AbstractClientPlayer player) {
        ItemStack mainHandItem = player.getMainHandItem();
        ItemStack offhandItem = player.getOffhandItem();
        boolean holdsBow = mainHandItem.is(Items.BOW) || offhandItem.is(Items.BOW);
        boolean holdsCrossbow = mainHandItem.is(Items.CROSSBOW) || offhandItem.is(Items.CROSSBOW);
        if (!holdsBow && !holdsCrossbow) {
            return FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_BOTH_HANDS;
        } else if (!player.isUsingItem()) {
            return isChargedCrossbow(mainHandItem) ? FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_MAIN_HAND_ONLY : FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_BOTH_HANDS;
        } else {
            ItemStack usedItemStack = player.getUseItem();
            InteractionHand usedHand = player.getUsedItemHand();
            if (!usedItemStack.is(Items.BOW) && !usedItemStack.is(Items.CROSSBOW)) {
                return usedHand == InteractionHand.MAIN_HAND && isChargedCrossbow(offhandItem) ? FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_MAIN_HAND_ONLY : FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_BOTH_HANDS;
            } else {
                return FirstPersonHandsAndItemsRenderState.HandRenderSelection.onlyForHand(usedHand);
            }
        }
    }

    public void flashback$extractFirstPersonHandsAndItems(float partialTicks, FirstPersonHandsAndItemsRenderState state) {
        Minecraft minecraft = Minecraft.getInstance();

        LivingEntity.SwingDescription currentSwing = this.getCurrentSwing();
        state.attackHand = currentSwing == null ? InteractionHand.MAIN_HAND : currentSwing.hand();
        state.viewXRot = this.getViewXRot(partialTicks);
        state.viewYRot = this.getViewYRot(partialTicks);
        state.xBob = Mth.lerp(partialTicks, this.xBobO, this.xBob);
        state.yBob = Mth.lerp(partialTicks, this.yBobO, this.yBob);
        state.isScoping = this.isScoping();
        state.useItemRemainingTicks = this.getUseItemRemainingTicks();
        state.handRenderSelection = evaluateWhichHandsToRender(this);
        state.mainHandItem = this.mainHandItem;
        state.offHandItem = this.offHandItem;
        state.mainHandHeight = this.mainHandHeight;
        state.oldMainHandHeight = this.oMainHandHeight;
        state.offHandHeight = this.offHandHeight;
        state.oldOffHandHeight = this.oOffHandHeight;
        boolean isMainHandRight = this.getMainArm() == HumanoidArm.RIGHT;
        ItemDisplayContext mainHandDisplayContext = isMainHandRight ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        ItemDisplayContext offHandDisplayContext = isMainHandRight ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        state.mainHandRenderState.clear();
        state.offHandRenderState.clear();
        minecraft.getItemModelResolver().updateForTopItem(state.mainHandRenderState, state.mainHandItem, mainHandDisplayContext, this.level(), this, this.getId() + mainHandDisplayContext.ordinal());
        minecraft.getItemModelResolver().updateForTopItem(state.offHandRenderState, state.offHandItem, offHandDisplayContext, this.level(), this, this.getId() + offHandDisplayContext.ordinal());
        state.mainHandUseDuration = state.mainHandItem.getUseDuration(this);
        state.offHandUseDuration = state.offHandItem.getUseDuration(this);
        state.mainHandChargeDuration = CrossbowItem.getChargeDuration(state.mainHandItem, this);
        state.offHandChargeDuration = CrossbowItem.getChargeDuration(state.offHandItem, this);
        state.mainHandSwapScale = minecraft.getItemModelResolver().swapAnimationScale(state.mainHandItem);
        state.offHandSwapScale = minecraft.getItemModelResolver().swapAnimationScale(state.offHandItem);
        state.hasMainHandMapData = extractMapRenderState(minecraft, this, state.mainHandItem, state.mainHandMapRenderState);
        state.hasOffHandMapData = extractMapRenderState(minecraft, this, state.offHandItem, state.offHandMapRenderState);
    }

    @Unique
    private static boolean extractMapRenderState(Minecraft minecraft, final AbstractClientPlayer player, final ItemStack itemStack, final MapRenderState state) {
        MapId mapId = itemStack.get(DataComponents.MAP_ID);
        MapItemSavedData mapData = mapId == null ? null : MapItem.getSavedData(mapId, player.level());
        if (mapId != null && mapData != null) {
            minecraft.getMapRenderer().extractRenderState(mapId, mapData, state);
            return true;
        } else {
            return false;
        }
    }

}
