package com.moulberry.flashback;

import com.moulberry.flashback.combo_options.AspectRatio;
import com.moulberry.flashback.combo_options.Sizing;
import net.minecraft.client.Minecraft;

public class ReplayVisuals {

    // todo: move all of this to replaystate?

    public static boolean showChat = true;
    public static boolean showBossBar = true;
    public static boolean showTitleText = true;
    public static boolean showScoreboard = true;
    public static boolean showActionBar = true;

    public static boolean renderBlocks = true;
    public static boolean renderEntities = true;
    public static boolean renderPlayers = true;
    public static boolean renderParticles = true;
    public static boolean renderSky = true;
    public static boolean renderNametags = true;

    public static boolean overrideFog = false;
    public static float overrideFogStart = 0.0f;
    public static float overrideFogEnd = 256.0f;

    public static boolean overrideFov = false;
    public static float overrideFovAmount = 70f;

    public static long overrideTimeOfDay = -1;

    public static boolean ruleOfThirdsGuide = false;
    public static boolean centerGuide = false;
    public static Sizing sizing = Sizing.KEEP_ASPECT_RATIO;
    public static AspectRatio changeAspectRatio = AspectRatio.ASPECT_16_9;

    public static volatile int forceEntityLerpTicks = 0;

    public static void setFov(float fov) {
        if (!overrideFov || Math.abs(overrideFovAmount - fov) >= 0.01) {
            Minecraft.getInstance().levelRenderer.needsUpdate();
        }

        overrideFov = true;
        overrideFovAmount = fov;
    }

    public static void disable() {
        showBossBar = true;
        showTitleText = true;
        showScoreboard = true;
        showActionBar = true;

        renderBlocks = true;
        renderEntities = true;
        renderParticles = true;
        renderSky = true;
        renderNametags = true;

        overrideFog = false;
        ruleOfThirdsGuide = false;
        centerGuide = false;
        overrideFov = false;
        forceEntityLerpTicks = 0;
        overrideTimeOfDay = -1;
    }

    public static void setupReplay() {
        disable();

        showChat = false;
        showBossBar = false;
        showTitleText = false;
        showActionBar = false;
        showScoreboard = false;
    }

}
