package com.moulberry.flashback.exporting.taskbar;

public interface ITaskbar {
    void close();

    void setProgress(long count, long outOf);
}
