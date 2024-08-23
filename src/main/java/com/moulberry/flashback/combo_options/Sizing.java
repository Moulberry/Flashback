package com.moulberry.flashback.combo_options;

public enum Sizing implements ComboOption {

    KEEP_ASPECT_RATIO("Keep Aspect Ratio"),
    CHANGE_ASPECT_RATIO("Change Aspect Ratio"),
    FILL("Fill"),
    UNDERLAY("Underlay");

    private final String text;

    Sizing(String text) {
        this.text = text;
    }

    @Override
    public String text() {
        return this.text;
    }

}
