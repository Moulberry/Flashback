package com.moulberry.flashback.combo_options;

public enum AspectRatio implements ComboOption {

    ASPECT_16_9("16:9 (HDTV)", 16/9f),
    ASPECT_9_16("9:16 (Shorts)", 9/16f),
    ASPECT_240_1("2.40:1 (Widescreen)", 2.4f),
    ASPECT_1_1("1:1 (Square)", 1f),
    ASPECT_4_3("4:3 (Fullscreen)", 4/3f),
    ASPECT_3_2("3:2 (Photo)", 3/2f);

    private final String text;
    private final float aspectRatio;

    AspectRatio(String text, float aspectRatio) {
        this.text = text;
        this.aspectRatio = aspectRatio;
    }

    @Override
    public String text() {
        return this.text;
    }

    public float aspectRatio() {
        return aspectRatio;
    }
}
