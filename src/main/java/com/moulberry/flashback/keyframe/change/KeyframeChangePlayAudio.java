package com.moulberry.flashback.keyframe.change;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.sound.FlashbackAudioBuffer;
import com.moulberry.flashback.sound.FlashbackAudioManager;
import net.minecraft.client.Minecraft;

public class KeyframeChangePlayAudio implements KeyframeChange {

    private final FlashbackAudioBuffer audioBuffer;
    private final int startTick;
    private final float seconds;

    public KeyframeChangePlayAudio(FlashbackAudioBuffer audioBuffer, int startTick, float seconds) {
        this.audioBuffer = audioBuffer;
        this.startTick = startTick;
        this.seconds = seconds;
    }

    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        Minecraft minecraft = keyframeHandler.getMinecraft();
        if (minecraft != null && minecraft.level != null) {
            var tickRateManager = minecraft.level.tickRateManager();
            float tickrate = tickRateManager.tickrate();
            FlashbackAudioManager.playAt(minecraft.getSoundManager().soundEngine, this.audioBuffer, this.startTick,
                    this.seconds, tickrate / 20f);
        }
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange to, double amount) {
        return this;
    }
}
