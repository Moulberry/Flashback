package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;

public interface KeyframeChange {

    void apply(KeyframeHandler keyframeHandler);
    KeyframeChange interpolate(KeyframeChange to, double amount);

    static KeyframeChange interpolateSafe(KeyframeChange from, KeyframeChange to, double amount) {
        if (from == to) {
            return from;
        } else if (from == null) {
            return to;
        } else if (to == null) {
            return from;
        } else if (from.getClass().isAssignableFrom(to.getClass())) {
            return from.interpolate(to, amount);
        } else if (to.getClass().isAssignableFrom(from.getClass())) {
            return to.interpolate(from, 1 - amount);
        } else if (amount < 0.5) {
            return from;
        } else {
            return to;
        }
    }

}
