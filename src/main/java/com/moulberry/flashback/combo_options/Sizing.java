package com.moulberry.flashback.combo_options;

import net.minecraft.client.resources.language.I18n;

public enum Sizing implements ComboOption {

    KEEP_ASPECT_RATIO("flashback.sizing.keep_aspect_ratio"),
    CHANGE_ASPECT_RATIO("flashback.sizing.change_aspect_ratio"),
    FILL("flashback.sizing.fill"),
    UNDERLAY("flashback.sizing.underlay");

    private final String text;

    Sizing(String text) {
        this.text = text;
    }

    @Override
    public String text() {
        return I18n.get(this.text);
    }

}
