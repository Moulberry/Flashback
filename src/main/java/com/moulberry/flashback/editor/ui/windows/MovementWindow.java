package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.MovementDirection;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImGuiViewport;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.type.ImBoolean;
import net.minecraft.client.resources.language.I18n;

public class MovementWindow {

    private static boolean wasDocked = false;

    public static void render(ImBoolean open, boolean newlyOpened) {
        if (newlyOpened) {
            ImGuiViewport viewport = ImGui.getMainViewport();
            ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing, 0.5f, 0.5f);
        }

        ImGui.setNextWindowSizeConstraints(250, 50, 5000, 5000);
        int flags = ImGuiWindowFlags.NoFocusOnAppearing;
        if (!wasDocked) {
            flags |= ImGuiWindowFlags.AlwaysAutoResize;
        }
        String title = I18n.get("flashback.movement");
        if (ImGui.begin(title + "###Movement", open, flags)) {
            wasDocked = ImGui.isWindowDocked();

            FlashbackConfigV1 config = Flashback.getConfig();

            MovementDirection newDirection = ImGuiHelper.enumCombo(I18n.get("flashback.movement_direction"), config.editorMovement.flightDirection);
            if (newDirection != config.editorMovement.flightDirection) {
                config.editorMovement.flightDirection = newDirection;
                config.delayedSaveToDefaultFolder();
            }

            float[] momentum = new float[]{config.editorMovement.flightMomentum};
            ImGui.sliderFloat(I18n.get("flashback.momentum"), momentum, 0.0f, 1.0f);
            if (config.editorMovement.flightMomentum != momentum[0]) {
                config.editorMovement.flightMomentum = momentum[0];
                config.delayedSaveToDefaultFolder();
            }

            if (ImGui.checkbox(I18n.get("flashback.lock_x"), config.editorMovement.flightLockX)) {
                config.editorMovement.flightLockX = !config.editorMovement.flightLockX;
                config.delayedSaveToDefaultFolder();
            }
            ImGui.sameLine();
            if (ImGui.checkbox(I18n.get("flashback.lock_y"), config.editorMovement.flightLockY)) {
                config.editorMovement.flightLockY = !config.editorMovement.flightLockY;
                config.delayedSaveToDefaultFolder();
            }
            ImGui.sameLine();
            if (ImGui.checkbox(I18n.get("flashback.lock_z"), config.editorMovement.flightLockZ)) {
                config.editorMovement.flightLockZ = !config.editorMovement.flightLockZ;
                config.delayedSaveToDefaultFolder();
            }

            if (ImGui.checkbox(I18n.get("flashback.lock_yaw"), config.editorMovement.flightLockYaw)) {
                config.editorMovement.flightLockYaw = !config.editorMovement.flightLockYaw;
                config.delayedSaveToDefaultFolder();
            }
            ImGui.sameLine();
            if (ImGui.checkbox(I18n.get("flashback.lock_pitch"), config.editorMovement.flightLockPitch)) {
                config.editorMovement.flightLockPitch = !config.editorMovement.flightLockPitch;
                config.delayedSaveToDefaultFolder();
            }
        }
        ImGui.end();
    }

}
