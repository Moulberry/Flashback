package com.moulberry.flashback.ext;

import net.minecraft.core.Holder;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;

import java.util.Map;

public interface ClientClockManagerExt {

    Map<Holder<WorldClock>, ClockNetworkState> flashback$encodeClockUpdates();

}
