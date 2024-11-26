package com.moulberry.flashback.exporting.taskbar;

public interface ITaskbar {
    void close();
    void reset();

    void setProgress(long count, long outOf);
    void setPaused();
    void setNormal();
}
