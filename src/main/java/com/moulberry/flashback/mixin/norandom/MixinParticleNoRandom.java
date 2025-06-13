package com.moulberry.flashback.mixin.norandom;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(Particle.class)
public class MixinParticleNoRandom {

    @Shadow
    @Final
    protected RandomSource random;

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;setSize(FF)V"))
    private void newParticle(ClientLevel clientLevel, double d, double e, double f, CallbackInfo ci) {
        if (Flashback.isExporting()) {
            Random random = Flashback.EXPORT_JOB.getParticleRandom();
            if (random != null) {
                this.random.setSeed(random.nextLong());
            }
        }
    }

}
