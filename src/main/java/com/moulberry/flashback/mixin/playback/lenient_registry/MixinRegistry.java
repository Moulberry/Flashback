package com.moulberry.flashback.mixin.playback.lenient_registry;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.DataResult;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayConfigurationPacketHandler;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Registry.class)
public interface MixinRegistry {

    @ModifyReturnValue(method = "method_57065", at = @At("RETURN"))
    default DataResult<?> referenceHolderWithLifecycle_get(DataResult<?> original) {
        if (original.isSuccess()) {
            return original;
        }
        if (Flashback.isInReplay() && ReplayConfigurationPacketHandler.LENIENT_REGISTRY_LOADING.get() == Boolean.TRUE) {
            if (this == BuiltInRegistries.PARTICLE_TYPE) {
                return DataResult.success(ParticleTypes.ASH);
            } else if (this instanceof DefaultedRegistry<?> defaultedRegistry) {
                return DataResult.success(defaultedRegistry.getValue(defaultedRegistry.getDefaultKey()));
            }
        }
        return original;
    }

}
