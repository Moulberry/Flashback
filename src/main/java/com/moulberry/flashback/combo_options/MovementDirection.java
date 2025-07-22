package com.moulberry.flashback.combo_options;

import net.minecraft.client.resources.language.I18n;

public enum MovementDirection implements ComboOption {

    HORIZONTAL("flashback.movement_direction.horizontal"),
    CAMERA("flashback.movement_direction.camera");

    private final String text;

    MovementDirection(String text) {
        this.text = text;
    }

    @Override
    public String text() {
        return I18n.get(this.text);
    }

    @Override
    public String toString() {
        return this.name();
    }

}
