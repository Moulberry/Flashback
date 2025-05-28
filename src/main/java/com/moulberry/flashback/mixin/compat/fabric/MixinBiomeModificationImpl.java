package com.moulberry.flashback.mixin.compat.fabric;

import com.moulberry.flashback.Flashback;
import net.fabricmc.fabric.impl.biome.modification.BiomeModificationImpl;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BiomeModificationImpl.class)
public class MixinBiomeModificationImpl {

    @Inject(method = "finalizeWorldGen", at = @At("HEAD"), cancellable = true, require = 0)
    public void finalizeWorldGen(RegistryAccess impl, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

}
