package com.moulberry.flashback.editor.ui;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class ReplayUIDefaults {

    public static final String LAYOUT = """
            [Window][###Timeline]
            Pos=0,830
            Size=1920,250
            Collapsed=0
            DockId=0x00000002,0

            [Window][Visuals]
            Pos=1670,0
            Size=250,830
            Collapsed=0
            DockId=0x00000004,0

            [Docking][Data]
            DockSpace     ID=0x8B93E3BD Window=0xA787BDB4 Pos=0,22 Size=1920,1080 Split=Y Selected=0x1F1A625A
              DockNode    ID=0x00000001 Parent=0x8B93E3BD SizeRef=1920,830 Split=X Selected=0x1F1A625A
                DockNode  ID=0x00000003 Parent=0x00000001 SizeRef=1670,830 CentralNode=1 NoTabBar=1 Selected=0x1F1A625A
                DockNode  ID=0x00000004 Parent=0x00000001 SizeRef=250,830 Selected=0x595032EA
              DockNode    ID=0x00000002 Parent=0x8B93E3BD SizeRef=1920,250 Selected=0xBF88A430
            """;

    public static void applyStyle(ImGuiStyle style) {
        ImGui.styleColorsDark(style);

//        for (int i = 0; i < ImGuiCol.COUNT; i++) {
//            style.setColor(i, 0xFF0000FF);
//        }

        style.setColor(ImGuiCol.Text, 0xFFFFFFFF);
        style.setColor(ImGuiCol.TextDisabled, 0xFFAAAAAA);
        style.setColor(ImGuiCol.Separator, 0xFFAAAAAA);

        int windowBg = 0xCC282020;
        style.setColor(ImGuiCol.WindowBg, windowBg);
        style.setColor(ImGuiCol.Border, brighten(windowBg, 150));

        int titleBg = 0xFF1b1515;
        style.setColor(ImGuiCol.TitleBg, titleBg);
        style.setColor(ImGuiCol.TitleBgActive, brighten(titleBg, 140));

        int popupBg = 0xFF382828;
        style.setColor(ImGuiCol.PopupBg, popupBg);

        int frameBg = 0xAA3F3838;
        style.setColor(ImGuiCol.FrameBg, frameBg);
        style.setColor(ImGuiCol.FrameBgActive, darken(frameBg));
        style.setColor(ImGuiCol.FrameBgHovered, brighten(frameBg));

        int button = 0xFFCB6800;
        style.setColor(ImGuiCol.Button, button);
        style.setColor(ImGuiCol.ButtonActive, darken(button));
        style.setColor(ImGuiCol.ButtonHovered, brighten(button));

        style.setColor(ImGuiCol.CheckMark, 0xFFFB8800);

        int header = 0x80BB5F00;
        style.setColor(ImGuiCol.Header, header);
        style.setColor(ImGuiCol.HeaderActive, darken(header));
        style.setColor(ImGuiCol.HeaderHovered, brighten(header));

        style.setColor(ImGuiCol.MenuBarBg, windowBg);

        style.setColor(ImGuiCol.TabUnfocusedActive, 0xCC383030);
        style.setColor(ImGuiCol.TabActive, 0xFF484040);
        style.setColor(ImGuiCol.ModalWindowDimBg, 0x58CCCCCC);

        if (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_K) != 0) {
            ImGui.styleColorsDark(style);
        }
    }

    private static int brighten(int abgr) {
        return brighten(abgr, 120);
    }

    private static int brighten(int abgr, int percentage) {
        int alpha = (abgr >> 24) & 0xFF;
        int blue = (abgr >> 16) & 0xFF;
        int green = (abgr >> 8) & 0xFF;
        int red = abgr & 0xFF;

        blue = Math.min(0xFF, blue * percentage/100);
        green = Math.min(0xFF, green * percentage/100);
        red = Math.min(0xFF, red * percentage/100);

        return alpha << 24 | blue << 16 | green << 8 | red;
    }

    private static int darken(int abgr) {
        int alpha = (abgr >> 24) & 0xFF;
        int blue = (abgr >> 16) & 0xFF;
        int green = (abgr >> 8) & 0xFF;
        int red = abgr & 0xFF;

        blue = Math.min(0xFF, blue * 4/5);
        green = Math.min(0xFF, green * 4/5);
        red = Math.min(0xFF, red * 4/5);

        return alpha << 24 | blue << 16 | green << 8 | red;
    }

}
