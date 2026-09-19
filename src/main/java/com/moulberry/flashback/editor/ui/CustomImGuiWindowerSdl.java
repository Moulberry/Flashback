package com.moulberry.flashback.editor.ui;

import com.moulberry.flashback.editor.keybinds.Keybind;
import com.moulberry.flashback.editor.keybinds.Keybinds;
import com.moulberry.flashback.utils.AsyncFileDialogs;
import com.moulberry.flashback.utils.InputHelper;
import com.moulberry.flashback.utils.WindowSizeTracker;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImGuiIO;
import imgui.moulberry90.callback.ImStrConsumer;
import imgui.moulberry90.callback.ImStrSupplier;
import imgui.moulberry90.flag.ImGuiBackendFlags;
import imgui.moulberry90.flag.ImGuiConfigFlags;
import imgui.moulberry90.flag.ImGuiKey;
import imgui.moulberry90.flag.ImGuiMouseButton;
import imgui.moulberry90.flag.ImGuiMouseCursor;
import imgui.moulberry90.flag.ImGuiMouseSource;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.phys.Vec2;
import org.lwjgl.sdl.SDLClipboard;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLTimer;
import org.lwjgl.sdl.SDLTouch;
import org.lwjgl.sdl.SDLVersion;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_KeyboardEvent;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class CustomImGuiWindowerSdl implements CustomImGuiWindower {

    private long windowId;
    private boolean isWayland;

    private long time = 0;
    private int mouseButtonsDownImgui;
    private int mouseButtonsDownGame;

    private final int[] winWidth = new int[1];
    private final int[] winHeight = new int[1];
    private final int[] fbWidth = new int[1];
    private final int[] fbHeight = new int[1];
    private final float[] windowScale = new float[1];

    private long lastMouseCursor = -1;
    private final long[] mouseCursors = new long[ImGuiMouseCursor.COUNT];
    private boolean mouseCanUseGlobalState = false;
    private CaptureMouseMode captureMouseMode = CaptureMouseMode.Disabled;

    private MouseHandledBy grabbed = null;
    private int ignoreMouseMovements = 0;
    private boolean releasedAllKeysBecauseOfDialog = false;
    private boolean releasedAllKeysBecauseOfDisable = false;
    private float grabbedOriginalMouseX = 0;
    private float grabbedOriginalMouseY = 0;
    private int grabLinkedKey = 0;

    private float rawMouseX = 0;
    private float rawMouseY = 0;

    private double grabbedLastMouseX = 0;
    private double grabbedLastMouseY = 0;
    private double grabbedCurrMouseX = 0;
    private double grabbedCurrMouseY = 0;

    private IntSet imguiPressedScancodes = new IntOpenHashSet();
    private IntSet gamePressedScancodes = new IntOpenHashSet();

    private float contentScale = 1.0f;

    private enum CaptureMouseMode {
        Disabled, // Wayland + RPI
        EnabledAfterDrag, // X11
        Enabled
    }

    @Override
    public void init(long windowId) {
        final ImGuiIO io = ReplayUI.getIO();

        int verLinked = SDLVersion.SDL_GetVersion();
        String sdlVideoDriver = SDLVideo.SDL_GetCurrentVideoDriver();

        if (io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            throw new UnsupportedOperationException();
        }

        io.addBackendFlags(ImGuiBackendFlags.HasMouseCursors);
        io.setBackendPlatformName("Axiom SDL");

        this.windowId = windowId;
        this.isWayland = sdlVideoDriver != null && sdlVideoDriver.equalsIgnoreCase("wayland");

        if (sdlVideoDriver != null && (sdlVideoDriver.equalsIgnoreCase("wayland") || sdlVideoDriver.equalsIgnoreCase("rpi"))) {
            this.mouseCanUseGlobalState = false;
            this.captureMouseMode = CaptureMouseMode.Disabled;
        } else if (sdlVideoDriver != null && sdlVideoDriver.equalsIgnoreCase("x11")) {
            this.mouseCanUseGlobalState = true;
            this.captureMouseMode = CaptureMouseMode.EnabledAfterDrag;
        } else {
            this.mouseCanUseGlobalState = true;
            this.captureMouseMode = CaptureMouseMode.Enabled;
        }

        io.setGetClipboardTextFn(new ImStrSupplier() {
            @Override
            public String get() {
                final String clipboardString = SDLClipboard.SDL_GetClipboardText();
                return clipboardString != null ? clipboardString : "";
            }
        });

        io.setSetClipboardTextFn(new ImStrConsumer() {
            @Override
            public void accept(final String str) {
                SDLClipboard.SDL_SetClipboardText(str);
            }
        });

        this.mouseCursors[ImGuiMouseCursor.Arrow] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT);
        this.mouseCursors[ImGuiMouseCursor.TextInput] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_TEXT);
        this.mouseCursors[ImGuiMouseCursor.ResizeAll] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_MOVE);
        this.mouseCursors[ImGuiMouseCursor.ResizeNS] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_NS_RESIZE);
        this.mouseCursors[ImGuiMouseCursor.ResizeEW] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_EW_RESIZE);
        this.mouseCursors[ImGuiMouseCursor.ResizeNESW] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_NESW_RESIZE);
        this.mouseCursors[ImGuiMouseCursor.ResizeNWSE] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_NWSE_RESIZE);
        this.mouseCursors[ImGuiMouseCursor.Hand] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_POINTER);
        this.mouseCursors[ImGuiMouseCursor.NotAllowed] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_NOT_ALLOWED);

        // Calculate content scale
        this.updateContentScale(io);
    }

    private void updateContentScale(ImGuiIO io) {
        WindowSizeTracker.getWindowSizeRaw(this.windowId, this.winWidth, this.winHeight);
        WindowSizeTracker.getFramebufferSizeRaw(this.windowId, this.fbWidth, this.fbHeight);
        io.setDisplaySize((float) this.winWidth[0], (float) this.winHeight[0]);
        if (this.winWidth[0] > 0 && this.winHeight[0] > 0) {
            final float scaleX = (float) this.fbWidth[0] / this.winWidth[0];
            final float scaleY = (float) this.fbHeight[0] / this.winHeight[0];
            io.setDisplayFramebufferScale(scaleX, scaleY);

            float scale = SDLVideo.SDL_GetWindowDisplayScale(this.windowId);
            this.contentScale = Math.max(scale / scaleX, scale / scaleY);
        }
    }

    private static final IntSet SDL_INPUT_EVENTS = IntSet.of(SDLEvents.SDL_EVENT_MOUSE_MOTION, SDLEvents.SDL_EVENT_MOUSE_WHEEL,
        SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN, SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP, SDLEvents.SDL_EVENT_TEXT_INPUT,
        SDLEvents.SDL_EVENT_KEY_DOWN, SDLEvents.SDL_EVENT_KEY_UP);

    public boolean processEvent(SDL_Event event) {
        long window = SDLEvents.SDL_GetWindowFromEvent(event);
        if (this.windowId != window) {
            return false;
        }

        // Block input when file dialog is open
        if (AsyncFileDialogs.hasDialog() && SDL_INPUT_EVENTS.contains(event.type())) {
            if (event.type() == SDLEvents.SDL_EVENT_KEY_UP && this.gamePressedScancodes.remove(event.key().scancode())) {
                // Allow game to receive release event
                return false;
            } else if (event.type() == SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP) {
                int rawButton = event.button().button();
                int imguiButton = InputHelper.sdlMouseToImguiMouse(rawButton);

                if ((this.mouseButtonsDownGame & (1 << imguiButton)) != 0){
                    // Allow game to receive the release event
                    this.mouseButtonsDownGame &= ~(1 << imguiButton);
                    return false;
                }
            }

            return true;
        }

        ImGuiIO io = ReplayUI.getIO();
        switch (event.type()) {
            case SDLEvents.SDL_EVENT_MOUSE_MOTION -> {
                if (!ReplayUI.isActive()) return false;

                MouseHandledBy handledBy = this.getMouseHandledBy();

                if (this.ignoreMouseMovements > 0) {
                    this.grabbedCurrMouseX = event.motion().x();
                    this.grabbedCurrMouseY = event.motion().y();
                    this.grabbedLastMouseX = event.motion().x();
                    this.grabbedLastMouseY = event.motion().y();
                    this.ignoreMouseMovements -= 1;
                    return true;
                }

                if (handledBy == MouseHandledBy.EDITOR_GRABBED) {
                    this.grabbedCurrMouseX = event.motion().x();
                    this.grabbedCurrMouseY = event.motion().y();
                }
                this.rawMouseX = event.motion().x();
                this.rawMouseY = event.motion().y();

                if (handledBy.allowImgui()) {
                    io.addMouseSourceEvent(event.motion().which() == SDLTouch.SDL_TOUCH_MOUSEID ? ImGuiMouseSource.TouchScreen : ImGuiMouseSource.Mouse);
                    io.addMousePosEvent(event.motion().x(), event.motion().y());
                }

                event.motion().x((float) ReplayUI.getNewMouseX(event.motion().x()));
                event.motion().y((float) ReplayUI.getNewMouseY(event.motion().y()));

                return !handledBy.allowGame();
            }
            case SDLEvents.SDL_EVENT_MOUSE_WHEEL -> {
                float xOffset = -event.wheel().x();
                float yOffset = event.wheel().y();

                if (ReplayUI.isActive()) {
                    io.setMouseWheelH(io.getMouseWheelH() + xOffset);
                    io.setMouseWheel(io.getMouseWheel() + yOffset);

                    return Minecraft.getInstance().gui.screen() == null || !ReplayUI.isMainFrameHovered();
                } else {
                    return false;
                }
            }
            case SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN, SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP -> {
                int rawButton = event.button().button();
                int imguiButton = InputHelper.sdlMouseToImguiMouse(rawButton);
                boolean pressed = event.type() == SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN;

                if (!ReplayUI.isActive()) {
                    if (pressed) {
                        this.mouseButtonsDownGame |= 1 << imguiButton;
                    } else {
                        this.mouseButtonsDownGame &= ~(1 << imguiButton);
                    }
                    return false;
                }

                if (this.grabbed != null && !pressed && this.grabLinkedKey < 0 && imguiButton == -this.grabLinkedKey-1) {
                    this.ungrab();
                }

                MouseHandledBy handledBy = this.getMouseHandledBy();

                if (handledBy.allowImgui() && imguiButton >= 0) {
                    io.addMouseSourceEvent(event.button().which() == SDLTouch.SDL_TOUCH_MOUSEID ? ImGuiMouseSource.TouchScreen : ImGuiMouseSource.Mouse);
                    io.addMouseButtonEvent(imguiButton, pressed);
                    if (pressed) {
                        this.mouseButtonsDownImgui |= 1 << imguiButton;
                    } else {
                        this.mouseButtonsDownImgui &= ~(1 << imguiButton);
                    }
                }

                if (handledBy.allowGame()) {
                    if (pressed) {
                        this.mouseButtonsDownGame |= 1 << imguiButton;
                    } else {
                        this.mouseButtonsDownGame &= ~(1 << imguiButton);
                    }

                    return false;
                } else if ((this.mouseButtonsDownGame & (1 << imguiButton)) != 0){
                    // Allow game to receive the release event
                    this.mouseButtonsDownGame &= ~(1 << imguiButton);
                    return false;
                } else {
                    return true;
                }
            }
            case SDLEvents.SDL_EVENT_TEXT_INPUT -> {
                if (!ReplayUI.isActive()) {
                    return false;
                }

                if (ImGuiHelper.getEditingKeybind() != null) {
                    return true;
                }

                if (io.getWantTextInput()) {
                    String textString = event.text().textString();
                    io.addInputCharactersUTF8(textString);

                    return true;
                } else {
                    return false;
                }
            }
            case SDLEvents.SDL_EVENT_KEY_DOWN, SDLEvents.SDL_EVENT_KEY_UP -> {
                boolean pressed = event.type() == SDLEvents.SDL_EVENT_KEY_DOWN;
                int rawScancode = event.key().scancode();
                int imguiKey = InputHelper.sdlScancodeToImguiKey(rawScancode);

                if (this.grabbed != null && !pressed && this.grabLinkedKey > 0 && rawScancode == this.grabLinkedKey) {
                    this.ungrab();
                }

                int mods = event.key().mod();
                boolean shiftMod = (mods & SDLKeycode.SDL_KMOD_SHIFT) != 0;
                boolean ctrlMod = (mods & SDLKeycode.SDL_KMOD_CTRL) != 0;
                boolean altMod = (mods & SDLKeycode.SDL_KMOD_ALT) != 0;
                boolean superMod = (mods & SDLKeycode.SDL_KMOD_GUI) != 0;

                if (!ReplayUI.isActive() || Minecraft.getInstance().gui.screen() != null) {
                    if (!pressed && this.imguiPressedScancodes.remove(rawScancode)) {
                        if (imguiKey != ImGuiKey.None) {
                            io.addKeyEvent(imguiKey, false);
                        }
                    }

                    if (pressed) {
                        this.gamePressedScancodes.add(rawScancode);
                    } else {
                        this.gamePressedScancodes.remove(rawScancode);
                    }
                    return false;
                }

                // Update edited keybind
                if (pressed && imguiKey != ImGuiKey.Escape && ImGuiHelper.getEditingKeybind() != null) {
                    shiftMod &= imguiKey != ImGuiKey.LeftShift && imguiKey != ImGuiKey.RightShift;
                    ctrlMod &= imguiKey != ImGuiKey.LeftCtrl && imguiKey != ImGuiKey.RightCtrl;
                    altMod &= imguiKey != ImGuiKey.LeftAlt && imguiKey != ImGuiKey.RightAlt;
                    superMod &= imguiKey != ImGuiKey.LeftSuper && imguiKey != ImGuiKey.RightSuper;

                    if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
                        boolean temp = ctrlMod;
                        ctrlMod = superMod;
                        superMod = temp;
                    }

                    ImGuiHelper.getEditingKeybind().set(imguiKey, shiftMod, ctrlMod, altMod, superMod);
                    return true;
                }

                boolean passToMinecraft = false;
                boolean passToImGui = false;

                if (!pressed) {
                    passToMinecraft = this.gamePressedScancodes.contains(rawScancode);
                    passToImGui = this.imguiPressedScancodes.contains(rawScancode);
                } else {
                    passToMinecraft = shouldPassToMinecraft(io, event.key(), imguiKey, shiftMod, ctrlMod, altMod, superMod);
                    passToImGui = !passToMinecraft;
                }

                // ImGui Handling
                if (passToImGui) {
                    io.addKeyEvent(ImGuiKey.ImGuiMod_Shift, shiftMod);
                    io.addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, ctrlMod);
                    io.addKeyEvent(ImGuiKey.ImGuiMod_Alt, altMod);
                    io.addKeyEvent(ImGuiKey.ImGuiMod_Super, superMod);
                    io.addKeyEvent(imguiKey, pressed);
                    if (pressed) {
                        this.imguiPressedScancodes.add(rawScancode);
                    } else {
                        this.imguiPressedScancodes.remove(rawScancode);
                    }
                }

                // Minecraft handling
                if (passToMinecraft) {
                    if (pressed) {
                        this.gamePressedScancodes.add(rawScancode);
                    } else {
                        this.gamePressedScancodes.remove(rawScancode);
                    }
                    // Pass input to game
                    return false;
                } else if (!pressed && this.gamePressedScancodes.remove(rawScancode)) {
                    // Allow game to receive release event
                    return false;
                } else {
                    // Don't pass input to game
                    return true;
                }
            }
            case SDLEvents.SDL_EVENT_WINDOW_FOCUS_GAINED, SDLEvents.SDL_EVENT_WINDOW_FOCUS_LOST -> {
                io.addFocusEvent(event.type() == SDLEvents.SDL_EVENT_WINDOW_FOCUS_GAINED);
                return false;
            }
        }

        return false;
    }

    private boolean shouldPassToMinecraft(ImGuiIO io, SDL_KeyboardEvent keyboardEvent, int imguiKey, boolean shiftMod, boolean ctrlMod, boolean altMod, boolean superMod) {
        if (imguiKey == ImGuiKey.Escape) {
            return !io.getWantTextInput() && !ReplayUI.hasAnyPopupOpen;
        }

        // If any of our keybinds would be triggered, don't pass to Minecraft
        for (Keybind keybind : Keybinds.KEYBINDS) {
            if (keybind.wouldBePressed(imguiKey, shiftMod, ctrlMod, altMod, superMod)) {
                return false;
            }
        }

        // Pass all function keys to Minecraft
        if (imguiKey >= ImGuiKey.F1 && imguiKey <= ImGuiKey.F24) {
            return true;
        }

        // Pass all F3 combinations to Minecraft
        if (InputHelper.isKeyDownRaw(ImGuiKey.F3)) {
            return true;
        }

        if (io.getWantTextInput()) {
            return false;
        } else if (this.grabbed == MouseHandledBy.GAME) {
            return true;
        }

        var options = Minecraft.getInstance().options;
        var keyEvent = new KeyEvent(keyboardEvent.scancode(), keyboardEvent.key(), keyboardEvent.mod());

        // If any Minecraft keybinds would be triggered while focusing the main frame, pass to minecraft
        if (ReplayUI.isMainFrameActive()) {
            for (KeyMapping keyMapping : options.keyMappings) {
                if (keyMapping.matches(keyEvent)) {
                    return true;
                }
            }
        }

        // Special keybinds that take priority even if the main frame isn't focused
        if (options.keyUp.matches(keyEvent) ||
            options.keyLeft.matches(keyEvent) ||
            options.keyDown.matches(keyEvent) ||
            options.keyRight.matches(keyEvent) ||
            options.keyJump.matches(keyEvent) ||
            options.keyChat.matches(keyEvent) ||
            options.keyCommand.matches(keyEvent)) {
            ReplayUI.focusMainWindowCounter = 5;
            return true;
        }

        return false;
    }

    @Override
    public void newFrame() {
        final ImGuiIO io = ReplayUI.getIO();

        this.updateContentScale(io);

        // Update delta time
        long frequency = SDLTimer.SDL_GetPerformanceFrequency();
        long currentTime = SDLTimer.SDL_GetPerformanceCounter();
        if (currentTime < this.time) {
            currentTime = this.time + 1;
        }
        if (this.time == 0) {
            io.setDeltaTime(1f/60f);
        } else {
            io.setDeltaTime((float)((currentTime - this.time)/(double)frequency));
        }
        this.time = currentTime;

        if (AsyncFileDialogs.hasDialog()) {
            if (!this.releasedAllKeysBecauseOfDialog) {
                this.releasedAllKeysBecauseOfDialog = true;

                // Release for imgui
                this.releaseAllImGuiKeys(io);
            }

            return;
        } else {
            this.releasedAllKeysBecauseOfDialog = false;
        }

        this.updateMouseData(io);
        this.updateMouseCursor(io);
        this.updateIme(io);
    }

    private void updateMouseData(ImGuiIO io) {
        switch (this.captureMouseMode) {
            case Disabled -> {}
            case EnabledAfterDrag -> {
                boolean capture = false;
                float[] dragDistances = io.getMouseDragMaxDistanceSqr();
                for (int i = 0; i < ImGuiMouseButton.COUNT; i++) {
                    if (io.getMouseDown(i) && dragDistances[i] > 1) {
                        capture = true;
                        break;
                    }
                }
                SDLMouse.SDL_CaptureMouse(capture);
            }
            case Enabled -> SDLMouse.SDL_CaptureMouse(this.mouseButtonsDownImgui != 0);
        }
    }

    private void updateMouseCursor(ImGuiIO io) {
        if (io.hasConfigFlags(ImGuiConfigFlags.NoMouseCursorChange)) {
            return;
        }

        int imguiCursor = ImGui.getMouseCursor();
        if (io.getMouseDrawCursor() || imguiCursor == ImGuiMouseCursor.None) {
            // Hide OS mouse cursor if imgui is drawing it or if it wants no cursor
            SDLMouse.SDL_HideCursor();
        } else {
            // Show OS mouse cursor
            long expectedCursor = this.mouseCursors[imguiCursor] != 0 ? this.mouseCursors[imguiCursor] : this.mouseCursors[ImGuiMouseCursor.Arrow];
            if (this.lastMouseCursor != -1 && this.lastMouseCursor != expectedCursor) {
                SDLMouse.SDL_SetCursor(expectedCursor);
                SDLMouse.SDL_ShowCursor();
            }
            this.lastMouseCursor = expectedCursor;
        }
    }

    private void updateIme(ImGuiIO io) {
        if (io.getWantTextInput()) {
            Minecraft.getInstance().textInputManager().startTextInput(this);
        } else {
            Minecraft.getInstance().textInputManager().stopTextInput(this);
        }
    }

    @Override
    public float getContentScale() {
        return this.contentScale;
    }

    public MouseHandledBy getMouseHandledBy() {
        if (!ReplayUI.isActive()) return MouseHandledBy.GAME;
        if (this.grabbed != null) return this.grabbed;
        if (ReplayUI.getIO().getWantCaptureMouse()) return MouseHandledBy.IMGUI;
        return MouseHandledBy.BOTH;
    }

    @Override
    public void setReleaseAllKeys(boolean release) {
        if (release) {
            if (!this.releasedAllKeysBecauseOfDisable) {
                this.releasedAllKeysBecauseOfDisable = true;

                final ImGuiIO io = ReplayUI.getIO();

                this.releaseAllImGuiKeys(io);

                if (ImGui.getMouseCursor() != ImGuiMouseCursor.Arrow) {
                    SDLMouse.SDL_SetCursor(this.mouseCursors[ImGuiMouseCursor.Arrow]);
                    SDLMouse.SDL_ShowCursor();
                }
            }
        } else {
            this.releasedAllKeysBecauseOfDisable = false;
        }
    }

    private void releaseAllImGuiKeys(ImGuiIO io) {
        for (Integer sdlScancode : this.imguiPressedScancodes) {
            int imguiKey = InputHelper.sdlScancodeToImguiKey(sdlScancode);
            if (imguiKey != ImGuiKey.None) {
                io.addKeyEvent(imguiKey, false);
            }
        }
        this.imguiPressedScancodes.clear();

        io.setKeyCtrl(false);
        io.setKeyShift(false);
        io.setKeyAlt(false);
        io.setKeySuper(false);
    }

    public boolean isGrabbed() {
        return this.grabbed != null;
    }

    public void ungrab() {
        if (this.grabbed == null) return;
        this.grabbed = null;
        this.grabLinkedKey = 0;

        SDLMouse.SDL_WarpMouseInWindow(this.windowId, this.grabbedOriginalMouseX, this.grabbedOriginalMouseY);
        SDLMouse.SDL_SetWindowRelativeMouseMode(this.windowId, false);
    }

    public void setGrabbed(boolean passthroughToGame, int grabLinkedKey, double x, double y) {
        if (grabLinkedKey != 0) {
            if (grabLinkedKey < 0) {
                if (!InputHelper.isMouseDownRaw(-grabLinkedKey-1)) {
                    this.ungrab();
                    return;
                }
            } else if (!InputHelper.isKeyDownRaw(grabLinkedKey)) {
                this.ungrab();
                return;
            }
        }
        if (this.grabbed != null) return;
        this.grabbed = passthroughToGame ? MouseHandledBy.GAME : MouseHandledBy.EDITOR_GRABBED;
        this.grabLinkedKey = grabLinkedKey;

        if (x >= 0 && y >= 0) {
            this.grabbedOriginalMouseX = (float) x;
            this.grabbedOriginalMouseY = (float) y;
        } else {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer xBuf = stack.callocFloat(1);
                FloatBuffer yBuf = stack.callocFloat(1);
                SDLMouse.SDL_GetMouseState(xBuf, yBuf);
                this.grabbedOriginalMouseX = xBuf.get();
                this.grabbedOriginalMouseY = yBuf.get();
            }
        }
        SDLMouse.SDL_SetWindowRelativeMouseMode(this.windowId, true);
        this.ignoreMouseMovements = 2;
        Minecraft.getInstance().mouseHandler.setIgnoreFirstMove();
    }

    public double getGrabbedMouseDeltaX() {
        double delta = this.grabbedCurrMouseX - this.grabbedLastMouseX;
        this.grabbedLastMouseX = this.grabbedCurrMouseX;
        return delta;
    }

    public double getGrabbedMouseDeltaY() {
        double delta = this.grabbedCurrMouseY - this.grabbedLastMouseY;
        this.grabbedLastMouseY = this.grabbedCurrMouseY;
        return delta;
    }

    @Override
    public float getRawMouseX() {
        return rawMouseX;
    }

    @Override
    public float getRawMouseY() {
        return rawMouseY;
    }

}
