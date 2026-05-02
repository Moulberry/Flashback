package com.moulberry.flashback.combo_options;

import net.minecraft.client.resources.language.I18n;

public enum ExportProjection implements ComboOption {

    PERSPECTIVE("flashback.projection.perspective"),
    ORTHOGRAPHIC("flashback.projection.orthographic"),
    CUBE_MAP("flashback.projection.cube_map"),
    EQUIRECTANGULAR("flashback.projection.equirectangular");

    private final String key;

    ExportProjection(String key) {
        this.key = key;
    }

    @Override
    public String text() {
        return I18n.get(this.key);
    }

}
