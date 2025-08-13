package com.moulberry.flashback.combo_options;

import net.minecraft.client.resources.language.I18n;

public enum TrackingBodyPart implements ComboOption {

    HEAD("flashback.tracking_body_part.head"),
    BODY("flashback.tracking_body_part.body"),
    ROOT("flashback.tracking_body_part.root");

    private final String text;

    TrackingBodyPart(String text) {
        this.text = text;
    }

    @Override
    public String text() {
        return I18n.get(this.text);
    }

}
