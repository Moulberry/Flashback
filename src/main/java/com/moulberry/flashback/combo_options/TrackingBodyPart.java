package com.moulberry.flashback.combo_options;

public enum TrackingBodyPart implements ComboOption {

    HEAD("Head"),
    BODY("Body"),
    ROOT("Root");

    private final String text;

    TrackingBodyPart(String text) {
        this.text = text;
    }

    @Override
    public String text() {
        return this.text;
    }

}
