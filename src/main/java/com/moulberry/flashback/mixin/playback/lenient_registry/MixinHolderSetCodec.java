package com.moulberry.flashback.mixin.playback.lenient_registry;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.codec.HolderSetCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HolderSetCodec.class)
public class MixinHolderSetCodec {

    @WrapOperation(method = "encode(Lnet/minecraft/core/HolderSet;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/HolderSet$Named;canSerializeIn(Lnet/minecraft/core/HolderOwner;)Z"))
    public boolean encode_canSerializeIn(HolderSet.Named instance, HolderOwner context, Operation<Boolean> original) {
        if (Flashback.isInReplay()) {
            return true;
        }
        return original.call(instance, context);
    }

}
