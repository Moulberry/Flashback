package com.moulberry.flashback.utils;

import imgui.moulberry90.flag.ImGuiKey;
import imgui.moulberry90.flag.ImGuiMouseButton;
import net.minecraft.client.input.InputQuirks;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLScancode;

import java.nio.ByteBuffer;

public class InputHelper {

    public static final int EDIT_SHORTCUT_KEY_LEFT = InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? SDLScancode.SDL_SCANCODE_LGUI : SDLScancode.SDL_SCANCODE_LCTRL;
    public static final int EDIT_SHORTCUT_KEY_RIGHT = InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? SDLScancode.SDL_SCANCODE_RGUI : SDLScancode.SDL_SCANCODE_RCTRL;

    public static boolean isCtrlOrCmdDownRaw() {
        ByteBuffer keyboardState = SDLKeyboard.SDL_GetKeyboardState();
        return keyboardState != null && (keyboardState.get(EDIT_SHORTCUT_KEY_LEFT) != 0 || keyboardState.get(EDIT_SHORTCUT_KEY_RIGHT) != 0);
    }

    public static boolean isCtrlDownRaw() {
        ByteBuffer keyboardState = SDLKeyboard.SDL_GetKeyboardState();
        return keyboardState != null && (keyboardState.get(SDLScancode.SDL_SCANCODE_LCTRL) != 0 || keyboardState.get(SDLScancode.SDL_SCANCODE_RCTRL) != 0);
    }

    public static boolean isShiftDownRaw() {
        ByteBuffer keyboardState = SDLKeyboard.SDL_GetKeyboardState();
        return keyboardState != null && (keyboardState.get(SDLScancode.SDL_SCANCODE_LSHIFT) != 0 || keyboardState.get(SDLScancode.SDL_SCANCODE_RSHIFT) != 0);
    }

    public static boolean isAltDownRaw() {
        ByteBuffer keyboardState = SDLKeyboard.SDL_GetKeyboardState();
        return keyboardState != null && (keyboardState.get(SDLScancode.SDL_SCANCODE_LALT) != 0 || keyboardState.get(SDLScancode.SDL_SCANCODE_RALT) != 0);
    }

    public static boolean isSuperDownRaw() {
        ByteBuffer keyboardState = SDLKeyboard.SDL_GetKeyboardState();
        return keyboardState != null && (keyboardState.get(SDLScancode.SDL_SCANCODE_LGUI) != 0 || keyboardState.get(SDLScancode.SDL_SCANCODE_RGUI) != 0);
    }

    public static boolean isKeyDownRaw(int imguiKey) {
        int sdlScancode = imguiKeyToSdlScancode(imguiKey);
        if (sdlScancode == 0) return false;
        ByteBuffer keyboardState = SDLKeyboard.SDL_GetKeyboardState();
        return keyboardState != null && keyboardState.get(sdlScancode) != 0;
    }

    public static boolean isMouseDownRaw(int imguiButton) {
        int button = imguiMouseToSdlMouse(imguiButton);
        int state = SDLMouse.SDL_GetMouseState(null, null);
        return (state & (1 << (button-1))) != 0;
    }

