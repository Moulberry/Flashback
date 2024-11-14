package com.moulberry.flashback.mixin.record;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    @WrapOperation(method = "continueDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"))
    public void playBreakingSound(SoundManager instance, SoundInstance soundInstance, Operation<Void> original) {
        original.call(instance, soundInstance);

        if (Flashback.RECORDER != null && !Flashback.RECORDER.isPaused()) {
            if (soundInstance.getSound() == null) {
                return;
            }

            Optional<Holder.Reference<SoundEvent>> builtinSoundEvent = BuiltInRegistries.SOUND_EVENT.get(soundInstance.getLocation());
            Holder<SoundEvent> holder;
            if (builtinSoundEvent.isEmpty()) {
                holder = Holder.direct(SoundEvent.createVariableRangeEvent(soundInstance.getLocation()));
            } else {
                holder = builtinSoundEvent.get();
            }

            SoundSource soundSource = soundInstance.getSource();
            float volume = soundInstance.getVolume();
            float pitch = soundInstance.getPitch();
            double x = soundInstance.getX();
            double y = soundInstance.getY();
            double z = soundInstance.getZ();

            Flashback.RECORDER.writeSound(holder, soundSource, x, y, z, volume, pitch,
                ThreadLocalRandom.current().nextLong());
        }
    }

}
