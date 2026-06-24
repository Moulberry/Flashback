package com.moulberry.flashback.mixin.playback.lenient_registry;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.DataResult;
import com.moulberry.flashback.Flashback;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Registry.class)
public interface MixinRegistry {

    @ModifyReturnValue(method = "lambda$referenceHolderWithLifecycle$0", at = @At("RETURN"))
    default DataResult<Holder.Reference<?>> referenceHolderWithLifecycle_get(DataResult<Holder.Reference<?>> original) {
        if (original.isSuccess()) {
            return original;
        }
        if (Flashback.isInReplay()) {
            if (this == BuiltInRegistries.PARTICLE_TYPE) {
                return DataResult.success(BuiltInRegistries.PARTICLE_TYPE.get(0).get());
            } else if (this instanceof DefaultedRegistry<?> defaultedRegistry) {
                return DataResult.success(defaultedRegistry.get(defaultedRegistry.getDefaultKey()).get());
            }
        }
        return original;
    }

}
