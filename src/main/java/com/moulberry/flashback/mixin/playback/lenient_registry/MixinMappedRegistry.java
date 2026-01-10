package com.moulberry.flashback.mixin.playback.lenient_registry;

import com.moulberry.flashback.playback.ReplayConfigurationPacketHandler;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(MappedRegistry.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MixinMappedRegistry {

    @Shadow
    private boolean frozen;

    @Shadow
    @Final
    private Map<TagKey, HolderSet.Named> frozenTags;

    @Inject(method = "freeze", at = @At("HEAD"))
    public void freeze(CallbackInfoReturnable<Registry> cir) {
        if (!this.frozen && ReplayConfigurationPacketHandler.LENIENT_REGISTRY_LOADING.get() == Boolean.TRUE) {
            for (Map.Entry<TagKey, HolderSet.Named> entry : this.frozenTags.entrySet()) {
                if (!entry.getValue().isBound()) {
                    entry.getValue().bind(List.of());
                }
            }
        }
    }

}
