package com.moulberry.flashback.mixin.compat.create;

// Headers
import com.moulberry.flashback.compat.create.CreateSupport;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mounted Storage Manager Mixin
@IfModLoaded("create")
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.MountedStorageManager", priority = 900, remap = false)
public abstract class MixinMountedStorageManager {

    // Tick Injection
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void flashback$tick(AbstractContraptionEntity entity, CallbackInfo ci) {
        CreateSupport.onTickStorage(entity, ci);
    }

}
