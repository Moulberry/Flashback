package com.moulberry.flashback.utils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedDaemonThreadFactory implements ThreadFactory {

    private final ThreadGroup group = Thread.currentThread().getThreadGroup();
    private final AtomicInteger threadNumber = new AtomicInteger();
    private final String namePrefix;

    public NamedDaemonThreadFactory(String name) {
        this.namePrefix = name + "-thread-";
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread t = new Thread(this.group, runnable, this.namePrefix + this.threadNumber.getAndIncrement(), 0L);
        t.setDaemon(true);
        return t;
    }

}
