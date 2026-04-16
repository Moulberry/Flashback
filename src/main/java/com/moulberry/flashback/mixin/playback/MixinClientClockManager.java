package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.ext.ClientClockManagerExt;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.ClientClockManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(ClientClockManager.class)
public class MixinClientClockManager implements ClientClockManagerExt {

    @Shadow
    @Final
    private Map<Holder<WorldClock>, ClientClockManager.ClockInstance> clocks;

    public Map<Holder<WorldClock>, ClockNetworkState> flashback$encodeClockUpdates() {
        Map<Holder<WorldClock>, ClockNetworkState> data = new HashMap<>();

        for (Map.Entry<Holder<WorldClock>, ClientClockManager.ClockInstance> entry : this.clocks.entrySet()) {
            var clock = entry.getValue();
            data.put(entry.getKey(), new ClockNetworkState(
                clock.totalTicks,
                clock.partialTick,
                clock.rate
            ));
        }

        return data;
    }

    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
    public void getTotalTicks(Holder<WorldClock> definition, CallbackInfoReturnable<Long> cir) {
        if (definition.is(WorldClocks.OVERWORLD)) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && editorState.replayVisuals.overrideTimeOfDay >= 0) {
                cir.setReturnValue(editorState.replayVisuals.overrideTimeOfDay);
            }
        }
    }

}
