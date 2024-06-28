package com.moulberry.flashback.mixin.client.playback;

import com.moulberry.flashback.FlashbackClient;
import net.minecraft.client.Minecraft;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TickRateManager.class)
public abstract class MixinTickRateManager {

    @Shadow
    public abstract boolean runsNormally();

    @Unique
    private final boolean isClientTickRateManager = !((Object)this instanceof ServerTickRateManager);

    @Inject(method = "isEntityFrozen", at = @At("HEAD"), cancellable = true)
    public void isEntityFrozen(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (this.isClientTickRateManager && FlashbackClient.isInReplay()) {
            cir.setReturnValue(!this.runsNormally() || entity == Minecraft.getInstance().player);
        }
    }

}
