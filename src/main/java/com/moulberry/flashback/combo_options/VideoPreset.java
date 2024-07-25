package com.moulberry.flashback.combo_options;

public enum VideoPreset implements ComboOption {

    ULTRAFAST("Ultra Fast", "ultrafast"),
    SUPERFAST("Super Fast", "superfast"),
    VERYFAST("Very Fast", "veryfast"),
    FASTER("Faster", "faster"),
    FAST("Fast", "fast"),
    MEDIUM("Medium", "medium"),
    SLOW("Slow", "slow"),
    SLOWER("Slower", "slower"),
    VERYSLOW("Very Slow", "veryslow");

    private final String text;
    private final String presetId;

    VideoPreset(String text, String presetId) {
        this.text = text;
        this.presetId = presetId;
    }

    @Override
    public String text() {
        return this.text;
    }

    public String presetId() {
        return this.presetId;
    }
}
