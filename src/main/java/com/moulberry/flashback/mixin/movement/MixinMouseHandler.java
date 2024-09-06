package com.moulberry.flashback.mixin.movement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    public void turnPlayer_turn(LocalPlayer instance, double x, double y, Operation<Void> original) {
        if (Flashback.isInReplay()) {
            FlashbackConfig config = Flashback.getConfig();
            if (config.flightLockYaw) {
                x = 0.0f;
            }
            if (config.flightLockPitch) {
                y = 0.0f;
            }
        }

        original.call(instance, x, y);
    }

}
