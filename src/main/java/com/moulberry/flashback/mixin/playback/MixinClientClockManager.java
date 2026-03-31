package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.ext.ClientClockManagerExt;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

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

}
