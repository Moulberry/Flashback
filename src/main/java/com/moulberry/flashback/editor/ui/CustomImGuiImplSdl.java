package com.moulberry.flashback.editor.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.keybinds.Keybind;
import com.moulberry.flashback.editor.keybinds.Keybinds;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImGuiIO;
import imgui.moulberry90.ImGuiPlatformIO;
import imgui.moulberry90.ImVec2;
import imgui.moulberry90.callback.ImStrConsumer;
import imgui.moulberry90.callback.ImStrSupplier;
import imgui.moulberry90.flag.ImGuiBackendFlags;
import imgui.moulberry90.flag.ImGuiConfigFlags;
import imgui.moulberry90.flag.ImGuiKey;
import imgui.moulberry90.flag.ImGuiMouseButton;
import imgui.moulberry90.flag.ImGuiMouseCursor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLTimer;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.system.MemoryUtil.memAllocInt;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT;

public class CustomImGuiImplSdl {
    private static final String OS = System.getProperty("os.name", "generic").toLowerCase();
    public static final boolean IS_WINDOWS = OS.contains("win");
    protected static final boolean IS_APPLE = OS.contains("mac") || OS.contains("darwin");

    // Handle of the main SDL window
    private long mainWindowPtr;

    private final IntBuffer winWidth = memAllocInt(1);
    private final IntBuffer winHeight = memAllocInt(1);
    private final IntBuffer fbWidth = memAllocInt(1);
    private final IntBuffer fbHeight = memAllocInt(1);

    private final long[] mouseCursors = new long[ImGuiMouseCursor.COUNT];
    private final long[] keyOwnerWindows = new long[512];
    private final boolean[] keyPressedGame = new boolean[512];

    private final boolean[] mouseJustPressed = new boolean[ImGuiMouseButton.COUNT];
    private final ImVec2 mousePosBackup = new ImVec2();

    private boolean wantUpdateMonitors = true;
    private double time = 0.0;
    private long mouseWindowPtr;

    private MouseHandledBy grabbed = null;
    private int ignoreMouseMovements = 0;
    private boolean releasedAllKeysBecauseOfDialog = false;
    private boolean releasedAllKeysBecauseOfDisable = false;
    private final double[] grabbedOriginalMouseX = new double[1];
    private final double[] grabbedOriginalMouseY = new double[1];
    private int grabLinkedKey = -1;
    private boolean releaseGrabOnUp = false;

    public double rawMouseX;
    public double rawMouseY;

    public float contentScale = 1.0f;

    public enum MouseHandledBy {
        EDITOR_GRABBED,
        IMGUI,
        GAME,
        BOTH;

        public boolean allowImgui() {
            return this == IMGUI || this == BOTH;
        }

        public boolean allowGame() {
            return this == GAME || this == BOTH;
        }
    }

    public MouseHandledBy getMouseHandledBy() {
        if (!ReplayUI.isActive()) return MouseHandledBy.GAME;
        if (this.grabbed != null) return this.grabbed;
        if (ReplayUI.getIO().getWantCaptureMouse()) return MouseHandledBy.IMGUI;
        return MouseHandledBy.BOTH;
    }

    public boolean isGrabbed() {
        return this.grabbed != null;
    }

    public void ungrab() {
        if (this.grabbed == null) return;
        this.grabbed = null;
        this.grabLinkedKey = 0;

        SDLMouse.SDL_SetWindowRelativeMouseMode(this.mainWindowPtr, false);
        SDLMouse.SDL_WarpMouseInWindow(this.mainWindowPtr, (float) this.grabbedOriginalMouseX[0], (float) this.grabbedOriginalMouseY[0]);
    }

    public void setGrabbed(boolean passthroughToGame, int grabLinkedKey, boolean releaseGrabOnUp, double x, double y) {
        if (grabLinkedKey != 0) {
            if (grabLinkedKey < 0) {
                if (!isMouseButtonDown(-grabLinkedKey - 1)) {
                    this.ungrab();
                    return;
                }
            } else if (!isKeyDown(grabLinkedKey)) {
                this.ungrab();
                return;
            }
        }
        if (this.grabbed != null) return;
        this.grabbed = passthroughToGame ? MouseHandledBy.GAME : MouseHandledBy.EDITOR_GRABBED;

        if (grabLinkedKey != 0) {
            this.grabLinkedKey = grabLinkedKey;
            this.releaseGrabOnUp = releaseGrabOnUp;
        }
        if (x >= 0 && y >= 0) {
            this.grabbedOriginalMouseX[0] = x;
            this.grabbedOriginalMouseY[0] = y;
        } else {
            getMousePosition(this.grabbedOriginalMouseX, this.grabbedOriginalMouseY);
        }
        SDLMouse.SDL_SetWindowRelativeMouseMode(this.mainWindowPtr, true);
        this.ignoreMouseMovements = 2;
        Minecraft.getInstance().mouseHandler.setIgnoreFirstMove();
    }

