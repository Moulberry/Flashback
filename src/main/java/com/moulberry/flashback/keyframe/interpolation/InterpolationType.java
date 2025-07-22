package com.moulberry.flashback.keyframe.interpolation;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.ComboOption;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;

import java.util.List;

public enum InterpolationType implements ComboOption {

    SMOOTH(SidedInterpolationType.SMOOTH, SidedInterpolationType.SMOOTH, "flashback.interpolation_type.smooth"),
    LINEAR(SidedInterpolationType.LINEAR, SidedInterpolationType.LINEAR, "flashback.interpolation_type.linear"),
    EASE_IN(SidedInterpolationType.EASE, SidedInterpolationType.LINEAR, "flashback.interpolation_type.ease_in"),
    EASE_OUT(SidedInterpolationType.LINEAR, SidedInterpolationType.EASE, "flashback.interpolation_type.ease_out"),
    EASE_IN_OUT(SidedInterpolationType.EASE, SidedInterpolationType.EASE, "flashback.interpolation_type.ease_in_out"),
    HOLD(SidedInterpolationType.HOLD, SidedInterpolationType.HOLD, "flashback.interpolation_type.hold"),
    HERMITE(SidedInterpolationType.HERMITE, SidedInterpolationType.HERMITE, "flashback.interpolation_type.hermite");

    public static final InterpolationType[] INTERPOLATION_TYPES = values();

    public static InterpolationType getDefault() {
        FlashbackConfigV1 config = Flashback.getConfig();
        if (config != null && config.keyframes.defaultInterpolationType != null) {
            return config.keyframes.defaultInterpolationType;
        }
        return InterpolationType.SMOOTH;
    }

    private static String[] NAMES = null;
    private static Language lastLanguage = null;

    public static String[] getNames() {
        Language currentLanguage = Language.getInstance();
        if (NAMES == null || lastLanguage != currentLanguage) {
            lastLanguage = currentLanguage;
            InterpolationType[] values = values();
            NAMES = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                NAMES[i] = values[i].text();
            }
        }
        return NAMES;
    }

    public final SidedInterpolationType leftSide;
    public final SidedInterpolationType rightSide;
    public final String name;

    InterpolationType(SidedInterpolationType leftSide, SidedInterpolationType rightSide, String name) {
        this.leftSide = leftSide;
        this.rightSide = rightSide;
        this.name = name;
    }

    @Override
    public String text() {
        return I18n.get(this.name);
    }

    @Override
    public String toString() {
        return this.text();
    }

}
