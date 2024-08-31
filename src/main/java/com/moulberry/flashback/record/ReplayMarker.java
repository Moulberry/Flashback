package com.moulberry.flashback.record;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public record ReplayMarker(int colour, @Nullable MarkerPosition position, @Nullable String description) {

    public record MarkerPosition(Vector3f position, String dimension) {}

}