    private static double grabbedLastMouseX = 0;
    private static double grabbedLastMouseY = 0;
    private static double grabbedCurrMouseX = 0;
    private static double grabbedCurrMouseY = 0;

    public double getGrabbedMouseDeltaX() {
        double delta = grabbedCurrMouseX - grabbedLastMouseX;
        grabbedLastMouseX = grabbedCurrMouseX;
        return delta;
    }

    public double getGrabbedMouseDeltaY() {
        double delta = grabbedCurrMouseY - grabbedLastMouseY;
        grabbedLastMouseY = grabbedCurrMouseY;
        return delta;
    }

    public static boolean isKeyDown(int scancode) {
        return InputConstants.isKeyDown(scancode);
    }

    /** @param button 0-based, matching ImGui's mouse button indices */
    public static boolean isMouseButtonDown(int button) {
        if (button < 0 || button >= ImGuiMouseButton.COUNT) {
            return false;
        }
        return (getMouseButtonMask() & (1 << button)) != 0;
    }

    private static final float[] mousePositionX = new float[1];
    private static final float[] mousePositionY = new float[1];

    /** Polls SDL and returns a bitmask of currently held mouse buttons (bit N = button N). */
    private static int pollMouseState() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer x = stack.mallocFloat(1);
            FloatBuffer y = stack.mallocFloat(1);
            int mask = SDLMouse.SDL_GetMouseState(x, y);
            mousePositionX[0] = x.get(0);
            mousePositionY[0] = y.get(0);
            return mask;
        }
    }

    private static int getMouseButtonMask() {
        return pollMouseState();
    }

    private static void getMousePosition(double[] x, double[] y) {
        pollMouseState();
        x[0] = mousePositionX[0];
        y[0] = mousePositionY[0];
    }

    private static boolean isShiftDown() {
        return isKeyDown(InputConstants.KEY_LSHIFT) || isKeyDown(InputConstants.KEY_RSHIFT);
    }

    private static boolean isCtrlDown() {
        return isKeyDown(InputConstants.KEY_LCONTROL) || isKeyDown(InputConstants.KEY_RCONTROL);
    }

    private static boolean isAltDown() {
        return isKeyDown(InputConstants.KEY_LALT) || isKeyDown(InputConstants.KEY_RALT);
    }

    private static boolean isSuperDown() {
        return isKeyDown(InputConstants.KEY_LGUI) || isKeyDown(InputConstants.KEY_RGUI);
    }

    protected void updateKeyModifiers() {
        final ImGuiIO io = ReplayUI.getIO();
        io.addKeyEvent(ImGuiKey.ModCtrl, isCtrlDown());
        io.addKeyEvent(ImGuiKey.ModShift, isShiftDown());
        io.addKeyEvent(ImGuiKey.ModAlt, isAltDown());
        io.addKeyEvent(ImGuiKey.ModSuper, isSuperDown());
    }

    public void mouseButtonCallback(final int button, final int action, final int mods) {
        if (AsyncFileDialogs.hasDialog()) return;

        if (!ReplayUI.isActive()) {
            return;
        }

        updateKeyModifiers();

        if (this.grabbed != null && this.grabLinkedKey < 0 && button == -this.grabLinkedKey - 1) {
            if ((action == InputConstants.RELEASE) == this.releaseGrabOnUp) {
                this.ungrab();
            }
        }

        MouseHandledBy handledBy = this.getMouseHandledBy();
        if (handledBy.allowImgui() && action == InputConstants.PRESS && button >= 0 && button < this.mouseJustPressed.length) {
            this.mouseJustPressed[button] = true;
        }
    }

    public void scrollCallback(final double xOffset, final double yOffset) {
        if (AsyncFileDialogs.hasDialog()) return;

        if (ReplayUI.isActive()) {
            var io = ReplayUI.getIO();
            io.setMouseWheelH(io.getMouseWheelH() + (float) xOffset);
            io.setMouseWheel(io.getMouseWheel() + (float) yOffset);
        }
    }

    /**
     * @return true if the event was handled by Flashback/ImGui and must not reach vanilla
     */
    public boolean keyCallback(int key, final int scancode, final int action, final int mods) {
        if (AsyncFileDialogs.hasDialog()) return false;

        if (!ReplayUI.isActive() || Minecraft.getInstance().gui.screen() != null) {
            var io = ReplayUI.getIO();
            if (action == InputConstants.RELEASE && key >= 0 && key < this.keyOwnerWindows.length) {
                if (this.keyOwnerWindows[key] != -1) {
                    io.addKeyEvent(ImGuiKeyMapping.scancodeToImGuiKey(key), false);
                    this.keyOwnerWindows[key] = -1;
                }
            }
            return false;
        }

        if (key < InputConstants.KEY_SPACE || key > InputConstants.KEY_RGUI) {
            return false;
        }

        var io = ReplayUI.getIO();

        updateKeyModifiers();

        boolean shiftMod = (mods & InputConstants.MOD_SHIFT) != 0 || isShiftDown();
        boolean ctrlMod = (mods & InputConstants.MOD_CONTROL) != 0 || isCtrlDown();
        boolean altMod = (mods & InputConstants.MOD_ALT) != 0 || isAltDown();
        boolean superMod = (mods & InputConstants.MOD_SUPER) != 0 || isSuperDown();

        if (this.grabbed != null && action == InputConstants.RELEASE && this.grabLinkedKey > 0 && key == this.grabLinkedKey) {
            this.ungrab();
        }

        Keybind editingKeybind = ImGuiHelper.getEditingKeybind();
        if (action == InputConstants.PRESS && key != InputConstants.KEY_ESCAPE && editingKeybind != null) {
            if (editingKeybind.isForceScrollKey()) {
                shiftMod |= key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT;
                ctrlMod |= key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL;
                altMod |= key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT;
                superMod |= key == InputConstants.KEY_LGUI || key == InputConstants.KEY_RGUI;
                editingKeybind.set(Keybind.FAKE_SCROLL_KEY, shiftMod, ctrlMod, altMod, superMod);
                return true;
            }

            shiftMod &= key != InputConstants.KEY_LSHIFT && key != InputConstants.KEY_RSHIFT;
            ctrlMod &= key != InputConstants.KEY_LCONTROL && key != InputConstants.KEY_RCONTROL;
            altMod &= key != InputConstants.KEY_LALT && key != InputConstants.KEY_RALT;
            superMod &= key != InputConstants.KEY_LGUI && key != InputConstants.KEY_RGUI;

            if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
                boolean temp = ctrlMod;
                ctrlMod = superMod;
                superMod = temp;
            }

            editingKeybind.set(key, shiftMod, ctrlMod, altMod, superMod);
            return true;
        }

        if (action == InputConstants.PRESS && ImGuiHelper.getWantsSpecialInput()) {
            if (key == InputConstants.KEY_BACKSPACE && ImGuiHelper.backspaceInput(mods)) {
                return true;
            } else if (key == InputConstants.KEY_SPACE) {
                return true;
            }
        }

        boolean passToMinecraft;
        boolean passToImGui;
        if (action == InputConstants.RELEASE) {
            passToMinecraft = key >= 0 && key < this.keyPressedGame.length && this.keyPressedGame[key];
            passToImGui = key >= 0 && key < this.keyOwnerWindows.length && this.keyOwnerWindows[key] != -1;
        } else {
            passToMinecraft = shouldPassToMinecraft(io, key, scancode, mods);
            passToImGui = !passToMinecraft;
        }

        if (key >= 0 && key < this.keyPressedGame.length) {
            this.keyPressedGame[key] = action != InputConstants.RELEASE;
        }

        if (passToImGui && key >= 0 && key < this.keyOwnerWindows.length) {
            final int imguiKey = ImGuiKeyMapping.scancodeToImGuiKey(key);
            if (action == InputConstants.PRESS || action == InputConstants.REPEAT) {
                io.addKeyEvent(imguiKey, true);
                io.setKeyEventNativeData(imguiKey, key, scancode);
                this.keyOwnerWindows[key] = this.mainWindowPtr;
            } else if (action == InputConstants.RELEASE) {
                io.addKeyEvent(imguiKey, false);
                io.setKeyEventNativeData(imguiKey, key, scancode);
                this.keyOwnerWindows[key] = -1;
            }
        }

        return !passToMinecraft;
    }

    /** @return true if the event should also be delivered to Minecraft */
    private boolean shouldPassToMinecraft(ImGuiIO io, int key, int scancode, int mods) {
        if (key == InputConstants.KEY_ESCAPE) {
            return !io.getWantTextInput() && !ReplayUI.hasAnyPopupOpen;
        }

        // If any of our keybinds would be triggered, don't pass to Minecraft
        boolean shiftMod = (mods & InputConstants.MOD_SHIFT) != 0;
        boolean ctrlMod = (mods & InputConstants.MOD_CONTROL) != 0;
        boolean altMod = (mods & InputConstants.MOD_ALT) != 0;
        boolean superMod = (mods & InputConstants.MOD_SUPER) != 0;
        for (Keybind keybind : Keybinds.KEYBINDS) {
            if (keybind.wouldBePressed(key, shiftMod, ctrlMod, altMod, superMod)) {
                return false;
            }
        }

        // Pass all function keys to Minecraft
        if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F24) {
            return true;
        }

        // Pass all F3 combinations to Minecraft
        if (isKeyDown(InputConstants.KEY_F3)) {
            return true;
        }

        if (io.getWantTextInput()) {
            return false;
        } else if (this.grabbed == MouseHandledBy.GAME) {
            return true;
        }

        var options = Minecraft.getInstance().options;
        var keyEvent = new KeyEvent(key, scancode, mods);

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

    public void cursorPosCallback(final double xpos, final double ypos, final double xrel, final double yrel) {
        if (AsyncFileDialogs.hasDialog()) return;

        this.rawMouseX = xpos;
        this.rawMouseY = ypos;

        if (!ReplayUI.isActive()) {
            return;
        }

        MouseHandledBy handledBy = this.getMouseHandledBy();

        if (this.ignoreMouseMovements > 0) {
            grabbedCurrMouseX = xpos;
            grabbedCurrMouseY = ypos;
            grabbedLastMouseX = xpos;
            grabbedLastMouseY = ypos;
            this.ignoreMouseMovements -= 1;
            return;
        }

        if (handledBy == MouseHandledBy.EDITOR_GRABBED) {
            grabbedCurrMouseX = xpos;
            grabbedCurrMouseY = ypos;
        }
    }

    /** Relative motion while the cursor is grabbed, in raw device units. */
    public void relativeMotionCallback(final double xrel, final double yrel) {
        if (AsyncFileDialogs.hasDialog() || !ReplayUI.isActive()) return;
        if (this.getMouseHandledBy() != MouseHandledBy.EDITOR_GRABBED) return;

        grabbedCurrMouseX += xrel;
        grabbedCurrMouseY += yrel;
    }

    public void windowFocusCallback(final boolean focused) {
        ReplayUI.getIO().addFocusEvent(focused);
    }

    public void cursorEnterCallback(final boolean entered) {
        if (entered) {
            this.mouseWindowPtr = this.mainWindowPtr;
        } else if (this.mouseWindowPtr == this.mainWindowPtr) {
            this.mouseWindowPtr = 0;
        }
    }

    public void textInputCallback(final String text) {
        if (AsyncFileDialogs.hasDialog()) return;

        if (!ReplayUI.isActive()) {
            return;
        }

        var io = ReplayUI.getIO();
        if (!ImGuiHelper.addInputCharacter(text.charAt(0))) {
            io.addInputCharactersUTF8(text);
        }
    }

    public boolean init(final long windowId, final boolean installCallbacks) {
        this.mainWindowPtr = windowId;

        final ImGuiIO io = ReplayUI.getIO();

        io.addBackendFlags(ImGuiBackendFlags.HasMouseCursors | ImGuiBackendFlags.HasSetMousePos);
        io.setBackendPlatformName("imgui_java_impl_sdl3");

        KeyboardHandler keyboardHandler = Minecraft.getInstance().keyboardHandler;
        io.setGetClipboardTextFn(new ImStrSupplier() {
            @Override
            public String get() {
                return keyboardHandler.getClipboard();
            }
        });
        io.setSetClipboardTextFn(new ImStrConsumer() {
            @Override
            public void accept(String s) {
                keyboardHandler.setClipboard(s);
            }
        });

        // Create mouse cursors
        this.mouseCursors[ImGuiMouseCursor.Arrow] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT);
        this.mouseCursors[ImGuiMouseCursor.TextInput] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_TEXT);
        this.mouseCursors[ImGuiMouseCursor.ResizeNS] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_NS_RESIZE);
        this.mouseCursors[ImGuiMouseCursor.ResizeEW] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_EW_RESIZE);
        this.mouseCursors[ImGuiMouseCursor.Hand] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_POINTER);
        this.mouseCursors[ImGuiMouseCursor.ResizeAll] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_MOVE);
        this.mouseCursors[ImGuiMouseCursor.ResizeNESW] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_NESW_RESIZE);
        this.mouseCursors[ImGuiMouseCursor.ResizeNWSE] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_NWSE_RESIZE);
        this.mouseCursors[ImGuiMouseCursor.NotAllowed] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_NOT_ALLOWED);

        // Calculate content scale
        updateWindowSizeAndScale(io);

        // Update monitors the first time
        this.updateMonitors();

        return true;
    }

    private void updateWindowSizeAndScale(final ImGuiIO io) {
        SDLVideo.SDL_GetWindowSize(this.mainWindowPtr, this.winWidth, this.winHeight);
        SDLVideo.SDL_GetWindowSizeInPixels(this.mainWindowPtr, this.fbWidth, this.fbHeight);

        io.setDisplaySize((float) this.winWidth.get(0), (float) this.winHeight.get(0));
        if (this.winWidth.get(0) > 0 && this.winHeight.get(0) > 0) {
            final float scaleX = (float) this.fbWidth.get(0) / this.winWidth.get(0);
            final float scaleY = (float) this.fbHeight.get(0) / this.winHeight.get(0);
            io.setDisplayFramebufferScale(scaleX, scaleY);

            float windowScale = SDLVideo.SDL_GetWindowDisplayScale(this.mainWindowPtr);
            if (windowScale <= 0) windowScale = 1.0f;
            this.contentScale = Math.max(windowScale / scaleX, windowScale / scaleY);
        }
    }

    public void newFrame() {
        final ImGuiIO io = ReplayUI.getIO();

        updateWindowSizeAndScale(io);
        if (this.wantUpdateMonitors) {
            this.updateMonitors();
        }

        final double currentTime = SDLTimer.SDL_GetTicks() / 1000.0;
        io.setDeltaTime(this.time > 0.0 ? (float) (currentTime - this.time) : 1.0f / 60.0f);
        this.time = currentTime;

        if (AsyncFileDialogs.hasDialog()) {
            if (!this.releasedAllKeysBecauseOfDialog) {
                this.releasedAllKeysBecauseOfDialog = true;

                // Release for game
                for (int key = 0; key < this.keyPressedGame.length; key++) {
                    if (this.keyPressedGame[key]) {
                        this.keyPressedGame[key] = false;
                        Minecraft.getInstance().keyboardHandler.keyPress(this.mainWindowPtr, InputConstants.RELEASE, new KeyEvent(key, key, 0));
                    }
                }

                // Release for imgui
                Arrays.fill(this.keyOwnerWindows, -1);
                io.clearInputKeys();

                io.setKeyCtrl(false);
                io.setKeyShift(false);
                io.setKeyAlt(false);
                io.setKeySuper(false);
            }

            return;
        } else {
            this.releasedAllKeysBecauseOfDialog = false;
        }

        io.setKeyShift(isShiftDown());
        io.setKeyCtrl(isCtrlDown());
        io.setKeyAlt(isAltDown());
        io.setKeySuper(isSuperDown());

        this.updateMousePosAndButtons();
        this.updateMouseCursor();
        this.updateGamepads();
    }

    public void updateReleaseAllKeys(boolean release) {
        if (release) {
            if (!this.releasedAllKeysBecauseOfDisable) {
                this.releasedAllKeysBecauseOfDisable = true;

                var io = ReplayUI.getIO();

                Arrays.fill(this.keyOwnerWindows, -1);
                io.clearInputKeys();

                io.setKeyCtrl(false);
                io.setKeyShift(false);
                io.setKeyAlt(false);
                io.setKeySuper(false);

                SDLMouse.SDL_SetCursor(this.mouseCursors[ImGuiMouseCursor.Arrow]);
            }
        } else {
            this.releasedAllKeysBecauseOfDisable = false;
        }
    }

    private void updateMousePosAndButtons() {
        var io = ReplayUI.getIO();

        var mouseHandledBy = this.getMouseHandledBy();
        if (!mouseHandledBy.allowImgui() || AsyncFileDialogs.hasDialog()) {
            for (int i = 0; i < ImGuiMouseButton.COUNT; i++) {
                io.setMouseDown(i, false);
                this.mouseJustPressed[i] = false;
            }
            return;
        }

        int buttonMask = getMouseButtonMask();
        for (int i = 0; i < ImGuiMouseButton.COUNT; i++) {
            // If a mouse press event came, always pass it as "mouse held this frame", so we don't miss click-release events that are shorter than 1 frame.
            io.setMouseDown(i, this.mouseJustPressed[i] || (buttonMask & (1 << i)) != 0);
            this.mouseJustPressed[i] = false;
        }

        io.getMousePos(this.mousePosBackup);
        io.setMousePos(-Float.MAX_VALUE, -Float.MAX_VALUE);
        io.setMouseHoveredViewport(0);

        pollMouseState();
        if (this.mouseWindowPtr == this.mainWindowPtr) {
            io.setMousePos(mousePositionX[0], mousePositionY[0]);
        }

        // Set OS mouse position from Dear ImGui if requested (rarely used, only when ImGuiConfigFlags_NavEnableSetMousePos is enabled by user)
        if (io.getWantSetMousePos()) {
            SDLMouse.SDL_WarpMouseInWindow(this.mainWindowPtr, this.mousePosBackup.x, this.mousePosBackup.y);
        }
    }

    private void updateMouseCursor() {
        if (AsyncFileDialogs.hasDialog()) return;

        var io = ReplayUI.getIO();
        if (io.hasConfigFlags(ImGuiConfigFlags.NoMouseCursorChange)) {
            return;
        }

        final int imguiCursor = ImGui.getMouseCursor();
        if (imguiCursor == ImGuiMouseCursor.None || io.getMouseDrawCursor()) {
            SDLMouse.SDL_HideCursor();
        } else {
            long cursor = this.mouseCursors[imguiCursor];
            SDLMouse.SDL_SetCursor(cursor != 0 ? cursor : this.mouseCursors[ImGuiMouseCursor.Arrow]);
            SDLMouse.SDL_ShowCursor();
        }
    }

    @FunctionalInterface
    private interface MapButton {
        void run(int keyNo, int buttonNo);
    }

    @FunctionalInterface
    private interface MapAnalog {
        void run(int keyNo, int axisNo, float v0, float v1);
    }

    @SuppressWarnings("ManualMinMaxCalculation")
    private float saturate(final float v) {
        return v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v;
    }

    private void updateGamepads() {
        if (AsyncFileDialogs.hasDialog()) return;
        final ImGuiIO io = ReplayUI.getIO();

        if (!io.hasConfigFlags(ImGuiConfigFlags.NavEnableGamepad)) {
            return;
        }

        io.removeBackendFlags(ImGuiBackendFlags.HasGamepad);
    }

    protected void updateMonitors() {
        final ImGuiPlatformIO platformIO = ImGui.getPlatformIO();
        this.wantUpdateMonitors = false;

        IntBuffer displays = SDLVideo.SDL_GetDisplays();
        if (displays == null || displays.limit() == 0) { // Preserve existing monitor list if there are none. Happens on macOS sleeping (#5683)
            return;
        }

        platformIO.resizeMonitors(0);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int n = 0; n < displays.limit(); n++) {
                final int display = displays.get(n);

                SDL_Rect bounds = SDL_Rect.malloc(stack);
                if (!SDLVideo.SDL_GetDisplayBounds(display, bounds)) {
                    continue;
                }

                SDL_Rect workArea = SDL_Rect.malloc(stack);
                boolean haveWorkArea = SDLVideo.SDL_GetDisplayUsableBounds(display, workArea)
                        && workArea.w() > 0 && workArea.h() > 0;

                float dpiScale = SDLVideo.SDL_GetDisplayContentScale(display);

                platformIO.pushMonitors(display, bounds.x(), bounds.y(), bounds.w(), bounds.h(),
                        haveWorkArea ? workArea.x() : 0, haveWorkArea ? workArea.y() : 0,
                        haveWorkArea ? workArea.w() : 0, haveWorkArea ? workArea.h() : 0,
                        dpiScale);
            }
        }
    }

    public void setViewportWindowsHidden(boolean viewportWindowsHidden) {
        // Multi-viewport support is disabled, so the main window is the only one we own
    }

}
