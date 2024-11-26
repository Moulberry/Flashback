package com.moulberry.flashback.exporting.taskbar;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.windows.ExportDoneWindow;
import com.moulberry.flashback.exporting.ExportJob;
import net.minecraft.Util;

import java.util.concurrent.ForkJoinPool;

public class TaskbarManager {

    private static ITaskbar taskbarInterface = null;

    private static int lastProgressCount = 0;
    private static int lastProgressOutOf = 0;
    private static long lastExportJobUpdate = -1;
    private static boolean lastPaused = false;

    private static volatile boolean started = false;

    public static void launchTaskbarManager() {
        if (started) {
            return;
        }
        started = true;

        Thread taskbarThread = new Thread(() -> {
            while (Flashback.isInReplay()) {
               try {
                   Thread.sleep(100L);
                   tickTaskbarProgress();
               } catch (InterruptedException ignored) {}
            }
            started = false;
        });
        taskbarThread.setName("Flashback-Taskbar-Updater");
        taskbarThread.start();
    }

    public static void tickTaskbarProgress() {
        ExportJob exportJob = Flashback.EXPORT_JOB;
        if (exportJob != null) {
            long currentTime = System.currentTimeMillis();
            if (lastExportJobUpdate > currentTime) {
                lastExportJobUpdate = currentTime;
            }

            boolean paused = false;
            if (lastExportJobUpdate > 0 && exportJob.progressCount == lastProgressCount && exportJob.progressOutOf == lastProgressOutOf) {
                paused = currentTime - lastExportJobUpdate > 5000;
            } else {
                lastExportJobUpdate = currentTime;
            }

            setTaskbarProgress(exportJob.progressCount, exportJob.progressOutOf, paused);
            return;
        } else {
            lastExportJobUpdate = -1;
        }

        if (ExportDoneWindow.exportDoneWindowOpen) {
            setTaskbarProgress(1, 1, false);
            return;
        }

        setTaskbarProgress(0, 0, false);
    }

    private static void setTaskbarProgress(int count, int outOf, boolean paused) {
        if (count == lastProgressCount && outOf == lastProgressOutOf && paused == lastPaused) {
            return;
        }

        if (taskbarInterface == null) {
            taskbarInterface = TaskbarHost.createTaskbar();
        }

        if (count <= 0 || outOf <= 0) {
            taskbarInterface.reset();
            lastPaused = false;
        } else {
            if (paused != lastPaused) {
                if (paused) {
                    taskbarInterface.setPaused();
                } else {
                    taskbarInterface.setNormal();
                }
                lastPaused = paused;
            }
            taskbarInterface.setProgress(count, outOf);
        }

        lastProgressCount = count;
        lastProgressOutOf = outOf;
    }

}
