package com.moulberry.flashback.combo_options;

import net.minecraft.client.resources.language.I18n;

public enum GlowingOverride implements ComboOption {

    DEFAULT("flashback.glowing_override.default"),
    FORCE_GLOW("flashback.glowing_override.force_glow"),
    FORCE_NO_GLOW("flashback.glowing_override.force_no_glow");

    private final String text;

    GlowingOverride(String text) {
        this.text = text;
    }

    @Override
    public String text() {
        return I18n.get(this.text);
    }

}
