package com.moulberry.flashback.mixin.compat.create;

// Headers
import com.moulberry.flashback.compat.create.CreateSupport;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageSyncData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Carriage Entity Mixin
@IfModLoaded("create")
@Pseudo
@Mixin(value = CarriageContraptionEntity.class, priority = 900, remap = false)
public abstract class MixinCarriageContraptionEntity {

    // Tick Head Injection
    @Inject(method = "tickContraption", at = @At("HEAD"), cancellable = true, require = 0)
    private void flashback$tickContraptionHead(CallbackInfo ci) {
        CreateSupport.onTickContraptionHead((CarriageContraptionEntity)(Object)this, ci);
    }

    // Tick Tail Injection
    @Inject(method = "tickContraption", at = @At("TAIL"), require = 0)
    private void flashback$tickContraptionTail(CallbackInfo ci) {
        CreateSupport.onTickContraptionTail((CarriageContraptionEntity)(Object)this);
    }

    // Bind Injection
    @Inject(method = "bindCarriage", at = @At("TAIL"), require = 0)
    private void flashback$bindCarriage(CallbackInfo ci) {
        CreateSupport.onBindCarriageTail((CarriageContraptionEntity)(Object)this);
    }

    // Carriage Data Update Injection
    @Inject(method = "onCarriageDataUpdate", at = @At("TAIL"), require = 0)
    private void flashback$onCarriageDataUpdate(CarriageSyncData newData, CallbackInfo ci) {
        CreateSupport.onCarriageDataUpdateTail((CarriageContraptionEntity)(Object)this, newData);
    }

}
