package com.moulberry.flashback.combo_options;

public enum WeatherOverride implements ComboOption {

    NONE("No Override"),
    CLEAR("Clear"),
    OVERCAST("Overcast"),
    RAINING("Raining"),
    SNOWING("Snowing"),
    THUNDERING("Thundering");

    private final String text;

    WeatherOverride(String text) {
        this.text = text;
    }

    @Override
    public String text() {
        return this.text;
    }

}
