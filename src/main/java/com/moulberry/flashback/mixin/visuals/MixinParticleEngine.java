package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class MixinParticleEngine {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    public void createParticle(ParticleOptions particleOptions, double d, double e, double f, double g, double h, double i, CallbackInfoReturnable<Particle> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.filteredParticles.isEmpty()) {
            ParticleType<?> particleType = particleOptions.getType();
            ResourceLocation resourceLocation = BuiltInRegistries.PARTICLE_TYPE.getKey(particleType);
            if (resourceLocation != null && editorState.filteredParticles.contains(resourceLocation.toString())) {
                cir.setReturnValue(null);
            }
        }
    }

    @Inject(method = {"destroy", "crack"}, at = @At("HEAD"), cancellable = true)
    public void destroy(CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.filteredParticles.isEmpty()) {
            ResourceLocation resourceLocation = BuiltInRegistries.PARTICLE_TYPE.getKey(ParticleTypes.BLOCK);
            if (resourceLocation != null && editorState.filteredParticles.contains(resourceLocation.toString())) {
                ci.cancel();
            }
        }
    }

}
