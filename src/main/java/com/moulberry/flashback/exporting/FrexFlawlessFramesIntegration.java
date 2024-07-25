package com.moulberry.flashback.exporting;

import java.util.function.Consumer;
import java.util.function.Function;

public class FrexFlawlessFramesIntegration implements Consumer<Function<String, Consumer<Boolean>>> {

    @Override
    public void accept(Function<String, Consumer<Boolean>> flawlessFrames) {
        PerfectFrames.frexFlawlessFrames.add(flawlessFrames.apply("flashback"));
    }

}
