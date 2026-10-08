package com.moulberry.flashback.mixin.compat.create;

// Headers
import com.moulberry.flashback.compat.create.CreateSupport;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Contraption Mixin
@IfModLoaded("create")
@Pseudo
@Mixin(value = Contraption.class, priority = 900, remap = false)
public abstract class MixinContraption {

    // Tick Storage Injection
    @Inject(method = "tickStorage", at = @At("HEAD"), cancellable = true, require = 0)
    private void flashback$tickStorage(AbstractContraptionEntity entity, CallbackInfo ci) {
        CreateSupport.onTickStorage(entity, ci);
    }

}

