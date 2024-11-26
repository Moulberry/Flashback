package com.moulberry.flashback.editor.ui.windows;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.editor.ui.WindowOpenState;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
        if (ImGui.begin("Movement###Movement", open, flags)) {
            wasDocked = ImGui.isWindowDocked();

            FlashbackConfig config = Flashback.getConfig();
            int[] direction = new int[]{config.flightCameraDirection ? 1 : 0};

            ImGuiHelper.combo("Direction", direction, new String[]{
                "Horizontal",
                "Camera"
            });
            boolean flightCameraDirection = direction[0] == 1;
            if (config.flightCameraDirection != flightCameraDirection) {
                config.flightCameraDirection = flightCameraDirection;
                config.delayedSaveToDefaultFolder();
            }

            float[] momentum = new float[]{config.flightMomentum};
            ImGui.sliderFloat("Momentum", momentum, 0.0f, 1.0f);
            if (config.flightMomentum != momentum[0]) {
                config.flightMomentum = momentum[0];
                config.delayedSaveToDefaultFolder();
            }

            if (ImGui.checkbox("Lock X", config.flightLockX)) {
                config.flightLockX = !config.flightLockX;
                config.delayedSaveToDefaultFolder();
            }
            ImGui.sameLine();
            if (ImGui.checkbox("Lock Y", config.flightLockY)) {
                config.flightLockY = !config.flightLockY;
                config.delayedSaveToDefaultFolder();
            }
            ImGui.sameLine();
            if (ImGui.checkbox("Lock Z", config.flightLockZ)) {
                config.flightLockZ = !config.flightLockZ;
                config.delayedSaveToDefaultFolder();
            }

            if (ImGui.checkbox("Lock Yaw", config.flightLockYaw)) {
                config.flightLockYaw = !config.flightLockYaw;
                config.delayedSaveToDefaultFolder();
            }
            ImGui.sameLine();
            if (ImGui.checkbox("Lock Pitch", config.flightLockPitch)) {
                config.flightLockPitch = !config.flightLockPitch;
                config.delayedSaveToDefaultFolder();
            }
        }
        ImGui.end();
    }

}
