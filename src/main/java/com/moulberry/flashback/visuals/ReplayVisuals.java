package com.moulberry.flashback.visuals;

import com.moulberry.flashback.combo_options.AspectRatio;
import com.moulberry.flashback.combo_options.Sizing;
import com.moulberry.flashback.combo_options.WeatherOverride;
import net.minecraft.client.Minecraft;

public class ReplayVisuals {

    public boolean showChat = false;
    public boolean showBossBar = false;
    public boolean showTitleText = false;
    public boolean showScoreboard = false;
    public boolean showActionBar = false;
    public boolean showHotbar = true;

    public boolean renderBlocks = true;
    public boolean renderEntities = true;
    public boolean renderPlayers = true;
    public boolean renderParticles = true;
    public boolean renderSky = true;
    public float[] skyColour = new float[]{0f, 1f, 0f};
    public boolean renderNametags = true;

    public boolean overrideFog = false;
    public float overrideFogStart = 0.0f;
    public float overrideFogEnd = 256.0f;

    public boolean overrideFogColour = false;
    public float[] fogColour = new float[]{0f, 1f, 0f};

    public boolean overrideFov = false;
    public float overrideFovAmount = -1f;

    public boolean overrideCameraShake = false;
    public boolean cameraShakeSplitParams = false;
    public float cameraShakeYFrequency = 1.0f;
    public float cameraShakeYAmplitude = 1.0f;
    public float cameraShakeXFrequency = 1.0f;
    public float cameraShakeXAmplitude = 1.0f;

    public boolean overrideRoll = false;
    public float overrideRollAmount = 0.0f;

    public WeatherOverride overrideWeatherMode = WeatherOverride.NONE;

    public long overrideTimeOfDay = -1;

    public boolean overrideNightVision = false;

    public boolean ruleOfThirdsGuide = false;
    public boolean centerGuide = false;
    public boolean cameraPath = true;
    public Sizing sizing = Sizing.KEEP_ASPECT_RATIO;
    public AspectRatio changeAspectRatio = AspectRatio.ASPECT_16_9;

    public void setFov(float fov) {
        if (!overrideFov || Math.abs(overrideFovAmount - fov) >= 0.01) {
            Minecraft.getInstance().levelRenderer.needsUpdate();
        }

        overrideFov = true;
        overrideFovAmount = fov;
    }

    public void setCameraShake(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY) {
        overrideCameraShake = true;
        cameraShakeSplitParams = frequencyX != frequencyY || amplitudeX != amplitudeY;
        cameraShakeXFrequency = frequencyX;
        cameraShakeXAmplitude = amplitudeX;
        cameraShakeYFrequency = frequencyY;
        cameraShakeYAmplitude = amplitudeY;
    }

}
