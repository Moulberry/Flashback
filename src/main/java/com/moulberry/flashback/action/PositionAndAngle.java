package com.moulberry.flashback.action;

import com.moulberry.flashback.record.Recorder;
import net.minecraft.util.Mth;

public record PositionAndAngle(double x, double y, double z, float yaw, float pitch) {
    public PositionAndAngle lerp(PositionAndAngle other, double amount) {
        return new PositionAndAngle(
            this.x + (other.x - this.x) * amount,
            this.y + (other.y - this.y) * amount,
            this.z + (other.z - this.z) * amount,
            (float) Mth.wrapDegrees(this.yaw + Mth.wrapDegrees(other.yaw - this.yaw) * amount),
            (float) Mth.wrapDegrees(this.pitch + Mth.wrapDegrees(other.pitch - this.pitch) * amount)
        );
    }
}
