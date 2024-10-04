package com.moulberry.flashback.mixin.movement;

import com.moulberry.flashback.EnhancedFlight;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Player.class, priority = 1100)
public abstract class MixinPlayerEntity extends LivingEntity {

    protected MixinPlayerEntity() {
        super(null, null);
    }

    @Shadow public abstract void travel(Vec3 movementInput);

    @Shadow
    protected abstract float getFlyingSpeed();

    @Inject(method="getFlyingSpeed", at=@At("HEAD"), cancellable = true)
    public void getFlyingSpeed(CallbackInfoReturnable<Float> cir) {
        if ((Object)this instanceof LocalPlayer player && Flashback.isInReplay()) {
            FlashbackConfig config = Flashback.getConfig();

            boolean doAirplaneFlight = config.flightCameraDirection || config.flightMomentum < 0.98;
            if (doAirplaneFlight && player.getAbilities().flying && !player.isPassenger() && !player.isFallFlying()) {
                cir.setReturnValue(EnhancedFlight.getFlightSpeed(player, config));
            }
        }
    }

    @Inject(method="travel", at=@At(value = "HEAD"), cancellable = true)
    public void travel(Vec3 movementInput, CallbackInfo ci) {
        if ((Object)this instanceof LocalPlayer player && Flashback.isInReplay()) {
            FlashbackConfig config = Flashback.getConfig();

            boolean doAirplaneFlight = config.flightCameraDirection || config.flightMomentum < 0.98 ||
                config.flightLockX || config.flightLockY || config.flightLockZ;
            if (doAirplaneFlight && player.getAbilities().flying && !player.isPassenger() && !player.isFallFlying()) {
                EnhancedFlight.doFlight(player, config, movementInput, this.getFlyingSpeed(), super::travel);
                ci.cancel();
            }
        }
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    public void getName(CallbackInfoReturnable<Component> cir) {
        if ((Object)this instanceof RemotePlayer) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null) {
                String nameOverride = editorState.nameOverride.get(this.uuid);
                if (nameOverride != null) {
                    cir.setReturnValue(Component.literal(nameOverride));
                }
            }
        }
    }

}
