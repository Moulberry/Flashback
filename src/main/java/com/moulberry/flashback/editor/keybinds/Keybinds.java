package com.moulberry.flashback.editor.keybinds;

import com.moulberry.flashback.configuration.FlashbackConfigV1;
import imgui.moulberry90.flag.ImGuiKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Keybinds {

    public static Map<Integer, Set<Keybind>> keybindsForKey = new HashMap<>();

    public static final Keybind PAUSE = new Keybind("pause", ImGuiKey.P, false, false, false, false);

    public static final Keybind COPY = new Keybind("copy", ImGuiKey.C, false, true, false, false);
    public static final Keybind PASTE = new Keybind("paste", ImGuiKey.V, false, true, false, false);
    public static final Keybind UNDO = new Keybind("undo", ImGuiKey.Z, false, true, false, false);
    public static final Keybind REDO = new Keybind("redo", ImGuiKey.Y, false, true, false, false);

    public static final Keybind MARK_IN = new Keybind("mark_in", ImGuiKey.I, false, false, false, false);
    public static final Keybind MARK_OUT = new Keybind("mark_out", ImGuiKey.O, false, false, false, false);
    public static final Keybind CLEAR_IN = new Keybind("clear_in", ImGuiKey.I, false, true, false, false);
    public static final Keybind CLEAR_OUT = new Keybind("clear_out", ImGuiKey.O, false, true, false, false);

    public static final Keybind ZOOM_IN = new Keybind("zoom_in", ImGuiKey.Equal, false, false, false, false);
    public static final Keybind ZOOM_OUT = new Keybind("zoom_out", ImGuiKey.Minus, false, false, false, false);

    public static final Keybind ADD_CAMERA = new Keybind("add_camera", 0, false, false, false, false);

    public static final Keybind TIMELINE_ZOOM_SCROLL = new Keybind("timeline_zoom_scroll", Keybind.FAKE_SCROLL_KEY, false, false, false, false).withForceScrollKey();
    public static final Keybind TIMELINE_MOVE_SCROLL = new Keybind("timeline_move_scroll", Keybind.FAKE_SCROLL_KEY, false, true, false, false).withForceScrollKey();

    public static final Keybind ROLL_CW = new Keybind("roll_cw", 0, false, false, false, false);
    public static final Keybind ROLL_CCW = new Keybind("roll_ccw", 0, false, false, false, false);

    public static final List<Keybind> KEYBINDS = List.of(PAUSE, COPY, PASTE, UNDO, REDO, MARK_IN, MARK_OUT, CLEAR_IN, CLEAR_OUT, ZOOM_IN, ZOOM_OUT, ADD_CAMERA,
        TIMELINE_ZOOM_SCROLL, TIMELINE_MOVE_SCROLL, ROLL_CW, ROLL_CCW);

    public static void updateMapping(Keybind keybind, int old) {
        if (keybind.getKey() == old) return;

        if (old != 0 && keybindsForKey.containsKey(old)) {
            Set<Keybind> set = keybindsForKey.get(old);
            set.remove(keybind);
        }

        if (keybind.getKey() != 0) {
            keybindsForKey.computeIfAbsent(keybind.getKey(), k -> new HashSet<>())
                .add(keybind);
        }
    }

    public static void load(FlashbackConfigV1 flashbackConfig) {
        for (Keybind keybind : KEYBINDS) {
            String description = keybind.getDescriptionRaw();
            String keybindValue = flashbackConfig.keybinds.get(description);

            if (keybindValue != null) {
                keybind.loadFromConfigValue(keybindValue);
            }
        }
    }

    public static void save(FlashbackConfigV1 flashbackConfig) {
        for (Keybind keybind : KEYBINDS) {
            String description = keybind.getDescriptionRaw();
            String keybindValue = keybind.toConfigValue();
            flashbackConfig.keybinds.put(description, keybindValue);
        }
    }

}
