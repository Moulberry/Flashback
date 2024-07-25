package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Scoreboard.class)
public abstract class MixinScoreboard {

    @Shadow @Final private Object2ObjectMap<String, Objective> objectivesByName;

    @Shadow public abstract void removeObjective(Objective objective);

    @Inject(method = "addObjective", at = @At("HEAD"))
    public void addObjective(String string, ObjectiveCriteria objectiveCriteria, Component component, ObjectiveCriteria.RenderType renderType, boolean bl, NumberFormat numberFormat, CallbackInfoReturnable<Objective> cir) {
        if (Flashback.isInReplay() && this.objectivesByName.containsKey(string)) {
            this.removeObjective(this.objectivesByName.get(string));
        }
    }

}
