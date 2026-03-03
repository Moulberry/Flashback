package com.moulberry.flashback.mixin.compat.bobby;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import de.johni0702.minecraft.bobby.BobbyConfig;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfModLoaded("bobby")
@Pseudo
@Mixin(value = BobbyConfig.class)
public class MixinBobbyConfig {
    @Inject(method = "getViewDistanceOverwrite", require = 0, remap = false, at = @At("RETURN"), cancellable = true)
    public void flashback$overrideViewDistanceOverwrite(CallbackInfoReturnable<Integer> cir) {
        if (Flashback.isInReplay()) {
            cir.setReturnValue(0);//we dont want server distance override
        }
    }
}
