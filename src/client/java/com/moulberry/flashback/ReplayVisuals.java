package com.moulberry.flashback;

public class ReplayVisuals {

    public static boolean showBossBar = true;
    public static boolean showTitleText = true;
    public static boolean showScoreboard = true;
    public static boolean showActionBar = true;
    public static boolean showNametags = true;
    public static float overrideFogDistance = 0.0f;
    public static float overrideFov = 0.0f;
    public static long overrideTimeOfDay = -1;

    public static volatile int forceEntityLerpTicks = 0;

    public static void disable() {
        showBossBar = true;
        showTitleText = true;
        showScoreboard = true;
        showActionBar = true;
        showNametags = true;
        overrideFogDistance = 0.0f;
        overrideFov = 0.0f;
        forceEntityLerpTicks = 0;
        overrideTimeOfDay = -1;
    }

    public static void setupReplay() {
        disable();

        showBossBar = false;
        showTitleText = false;
        showActionBar = false;
        showScoreboard = false;
    }

}
