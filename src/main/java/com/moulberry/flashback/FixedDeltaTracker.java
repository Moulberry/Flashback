package com.moulberry.flashback;

import net.minecraft.client.DeltaTracker;

public class FixedDeltaTracker implements DeltaTracker {
    private final float deltaTick;
    private final float partialTick;

    public FixedDeltaTracker(float deltaTick, float partialTick) {
        this.deltaTick = deltaTick;
        this.partialTick = partialTick;
    }

    public float getGameTimeDeltaTicks() {
        return this.deltaTick;
    }

    public float getGameTimeDeltaPartialTick(boolean bl) {
        return this.partialTick;
    }

    public float getRealtimeDeltaTicks() {
        return this.deltaTick;
    }
}