package com.moulberry.flashback.state;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class RealTimeMapping {

    private final NavigableMap<Integer, SpeedChange> map = new TreeMap<>();

    private record SpeedChange(float speedFactor, float realTimeUntilThisPoint) {}

    public void addMapping(int tick, float speed) {
        if (this.map.ceilingKey(tick) != null) {
            throw new IllegalStateException("Must call addMapping with tick that is greater than the last");
        }
        float realTimeUntilThisPoint = getRealTime(tick);
        this.map.put(tick, new SpeedChange(speed, realTimeUntilThisPoint));
    }

    public float getRealTime(float tick) {
        if (tick <= 0 || this.map.isEmpty()) {
            return tick;
        }

        var entry = map.floorEntry((int) tick);
        if (entry == null) {
            return tick;
        }

        SpeedChange change = entry.getValue();

        return change.realTimeUntilThisPoint + (tick - entry.getKey()) / change.speedFactor;
    }

    public float getTickForRealTime(float realTime) {
        if (this.map.isEmpty()) {
            return realTime;
        }

        Map.Entry<Integer, SpeedChange> lastEntry = null;
        for (Map.Entry<Integer, SpeedChange> entry : map.entrySet()) {
            if (entry.getValue().realTimeUntilThisPoint > realTime) {
                break;
            }
            lastEntry = entry;
        }

        if (lastEntry == null) {
            return realTime;
        }

        SpeedChange change = lastEntry.getValue();
        return lastEntry.getKey() + (realTime - change.realTimeUntilThisPoint) * change.speedFactor;
    }

}
