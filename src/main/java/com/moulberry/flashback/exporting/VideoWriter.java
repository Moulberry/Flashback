package com.moulberry.flashback.exporting;

import java.util.function.Consumer;

public interface VideoWriter extends AutoCloseable {

    void encode(ImageFrame src);
    void finish(Consumer<String> wait);

    default void close() {
    }

}
