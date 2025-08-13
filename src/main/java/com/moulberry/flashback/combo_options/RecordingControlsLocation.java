package com.moulberry.flashback.combo_options;

import net.minecraft.client.resources.language.I18n;

public enum RecordingControlsLocation implements ComboOption {

    RIGHT("flashback.recording_controls_location.right"),
    BELOW("flashback.recording_controls_location.below"),
    LEFT("flashback.recording_controls_location.left"),
    HIDDEN("flashback.recording_controls_location.hidden");

    private final String text;

    RecordingControlsLocation(String text) {
        this.text = text;
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