    public static int imguiMouseToSdlMouse(int imgui) {
        return switch (imgui) {
            case ImGuiMouseButton.Left -> SDLMouse.SDL_BUTTON_LEFT;
            case ImGuiMouseButton.Right -> SDLMouse.SDL_BUTTON_RIGHT;
            case ImGuiMouseButton.Middle -> SDLMouse.SDL_BUTTON_MIDDLE;
            case 3 -> SDLMouse.SDL_BUTTON_X1;
            case 4 -> SDLMouse.SDL_BUTTON_X2;
            default -> -1;
        };
    }
    public static int sdlMouseToImguiMouse(int sdl) {
        return switch (sdl) {
            case SDLMouse.SDL_BUTTON_LEFT -> ImGuiMouseButton.Left;
            case SDLMouse.SDL_BUTTON_RIGHT -> ImGuiMouseButton.Right;
            case SDLMouse.SDL_BUTTON_MIDDLE -> ImGuiMouseButton.Middle;
            case SDLMouse.SDL_BUTTON_X1 -> 3;
            case SDLMouse.SDL_BUTTON_X2 -> 4;
            default -> -1;
        };
    }
    public static int imguiKeyToSdlScancode(int imgui) {
        return switch (imgui) {
            case ImGuiKey.Tab -> SDLScancode.SDL_SCANCODE_TAB;
            case ImGuiKey.LeftArrow -> SDLScancode.SDL_SCANCODE_LEFT;
            case ImGuiKey.RightArrow -> SDLScancode.SDL_SCANCODE_RIGHT;
            case ImGuiKey.UpArrow -> SDLScancode.SDL_SCANCODE_UP;
            case ImGuiKey.DownArrow -> SDLScancode.SDL_SCANCODE_DOWN;
            case ImGuiKey.PageUp -> SDLScancode.SDL_SCANCODE_PAGEUP;
            case ImGuiKey.PageDown -> SDLScancode.SDL_SCANCODE_PAGEDOWN;
            case ImGuiKey.Home -> SDLScancode.SDL_SCANCODE_HOME;
            case ImGuiKey.End -> SDLScancode.SDL_SCANCODE_END;
            case ImGuiKey.Insert -> SDLScancode.SDL_SCANCODE_INSERT;
            case ImGuiKey.Delete -> SDLScancode.SDL_SCANCODE_DELETE;
            case ImGuiKey.Backspace -> SDLScancode.SDL_SCANCODE_BACKSPACE;
            case ImGuiKey.Space -> SDLScancode.SDL_SCANCODE_SPACE;
            case ImGuiKey.Enter -> SDLScancode.SDL_SCANCODE_RETURN;
            case ImGuiKey.Escape -> SDLScancode.SDL_SCANCODE_ESCAPE;
            case ImGuiKey.LeftCtrl -> SDLScancode.SDL_SCANCODE_LCTRL;
            case ImGuiKey.LeftShift -> SDLScancode.SDL_SCANCODE_LSHIFT;
            case ImGuiKey.LeftAlt -> SDLScancode.SDL_SCANCODE_LALT;
            case ImGuiKey.LeftSuper -> SDLScancode.SDL_SCANCODE_LGUI;
            case ImGuiKey.RightCtrl -> SDLScancode.SDL_SCANCODE_RCTRL;
            case ImGuiKey.RightShift -> SDLScancode.SDL_SCANCODE_RSHIFT;
            case ImGuiKey.RightAlt -> SDLScancode.SDL_SCANCODE_RALT;
            case ImGuiKey.RightSuper -> SDLScancode.SDL_SCANCODE_RGUI;
            case ImGuiKey.Menu -> SDLScancode.SDL_SCANCODE_APPLICATION;
            case ImGuiKey._0 -> SDLScancode.SDL_SCANCODE_0;
            case ImGuiKey._1 -> SDLScancode.SDL_SCANCODE_1;
            case ImGuiKey._2 -> SDLScancode.SDL_SCANCODE_2;
            case ImGuiKey._3 -> SDLScancode.SDL_SCANCODE_3;
            case ImGuiKey._4 -> SDLScancode.SDL_SCANCODE_4;
            case ImGuiKey._5 -> SDLScancode.SDL_SCANCODE_5;
            case ImGuiKey._6 -> SDLScancode.SDL_SCANCODE_6;
            case ImGuiKey._7 -> SDLScancode.SDL_SCANCODE_7;
            case ImGuiKey._8 -> SDLScancode.SDL_SCANCODE_8;
            case ImGuiKey._9 -> SDLScancode.SDL_SCANCODE_9;
            case ImGuiKey.A -> SDLScancode.SDL_SCANCODE_A;
            case ImGuiKey.B -> SDLScancode.SDL_SCANCODE_B;
            case ImGuiKey.C -> SDLScancode.SDL_SCANCODE_C;
            case ImGuiKey.D -> SDLScancode.SDL_SCANCODE_D;
            case ImGuiKey.E -> SDLScancode.SDL_SCANCODE_E;
            case ImGuiKey.F -> SDLScancode.SDL_SCANCODE_F;
            case ImGuiKey.G -> SDLScancode.SDL_SCANCODE_G;
            case ImGuiKey.H -> SDLScancode.SDL_SCANCODE_H;
            case ImGuiKey.I -> SDLScancode.SDL_SCANCODE_I;
            case ImGuiKey.J -> SDLScancode.SDL_SCANCODE_J;
            case ImGuiKey.K -> SDLScancode.SDL_SCANCODE_K;
            case ImGuiKey.L -> SDLScancode.SDL_SCANCODE_L;
            case ImGuiKey.M -> SDLScancode.SDL_SCANCODE_M;
            case ImGuiKey.N -> SDLScancode.SDL_SCANCODE_N;
            case ImGuiKey.O -> SDLScancode.SDL_SCANCODE_O;
            case ImGuiKey.P -> SDLScancode.SDL_SCANCODE_P;
            case ImGuiKey.Q -> SDLScancode.SDL_SCANCODE_Q;
            case ImGuiKey.R -> SDLScancode.SDL_SCANCODE_R;
            case ImGuiKey.S -> SDLScancode.SDL_SCANCODE_S;
            case ImGuiKey.T -> SDLScancode.SDL_SCANCODE_T;
            case ImGuiKey.U -> SDLScancode.SDL_SCANCODE_U;
            case ImGuiKey.V -> SDLScancode.SDL_SCANCODE_V;
            case ImGuiKey.W -> SDLScancode.SDL_SCANCODE_W;
            case ImGuiKey.X -> SDLScancode.SDL_SCANCODE_X;
            case ImGuiKey.Y -> SDLScancode.SDL_SCANCODE_Y;
            case ImGuiKey.Z -> SDLScancode.SDL_SCANCODE_Z;
            case ImGuiKey.F1 -> SDLScancode.SDL_SCANCODE_F1;
            case ImGuiKey.F2 -> SDLScancode.SDL_SCANCODE_F2;
            case ImGuiKey.F3 -> SDLScancode.SDL_SCANCODE_F3;
            case ImGuiKey.F4 -> SDLScancode.SDL_SCANCODE_F4;
            case ImGuiKey.F5 -> SDLScancode.SDL_SCANCODE_F5;
            case ImGuiKey.F6 -> SDLScancode.SDL_SCANCODE_F6;
            case ImGuiKey.F7 -> SDLScancode.SDL_SCANCODE_F7;
            case ImGuiKey.F8 -> SDLScancode.SDL_SCANCODE_F8;
            case ImGuiKey.F9 -> SDLScancode.SDL_SCANCODE_F9;
            case ImGuiKey.F10 -> SDLScancode.SDL_SCANCODE_F10;
            case ImGuiKey.F11 -> SDLScancode.SDL_SCANCODE_F11;
            case ImGuiKey.F12 -> SDLScancode.SDL_SCANCODE_F12;
            case ImGuiKey.F13 -> SDLScancode.SDL_SCANCODE_F13;
            case ImGuiKey.F14 -> SDLScancode.SDL_SCANCODE_F14;
            case ImGuiKey.F15 -> SDLScancode.SDL_SCANCODE_F15;
            case ImGuiKey.F16 -> SDLScancode.SDL_SCANCODE_F16;
            case ImGuiKey.F17 -> SDLScancode.SDL_SCANCODE_F17;
            case ImGuiKey.F18 -> SDLScancode.SDL_SCANCODE_F18;
            case ImGuiKey.F19 -> SDLScancode.SDL_SCANCODE_F19;
            case ImGuiKey.F20 -> SDLScancode.SDL_SCANCODE_F20;
            case ImGuiKey.F21 -> SDLScancode.SDL_SCANCODE_F21;
            case ImGuiKey.F22 -> SDLScancode.SDL_SCANCODE_F22;
            case ImGuiKey.F23 -> SDLScancode.SDL_SCANCODE_F23;
            case ImGuiKey.F24 -> SDLScancode.SDL_SCANCODE_F24;
            case ImGuiKey.Apostrophe -> SDLScancode.SDL_SCANCODE_APOSTROPHE;
            case ImGuiKey.Comma -> SDLScancode.SDL_SCANCODE_COMMA;
            case ImGuiKey.Minus -> SDLScancode.SDL_SCANCODE_MINUS;
            case ImGuiKey.Period -> SDLScancode.SDL_SCANCODE_PERIOD;
            case ImGuiKey.Slash -> SDLScancode.SDL_SCANCODE_SLASH;
            case ImGuiKey.Semicolon -> SDLScancode.SDL_SCANCODE_SEMICOLON;
            case ImGuiKey.Equal -> SDLScancode.SDL_SCANCODE_EQUALS;
            case ImGuiKey.LeftBracket -> SDLScancode.SDL_SCANCODE_LEFTBRACKET;
            case ImGuiKey.Backslash -> SDLScancode.SDL_SCANCODE_BACKSLASH;
            case ImGuiKey.RightBracket -> SDLScancode.SDL_SCANCODE_RIGHTBRACKET;
            case ImGuiKey.GraveAccent -> SDLScancode.SDL_SCANCODE_GRAVE;
            case ImGuiKey.CapsLock -> SDLScancode.SDL_SCANCODE_CAPSLOCK;
            case ImGuiKey.ScrollLock -> SDLScancode.SDL_SCANCODE_SCROLLLOCK;
            case ImGuiKey.NumLock -> SDLScancode.SDL_SCANCODE_NUMLOCKCLEAR;
            case ImGuiKey.PrintScreen -> SDLScancode.SDL_SCANCODE_PRINTSCREEN;
            case ImGuiKey.Pause -> SDLScancode.SDL_SCANCODE_PAUSE;
            case ImGuiKey.Keypad0 -> SDLScancode.SDL_SCANCODE_KP_0;
            case ImGuiKey.Keypad1 -> SDLScancode.SDL_SCANCODE_KP_1;
            case ImGuiKey.Keypad2 -> SDLScancode.SDL_SCANCODE_KP_2;
            case ImGuiKey.Keypad3 -> SDLScancode.SDL_SCANCODE_KP_3;
            case ImGuiKey.Keypad4 -> SDLScancode.SDL_SCANCODE_KP_4;
            case ImGuiKey.Keypad5 -> SDLScancode.SDL_SCANCODE_KP_5;
            case ImGuiKey.Keypad6 -> SDLScancode.SDL_SCANCODE_KP_6;
            case ImGuiKey.Keypad7 -> SDLScancode.SDL_SCANCODE_KP_7;
            case ImGuiKey.Keypad8 -> SDLScancode.SDL_SCANCODE_KP_8;
            case ImGuiKey.Keypad9 -> SDLScancode.SDL_SCANCODE_KP_9;
            case ImGuiKey.KeypadDecimal -> SDLScancode.SDL_SCANCODE_KP_DECIMAL;
            case ImGuiKey.KeypadDivide -> SDLScancode.SDL_SCANCODE_KP_DIVIDE;
            case ImGuiKey.KeypadMultiply -> SDLScancode.SDL_SCANCODE_KP_MULTIPLY;
            case ImGuiKey.KeypadSubtract -> SDLScancode.SDL_SCANCODE_KP_MINUS;
            case ImGuiKey.KeypadAdd -> SDLScancode.SDL_SCANCODE_KP_PLUS;
            case ImGuiKey.KeypadEnter -> SDLScancode.SDL_SCANCODE_KP_ENTER;
            case ImGuiKey.KeypadEqual -> SDLScancode.SDL_SCANCODE_KP_EQUALS;
            case ImGuiKey.AppBack -> SDLScancode.SDL_SCANCODE_AC_BACK;
            case ImGuiKey.AppForward -> SDLScancode.SDL_SCANCODE_AC_FORWARD;
            default -> 0;
        };
    }
    public static int sdlScancodeToImguiKey(int sdlScancode) {
        return switch (sdlScancode) {
            case SDLScancode.SDL_SCANCODE_TAB -> ImGuiKey.Tab;
            case SDLScancode.SDL_SCANCODE_LEFT -> ImGuiKey.LeftArrow;
            case SDLScancode.SDL_SCANCODE_RIGHT -> ImGuiKey.RightArrow;
            case SDLScancode.SDL_SCANCODE_UP -> ImGuiKey.UpArrow;
            case SDLScancode.SDL_SCANCODE_DOWN -> ImGuiKey.DownArrow;
            case SDLScancode.SDL_SCANCODE_PAGEUP -> ImGuiKey.PageUp;
            case SDLScancode.SDL_SCANCODE_PAGEDOWN -> ImGuiKey.PageDown;
            case SDLScancode.SDL_SCANCODE_HOME -> ImGuiKey.Home;
            case SDLScancode.SDL_SCANCODE_END -> ImGuiKey.End;
            case SDLScancode.SDL_SCANCODE_INSERT -> ImGuiKey.Insert;
            case SDLScancode.SDL_SCANCODE_DELETE -> ImGuiKey.Delete;
            case SDLScancode.SDL_SCANCODE_BACKSPACE -> ImGuiKey.Backspace;
            case SDLScancode.SDL_SCANCODE_SPACE -> ImGuiKey.Space;
            case SDLScancode.SDL_SCANCODE_RETURN -> ImGuiKey.Enter;
            case SDLScancode.SDL_SCANCODE_ESCAPE -> ImGuiKey.Escape;
            case SDLScancode.SDL_SCANCODE_LCTRL -> ImGuiKey.LeftCtrl;
            case SDLScancode.SDL_SCANCODE_LSHIFT -> ImGuiKey.LeftShift;
            case SDLScancode.SDL_SCANCODE_LALT -> ImGuiKey.LeftAlt;
            case SDLScancode.SDL_SCANCODE_LGUI -> ImGuiKey.LeftSuper;
            case SDLScancode.SDL_SCANCODE_RCTRL -> ImGuiKey.RightCtrl;
            case SDLScancode.SDL_SCANCODE_RSHIFT -> ImGuiKey.RightShift;
            case SDLScancode.SDL_SCANCODE_RALT -> ImGuiKey.RightAlt;
            case SDLScancode.SDL_SCANCODE_RGUI -> ImGuiKey.RightSuper;
            case SDLScancode.SDL_SCANCODE_APPLICATION -> ImGuiKey.Menu;
            case SDLScancode.SDL_SCANCODE_0 -> ImGuiKey._0;
            case SDLScancode.SDL_SCANCODE_1 -> ImGuiKey._1;
            case SDLScancode.SDL_SCANCODE_2 -> ImGuiKey._2;
            case SDLScancode.SDL_SCANCODE_3 -> ImGuiKey._3;
            case SDLScancode.SDL_SCANCODE_4 -> ImGuiKey._4;
            case SDLScancode.SDL_SCANCODE_5 -> ImGuiKey._5;
            case SDLScancode.SDL_SCANCODE_6 -> ImGuiKey._6;
            case SDLScancode.SDL_SCANCODE_7 -> ImGuiKey._7;
            case SDLScancode.SDL_SCANCODE_8 -> ImGuiKey._8;
            case SDLScancode.SDL_SCANCODE_9 -> ImGuiKey._9;
            case SDLScancode.SDL_SCANCODE_A -> ImGuiKey.A;
            case SDLScancode.SDL_SCANCODE_B -> ImGuiKey.B;
            case SDLScancode.SDL_SCANCODE_C -> ImGuiKey.C;
            case SDLScancode.SDL_SCANCODE_D -> ImGuiKey.D;
            case SDLScancode.SDL_SCANCODE_E -> ImGuiKey.E;
            case SDLScancode.SDL_SCANCODE_F -> ImGuiKey.F;
            case SDLScancode.SDL_SCANCODE_G -> ImGuiKey.G;
            case SDLScancode.SDL_SCANCODE_H -> ImGuiKey.H;
            case SDLScancode.SDL_SCANCODE_I -> ImGuiKey.I;
            case SDLScancode.SDL_SCANCODE_J -> ImGuiKey.J;
            case SDLScancode.SDL_SCANCODE_K -> ImGuiKey.K;
            case SDLScancode.SDL_SCANCODE_L -> ImGuiKey.L;
            case SDLScancode.SDL_SCANCODE_M -> ImGuiKey.M;
            case SDLScancode.SDL_SCANCODE_N -> ImGuiKey.N;
            case SDLScancode.SDL_SCANCODE_O -> ImGuiKey.O;
            case SDLScancode.SDL_SCANCODE_P -> ImGuiKey.P;
            case SDLScancode.SDL_SCANCODE_Q -> ImGuiKey.Q;
            case SDLScancode.SDL_SCANCODE_R -> ImGuiKey.R;
            case SDLScancode.SDL_SCANCODE_S -> ImGuiKey.S;
            case SDLScancode.SDL_SCANCODE_T -> ImGuiKey.T;
            case SDLScancode.SDL_SCANCODE_U -> ImGuiKey.U;
            case SDLScancode.SDL_SCANCODE_V -> ImGuiKey.V;
            case SDLScancode.SDL_SCANCODE_W -> ImGuiKey.W;
            case SDLScancode.SDL_SCANCODE_X -> ImGuiKey.X;
            case SDLScancode.SDL_SCANCODE_Y -> ImGuiKey.Y;
            case SDLScancode.SDL_SCANCODE_Z -> ImGuiKey.Z;
            case SDLScancode.SDL_SCANCODE_F1 -> ImGuiKey.F1;
            case SDLScancode.SDL_SCANCODE_F2 -> ImGuiKey.F2;
            case SDLScancode.SDL_SCANCODE_F3 -> ImGuiKey.F3;
            case SDLScancode.SDL_SCANCODE_F4 -> ImGuiKey.F4;
            case SDLScancode.SDL_SCANCODE_F5 -> ImGuiKey.F5;
            case SDLScancode.SDL_SCANCODE_F6 -> ImGuiKey.F6;
            case SDLScancode.SDL_SCANCODE_F7 -> ImGuiKey.F7;
            case SDLScancode.SDL_SCANCODE_F8 -> ImGuiKey.F8;
            case SDLScancode.SDL_SCANCODE_F9 -> ImGuiKey.F9;
            case SDLScancode.SDL_SCANCODE_F10 -> ImGuiKey.F10;
            case SDLScancode.SDL_SCANCODE_F11 -> ImGuiKey.F11;
            case SDLScancode.SDL_SCANCODE_F12 -> ImGuiKey.F12;
            case SDLScancode.SDL_SCANCODE_F13 -> ImGuiKey.F13;
            case SDLScancode.SDL_SCANCODE_F14 -> ImGuiKey.F14;
            case SDLScancode.SDL_SCANCODE_F15 -> ImGuiKey.F15;
            case SDLScancode.SDL_SCANCODE_F16 -> ImGuiKey.F16;
            case SDLScancode.SDL_SCANCODE_F17 -> ImGuiKey.F17;
            case SDLScancode.SDL_SCANCODE_F18 -> ImGuiKey.F18;
            case SDLScancode.SDL_SCANCODE_F19 -> ImGuiKey.F19;
            case SDLScancode.SDL_SCANCODE_F20 -> ImGuiKey.F20;
            case SDLScancode.SDL_SCANCODE_F21 -> ImGuiKey.F21;
            case SDLScancode.SDL_SCANCODE_F22 -> ImGuiKey.F22;
            case SDLScancode.SDL_SCANCODE_F23 -> ImGuiKey.F23;
            case SDLScancode.SDL_SCANCODE_F24 -> ImGuiKey.F24;
            case SDLScancode.SDL_SCANCODE_APOSTROPHE -> ImGuiKey.Apostrophe;
            case SDLScancode.SDL_SCANCODE_COMMA -> ImGuiKey.Comma;
            case SDLScancode.SDL_SCANCODE_MINUS -> ImGuiKey.Minus;
            case SDLScancode.SDL_SCANCODE_PERIOD -> ImGuiKey.Period;
            case SDLScancode.SDL_SCANCODE_SLASH -> ImGuiKey.Slash;
            case SDLScancode.SDL_SCANCODE_SEMICOLON -> ImGuiKey.Semicolon;
            case SDLScancode.SDL_SCANCODE_EQUALS -> ImGuiKey.Equal;
            case SDLScancode.SDL_SCANCODE_LEFTBRACKET -> ImGuiKey.LeftBracket;
            case SDLScancode.SDL_SCANCODE_BACKSLASH -> ImGuiKey.Backslash;
            case SDLScancode.SDL_SCANCODE_RIGHTBRACKET -> ImGuiKey.RightBracket;
            case SDLScancode.SDL_SCANCODE_GRAVE -> ImGuiKey.GraveAccent;
            case SDLScancode.SDL_SCANCODE_CAPSLOCK -> ImGuiKey.CapsLock;
            case SDLScancode.SDL_SCANCODE_SCROLLLOCK -> ImGuiKey.ScrollLock;
            case SDLScancode.SDL_SCANCODE_NUMLOCKCLEAR -> ImGuiKey.NumLock;
            case SDLScancode.SDL_SCANCODE_PRINTSCREEN -> ImGuiKey.PrintScreen;
            case SDLScancode.SDL_SCANCODE_PAUSE -> ImGuiKey.Pause;
            case SDLScancode.SDL_SCANCODE_KP_0 -> ImGuiKey.Keypad0;
            case SDLScancode.SDL_SCANCODE_KP_1 -> ImGuiKey.Keypad1;
            case SDLScancode.SDL_SCANCODE_KP_2 -> ImGuiKey.Keypad2;
            case SDLScancode.SDL_SCANCODE_KP_3 -> ImGuiKey.Keypad3;
            case SDLScancode.SDL_SCANCODE_KP_4 -> ImGuiKey.Keypad4;
            case SDLScancode.SDL_SCANCODE_KP_5 -> ImGuiKey.Keypad5;
            case SDLScancode.SDL_SCANCODE_KP_6 -> ImGuiKey.Keypad6;
            case SDLScancode.SDL_SCANCODE_KP_7 -> ImGuiKey.Keypad7;
            case SDLScancode.SDL_SCANCODE_KP_8 -> ImGuiKey.Keypad8;
            case SDLScancode.SDL_SCANCODE_KP_9 -> ImGuiKey.Keypad9;
            case SDLScancode.SDL_SCANCODE_KP_DECIMAL -> ImGuiKey.KeypadDecimal;
            case SDLScancode.SDL_SCANCODE_KP_DIVIDE -> ImGuiKey.KeypadDivide;
            case SDLScancode.SDL_SCANCODE_KP_MULTIPLY -> ImGuiKey.KeypadMultiply;
            case SDLScancode.SDL_SCANCODE_KP_MINUS -> ImGuiKey.KeypadSubtract;
            case SDLScancode.SDL_SCANCODE_KP_PLUS -> ImGuiKey.KeypadAdd;
            case SDLScancode.SDL_SCANCODE_KP_ENTER -> ImGuiKey.KeypadEnter;
            case SDLScancode.SDL_SCANCODE_KP_EQUALS -> ImGuiKey.KeypadEqual;
            case SDLScancode.SDL_SCANCODE_AC_BACK -> ImGuiKey.AppBack;
            case SDLScancode.SDL_SCANCODE_AC_FORWARD -> ImGuiKey.AppForward;
            default -> ImGuiKey.None;
        };
    }

}
