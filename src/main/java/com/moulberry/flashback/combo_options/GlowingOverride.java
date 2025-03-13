package com.moulberry.flashback.combo_options;

public enum GlowingOverride implements ComboOption {

    DEFAULT("Default"),
    FORCE_GLOW("Force Glow"),
    FORCE_NO_GLOW("Force No Glow");

    private final String text;

    GlowingOverride(String text) {
        this.text = text;
    }

    @Override
    public String text() {
        return this.text;
    }

}
