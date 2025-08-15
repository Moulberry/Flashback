package com.moulberry.flashback.combo_options;

import net.minecraft.client.resources.language.I18n;

public enum MarkerColour implements ComboOption {

    RED("color.minecraft.red", 0xFFFF0000),
    GREEN("color.minecraft.green", 0xFF00FF00),
    BLUE("color.minecraft.blue", 0xFF0000FF),
    ORANGE("color.minecraft.orange", 0xFFFF681F),
    MAGENTA("color.minecraft.magenta", 0xFFFF00FF),
    LIGHT_BLUE("color.minecraft.light_blue", 0xFF9AC0CD),
    YELLOW("color.minecraft.yellow", 0xFFFFFF00),
    LIME("color.minecraft.lime", 0xFFBFFF00),
    PINK("color.minecraft.pink", 0xFFFF69B4),
    CYAN("color.minecraft.cyan", 0xFF00FFFF),
    PURPLE("color.minecraft.purple", 0xFFA020F0),
    BROWN("color.minecraft.brown", 0xFF8B4513),
    WHITE("color.minecraft.white", 0xFFFFFFFF),
    LIGHT_GRAY("color.minecraft.light_gray", 0xFFD3D3D3),
    GRAY("color.minecraft.gray", 0xFF808080),
    BLACK("color.minecraft.black", 0xFF000000),
    CUSTOM_RGB("flashback.custom_rgb", -1);

    private final String text;
    public final int colour;

    MarkerColour(String text, int colour) {
        this.text = text;
        this.colour = colour;
    }

    @Override
    public String text() {
        return I18n.get(this.text);
    }

    @Override
    public String toString() {
        return this.text();
    }

}
