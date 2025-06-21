package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExperienceBarRenderer.class)
public class MixinExperienceBarRenderer {

    @Shadow
    @Final
    private Minecraft minecraft;

    @WrapOperation(method = "renderBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXpNeededForNextLevel()I"), require = 0)
    public int renderExperienceBar_getXpNeededForNextLevel(LocalPlayer instance, Operation<Integer> original) {
        if (Flashback.isInReplay()) {
            Entity entity = this.minecraft.getCameraEntity();
            if (entity instanceof Player player) {
                return player.getXpNeededForNextLevel();
            }
        }
        return original.call(instance);
    }

    @WrapOperation(method = "renderBackground", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;experienceProgress:F"), require = 0)
    public float renderExperienceBar_experienceProgress(LocalPlayer instance, Operation<Float> original) {
        if (Flashback.isInReplay()) {
            Entity entity = this.minecraft.getCameraEntity();
            if (entity instanceof Player player) {
                return player.experienceProgress;
            }
        }
        return original.call(instance);
    }

}
