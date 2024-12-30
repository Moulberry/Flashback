package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;

public interface KeyframeChange {

    void apply(KeyframeHandler keyframeHandler);
    KeyframeChange interpolate(KeyframeChange to, double amount);

}
