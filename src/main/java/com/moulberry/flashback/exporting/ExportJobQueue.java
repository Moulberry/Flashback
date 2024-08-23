package com.moulberry.flashback.exporting;

import java.util.ArrayList;
import java.util.List;

public class ExportJobQueue {

    public static List<ExportSettings> queuedJobs = new ArrayList<>();
    public static boolean drainingQueue = false;

    public static int count() {
        return queuedJobs.size();
    }


}
