package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.editor.keybinds.Keybind;
import com.moulberry.flashback.editor.keybinds.Keybinds;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImGuiListClipper;
import imgui.moulberry90.ImGuiViewport;
import imgui.moulberry90.callback.ImListClipperCallback;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiMouseButton;
import imgui.moulberry90.flag.ImGuiSelectableFlags;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class KeybindsWindow {

    private static String backedUpKeybind = null;
    private static int backupKey;
    private static boolean backupShiftMod;
    private static boolean backupCtrlMod;
    private static boolean backupAltMod;
    private static boolean backupSuperMod;

    public static void render(ImBoolean open, boolean newlyOpened) {
        ImGuiViewport viewport = ImGui.getMainViewport();
        if (newlyOpened) {
            ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing, 0.5f, 0.5f);
            ImGui.setNextWindowSize(viewport.getSizeX()/4f, viewport.getSizeY()/2f, ImGuiCond.Appearing);
        }

        final float warningSymbolWidth = ImGuiHelper.calcTextWidth("\u26A0");

        ImGui.setNextWindowSizeConstraints(250, 50, 5000, 5000);
        int flags = ImGuiWindowFlags.NoFocusOnAppearing;
        String title = I18n.get("flashback.keybinds");
        if (ImGui.begin(title + "###Keybinds", open, flags)) {
            for (Keybind keybind : Keybinds.KEYBINDS) {
                String descriptionRaw = keybind.getDescriptionRaw();
                String description = keybind.getDescription();

                if (ImGui.selectable(description + "##" + descriptionRaw, false, ImGuiSelectableFlags.DontClosePopups)) {
                    ImGui.openPopup(descriptionRaw + "##EditKeybind");
                }
                ImGui.sameLine();

                String longKeyIdent = keybind.longKeyIdentifier();

                StringBuilder conflicts = null;
                if (keybind.getKey() != 0) {
                    Set<Keybind> keybindsForKey = Keybinds.keybindsForKey.get(keybind.getKey());
                    for (Keybind other : keybindsForKey) {
                        if (other == keybind) continue;

                        if (keybind.hasSameMods(other)) {
                            if (conflicts == null) conflicts = new StringBuilder(I18n.get("flashback.keybinds.conflicts_with"));
                            conflicts.append('\n').append(other.getDescription());
                        }
                    }
                }

                float availX = ImGui.getContentRegionAvailX();
                float width = ImGuiHelper.calcTextWidth(longKeyIdent) + ImGui.getStyle().getFramePaddingX() * 2.0f;
                if (conflicts != null) {
                    width += warningSymbolWidth + ImGui.getStyle().getItemSpacingX();
                }
                ImGui.sameLine(0, availX - width);

                if (conflicts != null) {
                    ImGui.text("\u26A0");
                    ImGuiHelper.tooltip(conflicts.toString());
                    ImGui.sameLine();
                }

                ImGui.smallButton(longKeyIdent);

                if (ImGuiHelper.beginPopup(descriptionRaw + "##EditKeybind")) {
                    ImGuiHelper.setEditingKeybind(keybind);
                    if (backedUpKeybind == null || !backedUpKeybind.equals(keybind.getDescriptionRaw())) {
                        backedUpKeybind = keybind.getDescriptionRaw();
                        backupKey = keybind.getKey();
                        backupShiftMod = keybind.isShiftMod();
                        backupCtrlMod = keybind.isCtrlMod();
                        backupAltMod = keybind.isAltMod();
                        backupSuperMod = keybind.isSuperMod();
                    }

                    ImGui.text(description);
                    ImGui.button(longKeyIdent+"##KeybindPreview", -1, 0);
                    if (keybind.isForceScrollKey()) {
                        if (ImGui.isItemClicked()) {
                            long window = ImGui.getWindowViewport().getPlatformHandle();
                            boolean shiftDown = Keybind.isShiftDownGLFW(window);
                            boolean ctrlDown = Keybind.isCtrlDownGLFW(window);
                            boolean altDown = Keybind.isAltDownGLFW(window);
                            boolean superDown = Keybind.isSuperDownGLFW(window);

                            keybind.set(Keybind.FAKE_SCROLL_KEY, shiftDown,
                                InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? superDown : ctrlDown,
                                altDown,
                                InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? ctrlDown : superDown);
                        }
                    } else if (ImGui.isItemHovered()) {
                        for (int i = 0; i < ImGuiMouseButton.COUNT; i++) {
                            if (ImGui.isMouseClicked(i)) {
                                long window = ImGui.getWindowViewport().getPlatformHandle();
                                boolean shiftDown = Keybind.isShiftDownGLFW(window);
                                boolean ctrlDown = Keybind.isCtrlDownGLFW(window);
                                boolean altDown = Keybind.isAltDownGLFW(window);
                                boolean superDown = Keybind.isSuperDownGLFW(window);

                                keybind.set(-i-1, shiftDown,
                                    InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? superDown : ctrlDown,
                                    altDown,
                                    InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? ctrlDown : superDown);
                            }
                        }
                    }

                    if (ImGui.button(I18n.get("gui.ok"))) {
                        backedUpKeybind = null;
                        ImGui.closeCurrentPopup();
                    }
                    ImGui.sameLine();
                    if (ImGui.button(I18n.get("gui.cancel"))) {
                        keybind.set(backupKey, backupShiftMod, backupCtrlMod, backupAltMod, backupSuperMod);
                        backedUpKeybind = null;
                        ImGui.closeCurrentPopup();
                    }
                    ImGui.sameLine();
                    if (ImGui.button(I18n.get("flashback.keybinds.unbind"))) {
                        keybind.clear();
                        backedUpKeybind = null;
                        ImGui.closeCurrentPopup();
                    }
                    ImGui.endPopup();
                }
            }
        }
        ImGui.end();
    }

}
