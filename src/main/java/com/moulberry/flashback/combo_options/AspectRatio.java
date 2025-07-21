package com.moulberry.flashback.combo_options;

import net.minecraft.client.resources.language.I18n;

public enum AspectRatio implements ComboOption {

    ASPECT_16_9("flashback.aspect_ratio.16_by_9", 16/9f),
    ASPECT_9_16("flashback.aspect_ratio.9_by_16", 9/16f),
    ASPECT_240_1("flashback.aspect_ratio.widescreen", 2.4f),
    ASPECT_1_1("flashback.aspect_ratio.square", 1f),
    ASPECT_4_3("flashback.aspect_ratio.fullscreen", 4/3f),
    ASPECT_3_2("flashback.aspect_ratio.photo", 3/2f);

    private final String text;
    private final float aspectRatio;

    AspectRatio(String text, float aspectRatio) {
        this.text = text;
        this.aspectRatio = aspectRatio;
    }

    @Override
    public String text() {
        return I18n.get(this.text);
    }

    public float aspectRatio() {
        return aspectRatio;
    }
}
