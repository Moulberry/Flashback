package com.moulberry.flashback.editor.ui;

import com.moulberry.flashback.combo_options.ComboOption;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiComboFlags;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public class ImGuiHelper {

    public static String getString(ImString string) {
        StringBuilder builder = new StringBuilder();
        String str = string.get();
        for (int i=0; i<str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\0') break;
            builder.append(c);
        }
        return builder.toString();
    }

    public static ImString createResizableImString(String text) {
        ImString imString = new ImString(text);
        imString.inputData.isResizable = true;
        return imString;
    }

    public static int elementList(String name, List<String> elements, float width, int minLines, int maxLines, boolean framed, IntConsumer buttonConsumer) {
        if (minLines > maxLines) throw new IllegalArgumentException();

        if (width <= 0) {
            width = Math.max(ImGui.getContentRegionAvailX() + width, 4.0f);
        }

        float itemSpacingX = ImGui.getStyle().getItemSpacingX();
        float framePaddingX = ImGui.getStyle().getFramePaddingX();
        float availableSpace = width;
        if (framed) availableSpace -= framePaddingX*2f;

        int lines = 0;
        boolean scrollbar = false;
        if (minLines == maxLines) {
            lines = minLines;
            scrollbar = true; // assume there is a scrollbar
        } else {
            float consumedWidth = 0;
            for (String element : elements) {
                float elementWidth = framePaddingX*2f + ImGuiHelper.calcTextWidth(element);
                if (consumedWidth+elementWidth < availableSpace && consumedWidth > 0) {
                    consumedWidth += elementWidth + itemSpacingX;
                } else {
                    consumedWidth = elementWidth + itemSpacingX;
                    lines += 1;
                    if (lines > maxLines) {
                        lines = maxLines;
                        scrollbar = true;
                        break;
                    }
                }
            }
            if (lines < minLines) lines = minLines;
        }

        float lineHeight = ImGui.getFontSize() + ImGui.getStyle().getFramePaddingY()*2 + ImGui.getStyle().getItemSpacingY();
        boolean visible;
        if (framed) {
            visible = ImGui.beginChildFrame(ImGui.getID(name), width, lineHeight*lines + ImGui.getStyle().getFramePaddingY()*2 - ImGui.getStyle().getItemSpacingY());
        } else {
            visible = ImGui.beginChild(name, width, lineHeight*lines - ImGui.getStyle().getItemSpacingY());
        }

        int ret = -1;

        if (visible) {
            if (scrollbar) {
                availableSpace -= ImGui.getStyle().getScrollbarSize();
            }

            float consumedWidth = 0;
            for (int i=0; i<elements.size(); i++) {
                String element = elements.get(i);
                float elementWidth = framePaddingX*2f + ImGuiHelper.calcTextWidth(element);
                if (consumedWidth+elementWidth < availableSpace && consumedWidth > 0) {
                    ImGui.sameLine();
                    consumedWidth += elementWidth + itemSpacingX;
                } else {
                    consumedWidth = elementWidth + itemSpacingX;
                }
                ImGui.pushID(i);
                if (ImGui.button(element)) {
                    ret = i;
                }
                if (buttonConsumer != null) {
                    buttonConsumer.accept(i);
                }
                ImGui.popID();
            }
        }

        if (framed) {
            ImGui.endChildFrame();
        } else {
            ImGui.endChild();
        }

        return ret;
    }

    private static final int[] enumComboSharedArray = new int[]{0};
    private static final Map<Class<?>, Object[]> enumComboCachedValues = new HashMap<>();
    private static final Map<Class<?>, String[]> enumComboCachedText = new HashMap<>();

    public static <T extends Enum<?> & ComboOption> T enumCombo(String label, T c, T[] values) {
        int currentIndex = -1;
        for (int i = 0; i < values.length; i++) {
            T value = values[i];
            if (value == c) {
                currentIndex = i;
                break;
            }
        }

        String[] text = new String[values.length];
        for (int i = 0; i < text.length; i++) {
            text[i] = values[i].text();
        }

        enumComboSharedArray[0] = currentIndex;
        combo(label, enumComboSharedArray, text);
        if (enumComboSharedArray[0] == currentIndex) {
            return c;
        } else {
            return values[enumComboSharedArray[0]];
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<?> & ComboOption> T enumCombo(String label, T c) {
        Class<?> clazz = c.getClass();
        int currentIndex = c.ordinal();

        String[] text = enumComboCachedText.get(clazz);
        if (text == null) {
            T[] options = (T[]) clazz.getEnumConstants();
            enumComboCachedValues.put(clazz, options);

            text = new String[options.length];
            for (int i = 0; i < text.length; i++) {
                text[i] = options[i].text();
            }
            enumComboCachedText.put(clazz, text);
        }

        enumComboSharedArray[0] = currentIndex;
        combo(label, enumComboSharedArray, text);
        if (enumComboSharedArray[0] == currentIndex) {
            return c;
        } else {
            return (T) enumComboCachedValues.get(clazz)[enumComboSharedArray[0]];
        }
    }

    public static boolean combo(String label, int[] currentItem, String[] values) {
        return combo(label, currentItem, values, ImGuiComboFlags.None);
    }

    public static boolean combo(String label, int[] currentItem, String[] values, int imguiComboFlags) {
        boolean changed = false;

        if (currentItem[0] >= values.length && values.length > 0) {
            currentItem[0] = values.length-1;
        }

        if (ImGui.beginCombo(label, currentItem[0] < 0 ? "" : values[currentItem[0]], imguiComboFlags)) {
            for (int i=0; i<values.length; i++) {
                ImGui.pushID(i);
                boolean selected = i == currentItem[0];
                if (ImGui.selectable(values[i], selected) && !selected) {
                    currentItem[0] = i;
                    changed = true;
                }
                if (selected) ImGui.setItemDefaultFocus();
                ImGui.popID();
            }

            ImGui.endCombo();
        }
        return changed;
    }

    private static boolean closeableModalOnTopLast = false;
    private static boolean closeableModalOnTop = false;
    private static boolean inputIntScrolledLastFrame = false;
    private static boolean inputIntScrolledThisFrame = false;

    private static boolean wantSpecialInputLastFrame = false;
    private static boolean wantSpecialInputThisFrame = false;
    private static StringBuilder specialInput = new StringBuilder();
    private static int backspaceCount = 0;

    private static boolean handledFocusNext = false;
    private static boolean focusNext = false;

    public static void endFrame() {
        closeableModalOnTopLast = closeableModalOnTop;
        inputIntScrolledLastFrame = inputIntScrolledThisFrame;
        inputIntScrolledThisFrame = false;

        wantSpecialInputLastFrame = wantSpecialInputThisFrame;
        wantSpecialInputThisFrame = false;

        handledFocusNext = false;
        focusNext = false;

        if (!wantSpecialInputLastFrame) specialInput.setLength(0);
    }

    public static String modifyFromInput(String existing) {
        wantSpecialInputThisFrame = true;
        String newInput = specialInput.toString();

        existing = existing.substring(0, Math.max(0, existing.length() - backspaceCount));
        existing += newInput;

        specialInput.setLength(0);
        backspaceCount = 0;
        return existing;
    }

    public static boolean getWantsSpecialInput() {
        return wantSpecialInputLastFrame;
    }

    public static boolean addInputCharacter(char c) {
        if (wantSpecialInputLastFrame) {
            specialInput.append(c);
            return true;
        }
        return false;
    }

    public static boolean backspaceInput(int mods) {
        if (wantSpecialInputLastFrame) {
            if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                specialInput.setLength(0);
                backspaceCount = 10000;
                return true;
            }

            if (specialInput.length() > 0) {
                specialInput.setLength(specialInput.length() - 1);
            } else {
                backspaceCount += 1;
            }
            return true;
        }
        return false;
    }

    public static boolean beginPopup(String id) {
        return beginPopup(id, ImGuiWindowFlags.None);
    }

    public static boolean beginPopup(String id, int imGuiWindowFlags) {
        if (ImGui.beginPopup(id, imGuiWindowFlags | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings)) {
            closeableModalOnTop = false;
            return true;
        } else {
            return false;
        }
    }

    public static boolean beginPopupModal(String id) {
        return beginPopupModal(id, ImGuiWindowFlags.None);
    }

    public static boolean beginPopupModal(String id, int imGuiWindowFlags) {
        if (ImGui.beginPopupModal(id, imGuiWindowFlags | ImGuiWindowFlags.NoSavedSettings)) {
            closeableModalOnTop = false;
            return true;
        } else {
            return false;
        }
    }

    public static boolean beginPopupModalCloseable(String id) {
        return beginPopupModalCloseable(id, ImGuiWindowFlags.None);
    }

    public static boolean beginPopupModalCloseable(String id, int imGuiWindowFlags) {
        if (ImGui.beginPopupModal(id, new ImBoolean(true), imGuiWindowFlags | ImGuiWindowFlags.NoSavedSettings)) {
            closeableModalOnTop = true;
            return true;
        } else {
            return false;
        }
    }

    public static void endPopupModalCloseable() {
        if (closeableModalOnTop && closeableModalOnTopLast && ReplayUI.consumeNavClose()) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    public static float borderIndentation = 0;

    public static void setupBorder() {
        ImGui.beginGroup();

        float windowPadding = ImGui.getStyle().getWindowPaddingX();
        borderIndentation += windowPadding;

        ImGui.indent(windowPadding);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + ImGui.getStyle().getWindowPaddingY());
    }

    public static void finishBorder() {
        float windowPadding = ImGui.getStyle().getWindowPaddingX();
        borderIndentation -= windowPadding;

        ImGui.unindent(windowPadding);
        ImGui.endGroup();

        ImGui.getWindowDrawList().addRect(ImGui.getItemRectMinX(), ImGui.getItemRectMinY(),
            ImGui.getItemRectMinX() + ImGui.getContentRegionAvailX(),
            ImGui.getItemRectMaxY() + ImGui.getStyle().getWindowPaddingY(), ImGui.getColorU32(ImGuiCol.Separator));

        ImGui.setCursorPosY(ImGui.getCursorPosY() + ImGui.getStyle().getWindowPaddingY());
    }

    public static float calcTextWidth(String text) {
        ImVec2 textSizeVec = new ImVec2();
        ImGui.calcTextSize(textSizeVec, text);
        return textSizeVec.x;
    }

    public static void helpMarker(String message) {
        ImGui.textDisabled("(?)");
        tooltip(message);
    }

    public static void tooltip(String message) {
        tooltip(message, ImGuiHoveredFlags.None);
    }

    public static void tooltip(String message, int imGuiHoveredFlags) {
        if (ImGui.isItemHovered(imGuiHoveredFlags)) {
            drawTooltip(message);
        }
    }

    public static void drawTooltip(String message) {
        ImGui.beginTooltip();
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 35.0f);
        ImGui.textUnformatted(message);
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
    }

    public static void disabledMenuItem(String label, String disabledMessage) {
        ImGui.beginDisabled();
        ImGui.menuItem(label);
        ImGui.endDisabled();
        tooltip(disabledMessage, ImGuiHoveredFlags.AllowWhenDisabled);
    }

    public static void separatorWithText(String text) {
        float textStartX = ImGui.getCursorScreenPosX() + ImGui.getStyle().getIndentSpacing();

        float size = ImGui.getWindowSizeX() - borderIndentation;

        if (ImGui.isRectVisible(size, ImGui.getFontSize())) {
            float textEndX = textStartX + ImGuiHelper.calcTextWidth(text);
            float lineEndX = ImGui.getWindowPosX() + size;
            float lineY = ImGui.getCursorScreenPosY() + ImGui.getFontSize()/2f;

            ImGui.getWindowDrawList().addLine(ImGui.getCursorScreenPosX() - 4, lineY,
                    Math.min(lineEndX, textStartX) - 4, lineY,
                    ImGui.getColorU32(ImGuiCol.Separator));

            if (textEndX + 4 < lineEndX) {
                ImGui.getWindowDrawList().addLine(textEndX + 4, lineY,
                        lineEndX - 4, lineY,
                        ImGui.getColorU32(ImGuiCol.Separator));
            }
        }

        ImGui.setCursorScreenPos(textStartX, ImGui.getCursorScreenPosY());
        ImGui.textColored(ImGui.getColorU32(ImGuiCol.TextDisabled), text);
    }

    public static boolean radio(String label, int[] currentItem, String[] values) {
        int item = currentItem[0];
        int newItem = -1;

        for (int i = 0; i < values.length; i++) {
            String value = values[i];

            if (i > 0) ImGui.sameLine();
            if (item == i) ImGui.beginDisabled();
            if (ImGui.button(value)) newItem = i;
            if (item == i) ImGui.endDisabled();
        }

        if (label != null) {
            ImGui.sameLine();
            ImGui.text(label);
        }

        if (newItem != -1) {
            currentItem[0] = newItem;
            return true;
        } else {
            return false;
        }
    }

    public static int buttons(String... labels) {
        if (labels.length == 0) return -1;

        // Calculate widths
        float[] widths = new float[labels.length];
        float totalRawWidth = 0;
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            float width = ImGuiHelper.calcTextWidth(label);
            widths[i] = width;
            totalRawWidth += width;
        }

        // Calculate extraPadding
        float availableWidth = ImGui.getContentRegionAvailX();
        float freeSpace = availableWidth - totalRawWidth - ImGui.getStyle().getItemSpacingX()*(labels.length - 1);
        float extraPadding = Math.max(ImGui.getStyle().getFramePaddingX()*2, freeSpace / labels.length);

        // Draw buttons
        float height = ImGui.getFontSize() + ImGui.getStyle().getFramePaddingY()*2f;
        int pressed = -1;
        for (int i = 0; i < labels.length; i++) {
            if (ImGui.button(labels[i], widths[i] + extraPadding, height)) {
                pressed = i;
            }
            if (i != labels.length-1) ImGui.sameLine();
        }

        return pressed;
    }

    private static int pushedColors = 0;
    private static int pushedStyleVars = 0;

    public static void popAllStyleColors() {
        ImGui.popStyleColor(pushedColors);
        pushedColors = 0;
    }

    public static void popAllStyleVars() {
        ImGui.popStyleVar(pushedStyleVars);
        pushedStyleVars = 0;
    }

    public static void pushStyleColor(int imGuiCol, float r, float g, float b, float a) {
        ImGui.pushStyleColor(imGuiCol, r, g, b, a);
        pushedColors += 1;
    }

    public static void pushStyleColor(int imGuiCol, int r, int g, int b, int a) {
        ImGui.pushStyleColor(imGuiCol, r, g, b, a);
        pushedColors += 1;
    }

    public static void pushStyleColor(int imGuiCol, int col) {
        ImGui.pushStyleColor(imGuiCol, col);
        pushedColors += 1;
    }

    public static void popStyleColor() {
        if (pushedColors >= 1) {
            ImGui.popStyleColor();
            pushedColors -= 1;
        }
    }

    public static void popStyleColor(int count) {
        count = Math.min(count, pushedColors);
        if (count == 0) return;

        ImGui.popStyleColor(count);
        pushedColors -= count;
    }

    public static void pushStyleVar(int imGuiStyleVar, float val) {
        ImGui.pushStyleVar(imGuiStyleVar, val);
        pushedStyleVars += 1;
    }

    public static void pushStyleVar(int imGuiStyleVar, float valX, float valY) {
        ImGui.pushStyleVar(imGuiStyleVar, valX, valY);
        pushedStyleVars += 1;
    }

    public static void popStyleVar() {
        if (pushedStyleVars >= 1) {
            ImGui.popStyleVar();
            pushedStyleVars -= 1;
        }
    }

    public static void popStyleVar(int count) {
        count = Math.min(count, pushedStyleVars);
        if (count == 0) return;

        ImGui.popStyleVar(count);
        pushedStyleVars -= count;
    }

}
