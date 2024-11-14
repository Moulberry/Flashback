package com.moulberry.flashback.mixin.record;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {

    @WrapOperation(method = "playSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    public void playSound(Level instance, double x, double y, double z, SoundEvent soundEvent, SoundSource soundSource,
            float volume, float pitch, boolean global, Operation<Void> original) {
        if (Flashback.RECORDER != null && !Flashback.RECORDER.isPaused()) {

            Optional<Holder.Reference<SoundEvent>> builtinSoundEvent = BuiltInRegistries.SOUND_EVENT.get(soundEvent.location());
            Holder<SoundEvent> holder;
            if (builtinSoundEvent.isEmpty()) {
                holder = Holder.direct(soundEvent);
            } else {
                holder = builtinSoundEvent.get();
            }

            Flashback.RECORDER.writeSound(holder, soundSource, x, y, z, volume, pitch,
                ThreadLocalRandom.current().nextLong());
        }
        original.call(instance, x, y, z, soundEvent, soundSource, volume, pitch, global);
    }

}
