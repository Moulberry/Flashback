package com.moulberry.flashback.mixin.compat.replaymod;

import com.moulberry.flashback.Flashback;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import com.replaymod.recording.ReplayModRecording;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@IfModLoaded("replaymod")
@Pseudo
@Mixin(value = ReplayModRecording.class, remap = false)
public class MixinReplayModRecording {

    @Inject(method = "initiateRecording", at = @At("HEAD"), require = 0, cancellable = true)
    public void initiateRecording(Connection networkManager, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

}
