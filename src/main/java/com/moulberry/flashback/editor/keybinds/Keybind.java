package com.moulberry.flashback.editor.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.utils.InputHelper;
import com.moulberry.lattice.keybind.KeybindInterface;
import com.moulberry.lattice.keybind.LatticeInputType;
import imgui.moulberry90.ImGuiIO;
import imgui.moulberry90.flag.ImGuiKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class Keybind implements KeybindInterface {

    public static final int FAKE_SCROLL_KEY = 17;

    private final String description;
    private int key;
    private boolean shiftMod;
    private boolean ctrlMod;
    private boolean altMod;
    private boolean superMod;
    private boolean forceScrollKey = false;

    private boolean ingameDownLastTime = false;

    public Keybind(String description, int key, boolean shiftMod, boolean ctrlMod, boolean altMod, boolean superMod) {
        this.description = description;
        this.key = key;
        this.shiftMod = shiftMod;
        this.ctrlMod = ctrlMod;
        this.altMod = altMod;
        this.superMod = superMod;

        Keybinds.updateMapping(this, 0);
    }

    public Keybind withForceScrollKey() {
        this.forceScrollKey = true;
        return this;
    }

    public boolean isForceScrollKey() {
        return this.forceScrollKey;
    }

    public int getKey() {
        return this.key;
    }

    public boolean isShiftMod() {
        return this.shiftMod;
    }

    public boolean isCtrlMod() {
        return this.ctrlMod;
    }

    public boolean isAltMod() {
        return this.altMod;
    }

    public boolean isSuperMod() {
        return this.superMod;
    }

    public void clear() {
        int oldKey = this.key;
        this.key = 0;
        this.shiftMod = false;
        this.ctrlMod = false;
        this.altMod = false;
        this.superMod = false;

        Keybinds.updateMapping(this, oldKey);
    }

    public void set(Keybind other) {
        if (other.description.equals(this.description)) {
            int oldKey = this.key;
            this.key = other.key;
            this.shiftMod = other.shiftMod;
            this.ctrlMod = other.ctrlMod;
            this.altMod = other.altMod;
            this.superMod = other.superMod;

            Keybinds.updateMapping(this, oldKey);
        }
    }

    public String toConfigValue() {
        if (this.key == 0) {
            return "none";
        }

        String key = KeybindHelper.imguiToConfig(this.key);
        if (key.equals("none")) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        if (this.shiftMod) builder.append("shift+");
        if (this.ctrlMod) builder.append("ctrl+");
        if (this.altMod) builder.append("alt+");
        if (this.superMod) builder.append("super+");
        builder.append(key);
        return builder.toString();
    }

    public void loadFromConfigValue(String configValue) {
        String originalConfigValue = configValue;
        configValue = configValue.toLowerCase(Locale.ROOT);
        configValue = configValue.replaceAll("[^a-z0-9+_]", "");

        if (configValue.equals("none")) {
            int oldKey = this.key;
            this.key = 0;
            this.shiftMod = this.ctrlMod = this.altMod = this.superMod = false;
            Keybinds.updateMapping(this, oldKey);
            return;
        }

        boolean shiftMod = false;
        boolean ctrlMod = false;
        boolean altMod = false;
        boolean superMod = false;
        while (true) {
            if (configValue.startsWith("shift+")) {
                shiftMod = true;
                configValue = configValue.substring(6);
            } else if (configValue.startsWith("ctrl+")) {
                ctrlMod = true;
                configValue = configValue.substring(5);
            } else if (configValue.startsWith("alt+")) {
                altMod = true;
                configValue = configValue.substring(4);
            } else if (configValue.startsWith("super+")) {
                superMod = true;
                configValue = configValue.substring(6);
            } else {
                configValue = configValue.substring(configValue.lastIndexOf("+")+1);
                int key = KeybindHelper.configToImgui(configValue);
                if (key != 0) {
                    int oldKey = this.key;
                    this.key = key;
                    this.shiftMod = shiftMod;
                    this.ctrlMod = ctrlMod;
                    this.altMod = altMod;
                    this.superMod = superMod;
                    Keybinds.updateMapping(this, oldKey);
                } else {
                    Flashback.LOGGER.error("Invalid keybind in config for {}: {}", this.description, originalConfigValue);
                }
                break;
            }
        }
    }

    public Keybind copy() {
        return new Keybind(this.description, this.key, this.shiftMod, this.ctrlMod, this.altMod, this.superMod);
    }

    public void set(int key, boolean shiftMod, boolean ctrlMod, boolean altMod, boolean superMod) {
        int oldKey = this.key;
        this.key = key;
        this.shiftMod = shiftMod;
        this.ctrlMod = ctrlMod;
        this.altMod = altMod;
        this.superMod = superMod;
        Keybinds.updateMapping(this, oldKey);
    }

    public boolean hasSameMods(Keybind other) {
        return this.shiftMod == other.shiftMod && this.ctrlMod == other.ctrlMod && this.altMod == other.altMod && this.superMod == other.superMod;
    }

    private static String getKeyNameFor(int key) {
        if (key == FAKE_SCROLL_KEY) {
            return I18n.get("flashback.keymod.scroll");
        }

        //#if MC>=12109
        return InputConstants.getKey(new KeyEvent(key, -1, 0)).getDisplayName().getString();
        //#else
        //$$ return InputConstants.getKey(key, -1).getDisplayName().getString();
        //#endif
    }

    public String longKeyIdentifier() {
        if (this.key == 0) {
            return I18n.get("key.keyboard.unknown");
        }

        String keyName;
        if (this.key < 0) {
            keyName = InputConstants.Type.MOUSE.getOrCreate(-this.key-1).getDisplayName().getString();
        } else {
            keyName = getKeyNameFor(this.key);
        }
        if (!this.shiftMod && !this.ctrlMod && !this.altMod && !this.superMod) return keyName;

        StringBuilder builder = new StringBuilder();
        if (this.shiftMod) builder.append(I18n.get("flashback.keymod.shift")).append("+");
        if (this.ctrlMod) {
            if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
                builder.append(I18n.get("flashback.keymod.mac_cmd")).append("+");
            } else {
                builder.append(I18n.get("flashback.keymod.ctrl")).append("+");
            }
        }
        if (this.altMod) builder.append(I18n.get("flashback.keymod.alt")).append("+");
        if (this.superMod) {
            if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
                builder.append(I18n.get("flashback.keymod.ctrl")).append("+");
            } else if (Util.getPlatform() == Util.OS.WINDOWS) {
                builder.append(I18n.get("flashback.keymod.win_super")).append("+");
            } else {
                builder.append(I18n.get("flashback.keymod.super")).append("+");
            }
        }
        builder.append(keyName);

        return builder.toString();
    }

    public boolean wouldBePressed(int key, boolean shiftMod, boolean ctrlMod, boolean altMod, boolean superMod) {
        if (this.key == 0) return false;
        if (this.key != key) return false;

        if (this.shiftMod != shiftMod) return false;
        if (this.ctrlMod != (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? superMod : ctrlMod)) return false;
        if (this.altMod != altMod) return false;
        if (this.superMod != (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? ctrlMod : superMod)) return false;

        return true;
    }

    private boolean checkKeyRaw() {
        if (this.key < 0) {
            return InputHelper.isMouseDownRaw(-this.key-1);
        } else {
            return InputHelper.isKeyDownRaw(this.key);
        }
    }

    private static boolean isShift(int key) {
        return key == ImGuiKey.LeftShift || key == ImGuiKey.RightShift;
    }

    private static boolean isCtrl(int key) {
        return key == ImGuiKey.LeftCtrl || key == ImGuiKey.RightCtrl;
    }

    private static boolean isAlt(int key) {
        return key == ImGuiKey.LeftAlt || key == ImGuiKey.RightAlt;
    }

    private static boolean isSuper(int key) {
        return key == ImGuiKey.LeftSuper || key == ImGuiKey.RightSuper;
    }

    public boolean isDown() {
        return this.areAllModifiersDown() && this.isDownIgnoreMods();
    }

    public boolean isDownIgnoreMods() {
        if (this.key == 0 || this.key == FAKE_SCROLL_KEY) {
            return false;
        }

        if (ReplayUI.isActive() && ReplayUI.isImGuiContextActive()) {
            return ImGuiHelper.isImGuiBindingDown(this.key);
        } else {
            return this.checkKeyRaw();
        }
    }

    public boolean isPressed(boolean repeat) {
        return this.areAllModifiersDown() && this.isPressedIgnoreMods(repeat);
    }

    public boolean isPressedIgnoreMods(boolean repeat) {
        if (this.key == 0 || this.key == FAKE_SCROLL_KEY) {
            return false;
        }

        if (ReplayUI.isActive() && ReplayUI.isImGuiContextActive()) {
            return ImGuiHelper.isImGuiBindingClicked(this.key, repeat);
        } else {
            boolean down = this.isDownIgnoreMods();
            boolean wasDown = this.ingameDownLastTime;
            this.ingameDownLastTime = down;

            return down && !wasDown;
        }
    }

    public boolean areAllModifiersDown() {
        if (this.key == 0) {
            return false;
        }

        boolean ctrlMod = this.ctrlMod;
        boolean superMod = this.superMod;

        if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
            ctrlMod = this.superMod;
            superMod = this.ctrlMod;
        }

        if (ReplayUI.isActive() && ReplayUI.isImGuiContextActive()) {
            ImGuiIO io = ReplayUI.getIO();

            // ImGui swaps getKeyCtrl/getKeySuper, so we don't have to
            if (!isShift(this.key) && this.shiftMod != io.getKeyShift()) return false;
            if (!isCtrl(this.key) && this.ctrlMod != io.getKeyCtrl()) return false;
            if (!isAlt(this.key) && this.altMod != io.getKeyAlt()) return false;
            if (!isSuper(this.key) && this.superMod != io.getKeySuper()) return false;
        } else {
            if (!isShift(this.key) && this.shiftMod != InputHelper.isShiftDownRaw()) return false;
            if (!isCtrl(this.key) && ctrlMod != InputHelper.isCtrlDownRaw()) return false;
            if (!isAlt(this.key) && this.altMod != InputHelper.isAltDownRaw()) return false;
            if (!isSuper(this.key) && superMod != InputHelper.isSuperDownRaw()) return false;
        }

        return true;
    }

    public String getDescriptionRaw() {
        return this.description;
    }

    public String getDescription() {
        return I18n.get("flashback.keybinds."+this.description);
    }

    @Override
    public Component getKeyMessage() {
        return Component.literal(this.longKeyIdentifier());
    }

    @Override
    public void setKey(LatticeInputType type, int value, boolean shiftMod, boolean ctrlMod, boolean altMod, boolean superMod) {
        int oldKey = this.key;
        switch (type) {
            case KEYSYM -> {
                return;
            }
            case SCANCODE -> {
                this.key = InputHelper.sdlScancodeToImguiKey(value);
            }
            case MOUSE -> {
                this.key = -InputHelper.sdlMouseToImguiMouse(value)-1;
            }
        }
        this.shiftMod = shiftMod;
        this.ctrlMod = InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? superMod : ctrlMod;
        this.altMod = altMod;
        this.superMod = InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? ctrlMod : superMod;
        Keybinds.updateMapping(this, oldKey);
    }

    @Override
    public void setUnbound() {
        int oldKey = this.key;
        this.key = 0;
        this.shiftMod = this.ctrlMod = this.altMod = this.superMod = false;
        Keybinds.updateMapping(this, oldKey);
    }

    @Override
    public Collection<Component> getConflicts() {
        return List.of();
    }
}
