package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AttributeInstance.class)
public abstract class MixinAttributeInstance {

    @Shadow
    public abstract boolean removeModifier(ResourceLocation resourceLocation);

    @Inject(method = "addModifier", at = @At(value = "HEAD"))
    public void addModifier(AttributeModifier attributeModifier, CallbackInfo ci) {
        // Some mods (e.g. HorseBuff) may end up adding a duplicate modifier while inside a replay
        // Remove the modifier first in order to ensure no exceptions are thrown
        if (Flashback.isInReplay()) {
            try {
                this.removeModifier(attributeModifier.id());
            } catch (Exception ignored) {}
        }
    }

}
