package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

public interface VideoWriter extends AutoCloseable {

    void encode(NativeImage src, @Nullable FloatBuffer audioBuffer);
    void finish(Consumer<String> wait);

    default void close() {
    }

}
