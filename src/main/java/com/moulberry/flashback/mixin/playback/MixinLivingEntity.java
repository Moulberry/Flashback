package com.moulberry.flashback.mixin.playback;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    @Shadow
    public abstract void lerpTo(double d, double e, double f, float g, float h, int i);

    @Shadow
    public abstract void lerpHeadTo(float f, int i);

    public MixinLivingEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    // todo: maybe reintroduce this lerpTo code?
//    @Inject(method = "lerpTo", at = @At("HEAD"), cancellable = true)
//    public void lerpTo(double d, double e, double f, float g, float h, int i, CallbackInfo ci) {
//        if (FlashbackClient.isInReplay() && i > 1 && FlashbackClient.getReplayServer().getLocalPlayerId() == this.getId() && Minecraft.getInstance().getCameraEntity() == this) {
//            this.lerpTo(d, e, f, g, h, 1);
//            ci.cancel();
//        }
//    }
//
//    @Inject(method = "lerpHeadTo", at = @At("HEAD"), cancellable = true)
//    public void lerpHeadTo(float f, int i, CallbackInfo ci) {
//        if (FlashbackClient.isInReplay() && i > 1 && FlashbackClient.getReplayServer().getLocalPlayerId() == this.getId() && Minecraft.getInstance().getCameraEntity() == this) {
//            this.lerpHeadTo(f, 1);
//            ci.cancel();
//        }
//    }

}
