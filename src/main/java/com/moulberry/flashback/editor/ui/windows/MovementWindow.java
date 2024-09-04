package com.moulberry.flashback.editor.ui.windows;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.WindowOpenState;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
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

    private static WindowOpenState wasOpen = WindowOpenState.UNKNOWN;

    public static void render() {
        if (!Flashback.getConfig().openedWindows.contains("movement")) {
            wasOpen = WindowOpenState.CLOSED;
            return;
        }
        if (wasOpen == WindowOpenState.CLOSED) {
            ImGuiViewport viewport = ImGui.getMainViewport();
            ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing, 0.5f, 0.5f);
        }
        wasOpen = WindowOpenState.OPEN;

        FlashbackConfig config = Flashback.getConfig();

        ImGui.setNextWindowSizeConstraints(250, 50, 5000, 5000);
        if (ImGui.begin("Movement###Movement", ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoFocusOnAppearing)) {
            int[] direction = new int[]{config.flightCameraDirection ? 1 : 0};

            ImGuiHelper.combo("Direction", direction, new String[]{
                "Horizontal",
                "Camera"
            });
            boolean flightCameraDirection = direction[0] == 1;
            if (config.flightCameraDirection != flightCameraDirection) {
                config.flightCameraDirection = flightCameraDirection;
                config.saveToDefaultFolder();
            }

            float[] momentum = new float[]{config.flightMomentum};
            ImGui.sliderFloat("Momentum", momentum, 0.0f, 1.0f);
            if (config.flightMomentum != momentum[0]) {
                config.flightMomentum = momentum[0];
                config.saveToDefaultFolder();
            }

            if (ImGui.checkbox("Lock X", config.flightLockX)) {
                config.flightLockX = !config.flightLockX;
                config.saveToDefaultFolder();
            }
            ImGui.sameLine();
            if (ImGui.checkbox("Lock Y", config.flightLockY)) {
                config.flightLockY = !config.flightLockY;
                config.saveToDefaultFolder();
            }
            ImGui.sameLine();
            if (ImGui.checkbox("Lock Z", config.flightLockZ)) {
                config.flightLockZ = !config.flightLockZ;
                config.saveToDefaultFolder();
            }

            if (ImGui.checkbox("Lock Yaw", config.flightLockYaw)) {
                config.flightLockYaw = !config.flightLockYaw;
                config.saveToDefaultFolder();
            }
            ImGui.sameLine();
            if (ImGui.checkbox("Lock Pitch", config.flightLockPitch)) {
                config.flightLockPitch = !config.flightLockPitch;
                config.saveToDefaultFolder();
            }

        }
        ImGui.end();
    }

}
