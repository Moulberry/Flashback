package com.moulberry.flashback.exporting.taskbar;

public class NoopTaskbar implements ITaskbar {
    @Override
    public void close() {
    }

    @Override
    public void reset() {
    }

    @Override
    public void setProgress(long count, long outOf) {
    }

    @Override
    public void setPaused() {
    }

    @Override
    public void setNormal() {
    }
}
