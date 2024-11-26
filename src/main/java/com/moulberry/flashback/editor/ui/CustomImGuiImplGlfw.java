package com.moulberry.flashback.editor.ui;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiPlatformIO;
import imgui.ImGuiViewport;
import imgui.ImVec2;
import imgui.callback.ImPlatformFuncViewport;
import imgui.callback.ImPlatformFuncViewportFloat;
import imgui.callback.ImPlatformFuncViewportImVec2;
import imgui.callback.ImPlatformFuncViewportString;
import imgui.callback.ImPlatformFuncViewportSuppBoolean;
import imgui.callback.ImPlatformFuncViewportSuppImVec2;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.ImGuiBackendFlags;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiNavInput;
import imgui.flag.ImGuiViewportFlags;
import imgui.lwjgl3.glfw.ImGuiImplGlfwNative;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCharModsCallback;
import org.lwjgl.glfw.GLFWCursorEnterCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMonitorCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWWindowFocusCallback;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class CustomImGuiImplGlfw {
    private static final String OS = System.getProperty("os.name", "generic").toLowerCase();
    public static final boolean IS_WINDOWS = OS.contains("win");
    protected static final boolean IS_APPLE = OS.contains("mac") || OS.contains("darwin");

    // ImGui Structs
    private ImGuiIO io = null;

    // Pointer of the current GLFW window
    private long mainWindowPtr;

    // Some features may be available only from a specific version
    private boolean glfwHawWindowTopmost;
    private boolean glfwHasWindowAlpha;
    private boolean glfwHasPerMonitorDpi;
    private boolean glfwHasFocusWindow;
    private boolean glfwHasFocusOnShow;
    private boolean glfwHasMonitorWorkArea;
    private boolean glfwHasOsxWindowPosFix;

    // For application window properties
    private final int[] winWidth = new int[1];
    private final int[] winHeight = new int[1];
    private final int[] fbWidth = new int[1];
    private final int[] fbHeight = new int[1];
    private final float[] windowScaleX = new float[1];
    private final float[] windowScaleY = new float[1];

    // Mouse cursors provided by GLFW
    private final long[] mouseCursors = new long[ImGuiMouseCursor.COUNT];
    private final long[] keyOwnerWindows = new long[512];
    private final boolean[] keyPressedGame = new boolean[512];
    // private final int[] physicalToPrintableKeys = new int[GLFW_KEY_LAST - GLFW_KEY_SPACE + 1];

    // Empty array to fill ImGuiIO.NavInputs with zeroes
    private final float[] emptyNavInputs = new float[ImGuiNavInput.COUNT];

    // For mouse tracking
    private final boolean[] mouseJustPressed = new boolean[ImGuiMouseButton.COUNT];
    private final ImVec2 mousePosBackup = new ImVec2();
    private final double[] mouseX = new double[1];
    private final double[] mouseY = new double[1];

    private final int[] windowX = new int[1];
    private final int[] windowY = new int[1];

    // Monitor properties
    private final int[] monitorX = new int[1];
    private final int[] monitorY = new int[1];
    private final int[] monitorWorkAreaX = new int[1];
    private final int[] monitorWorkAreaY = new int[1];
    private final int[] monitorWorkAreaWidth = new int[1];
    private final int[] monitorWorkAreaHeight = new int[1];
    private final float[] monitorContentScaleX = new float[1];
    private final float[] monitorContentScaleY = new float[1];

    // GLFW callbacks
    private GLFWWindowFocusCallback prevUserCallbackWindowFocus = null;
    private GLFWMouseButtonCallback prevUserCallbackMouseButton = null;
    private GLFWScrollCallback prevUserCallbackScroll = null;
    private GLFWCursorPosCallback prevUserCallbackCursorPos = null;
    private GLFWKeyCallback prevUserCallbackKey = null;
    private GLFWCharModsCallback prevUserCallbackChar = null;
    private GLFWMonitorCallback prevUserCallbackMonitor = null;
    private GLFWCursorEnterCallback prevUserCallbackCursorEnter = null;

    // Internal data
    private boolean callbacksInstalled = false;
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
        if (this.io.getWantCaptureMouse()) return MouseHandledBy.IMGUI;
        return MouseHandledBy.BOTH;
    }

    public boolean isGrabbed() {
        return this.grabbed != null;
    }

    public void ungrab() {
        if (this.grabbed == null) return;
        this.grabbed = null;
        this.grabLinkedKey = 0;

        GLFW.glfwSetInputMode(this.mainWindowPtr, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(this.mainWindowPtr, this.grabbedOriginalMouseX[0], this.grabbedOriginalMouseY[0]);
    }

    public void setGrabbed(boolean passthroughToGame, int grabLinkedKey, boolean releaseGrabOnUp, double x, double y) {
        if (grabLinkedKey != 0) {
            if (grabLinkedKey < 0) {
                if (GLFW.glfwGetMouseButton(this.mainWindowPtr, -grabLinkedKey-1) == GLFW_RELEASE) {
                    this.ungrab();
                    return;
                }
            } else if (GLFW.glfwGetKey(this.mainWindowPtr, grabLinkedKey) == GLFW_RELEASE) {
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
            GLFW.glfwGetCursorPos(this.mainWindowPtr, this.grabbedOriginalMouseX, this.grabbedOriginalMouseY);
        }
        GLFW.glfwSetInputMode(this.mainWindowPtr, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
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

    /**
     * Method to set the {@link GLFWMouseButtonCallback}.
     *
     * @param windowId pointer to the window
     * @param button   clicked mouse button
     * @param action   click action type
     * @param mods     click modifiers
     */
    public void mouseButtonCallback(final long windowId, final int button, final int action, final int mods) {
        if (AsyncFileDialogs.hasDialog()) return;

        if (!ReplayUI.isActive()) {
            if (this.prevUserCallbackMouseButton != null && windowId == this.mainWindowPtr) {
                this.prevUserCallbackMouseButton.invoke(windowId, button, action, mods);
            }
            return;
        }

        if (this.grabbed != null && this.grabLinkedKey < 0 && button == -this.grabLinkedKey-1) {
            if ((action == GLFW_RELEASE) == this.releaseGrabOnUp) {
                this.ungrab();
            }
        }

        MouseHandledBy handledBy = this.getMouseHandledBy();

        if (handledBy.allowGame() && this.prevUserCallbackMouseButton != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackMouseButton.invoke(windowId, button, action, mods);
        }
        if (handledBy.allowImgui() && action == GLFW_PRESS && button >= 0 && button < this.mouseJustPressed.length) {
            this.mouseJustPressed[button] = true;
        }
    }

    /**
     * Method to set the {@link GLFWScrollCallback}.
     *
     * @param windowId pointer to the window
     * @param xOffset  scroll offset by x-axis
     * @param yOffset  scroll offset by y-axis
     */
    public void scrollCallback(final long windowId, final double xOffset, final double yOffset) {
        if (AsyncFileDialogs.hasDialog()) return;

        if (ReplayUI.isActive()) {
            this.io.setMouseWheelH(this.io.getMouseWheelH() + (float) xOffset);
            this.io.setMouseWheel(this.io.getMouseWheel() + (float) yOffset);

            if (Minecraft.getInstance().screen == null || !ReplayUI.isMainFrameActive()) {
                return;
            }
        }

        if (this.prevUserCallbackScroll != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackScroll.invoke(windowId, 0, yOffset);
        }
    }

    /**
     * Method to set the {@link GLFWKeyCallback}.
     *
     * @param windowId pointer to the window
     * @param key      pressed key
     * @param scancode key scancode
     * @param action   press action
     * @param mods     press modifiers
     */
    public void keyCallback(final long windowId, int key, final int scancode, final int action, final int mods) {
        if (AsyncFileDialogs.hasDialog()) return;

        if (key < GLFW_KEY_SPACE || key > GLFW_KEY_LAST) {
            if (this.prevUserCallbackKey != null && windowId == this.mainWindowPtr) this.prevUserCallbackKey.invoke(windowId, key, scancode, action, mods);
            return;
        }

        boolean shiftMod = (mods & GLFW_MOD_SHIFT) != 0;
        boolean ctrlMod = (mods & GLFW_MOD_CONTROL) != 0;
        boolean altMod = (mods & GLFW_MOD_ALT) != 0;
        boolean superMod = (mods & GLFW_MOD_SUPER) != 0;
        int minecraftKey = key;
        boolean keyInBoundsForGame = minecraftKey >= 0 && minecraftKey < this.keyPressedGame.length;

        if (this.grabbed != null && action == GLFW_RELEASE && this.grabLinkedKey > 0 && key == this.grabLinkedKey) {
            this.ungrab();
        }

        if (!ReplayUI.isActive() || Minecraft.getInstance().screen != null) {
            if (action == GLFW_RELEASE && key >= 0 && key < this.keyOwnerWindows.length) {
                this.io.setKeysDown(key, false);
                this.keyOwnerWindows[key] = 0;
            }
            if (this.prevUserCallbackKey != null && windowId == this.mainWindowPtr) {
                if (keyInBoundsForGame) this.keyPressedGame[minecraftKey] = action != GLFW_RELEASE;

                // Don't allow key presses during export
                if (Flashback.isExporting()) {
                    if (action == GLFW_RELEASE && keyInBoundsForGame && this.keyPressedGame[minecraftKey]) {
                        this.keyPressedGame[minecraftKey] = false;
                    } else {
                        return;
                    }
                }

                this.prevUserCallbackKey.invoke(windowId, minecraftKey, scancode, action, mods);
            }
            return;
        }

        if (action == GLFW_PRESS && ImGuiHelper.getWantsSpecialInput()) {
            if (key == GLFW_KEY_BACKSPACE && ImGuiHelper.backspaceInput(mods)) {
                return;
            } else if (key == GLFW_KEY_SPACE) {
                return;
            }
        }

        boolean forcePassToGame = action != GLFW_RELEASE && ((key == GLFW_KEY_ESCAPE && !io.getWantTextInput() && !ReplayUI.hasAnyPopupOpen) ||
                (key >= GLFW_KEY_F1 && key <= GLFW_KEY_F25) || GLFW.glfwGetKey(windowId, GLFW_KEY_F3) != GLFW_RELEASE);
        if (forcePassToGame) {
            this.prevUserCallbackKey.invoke(windowId, minecraftKey, scancode, action, mods);
            if (keyInBoundsForGame) this.keyPressedGame[minecraftKey] = true;
            return;
        }

        boolean passToMinecraft = false;
        boolean passToImGui = false;

        if (action == GLFW_RELEASE) {
            if (keyInBoundsForGame && this.keyPressedGame[minecraftKey]) {
                passToMinecraft = true;
            }
            if (key >= 0 && key < this.keyOwnerWindows.length) {
                passToImGui = true;
            }
        } else {
            if (io.getWantTextInput()) {
                passToMinecraft = false;
            } else if (this.grabbed == MouseHandledBy.GAME) {
                passToMinecraft = true;
            } else if (ReplayUI.isMainFrameActive()) {
                for (KeyMapping keyMapping : Minecraft.getInstance().options.keyMappings) {
                    if (keyMapping.matches(key, scancode)) {
                        passToMinecraft = true;
                        break;
                    }
                }
            } else if (Minecraft.getInstance().options.keyUp.matches(key, scancode) ||
                    Minecraft.getInstance().options.keyLeft.matches(key, scancode) ||
                    Minecraft.getInstance().options.keyDown.matches(key, scancode) ||
                    Minecraft.getInstance().options.keyRight.matches(key, scancode) ||
                    Minecraft.getInstance().options.keyJump.matches(key, scancode) ||
                    Minecraft.getInstance().options.keyChat.matches(key, scancode) ||
                    Minecraft.getInstance().options.keyCommand.matches(key, scancode)) {
                ReplayUI.focusMainWindowCounter = 5;
                passToMinecraft = true;
            }

            passToImGui = !passToMinecraft;
        }

        if (passToMinecraft && this.prevUserCallbackKey != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackKey.invoke(windowId, minecraftKey, scancode, action, mods);
            if (keyInBoundsForGame) this.keyPressedGame[minecraftKey] = action != GLFW_RELEASE;
        }

        if (passToImGui && key >= 0 && key < this.keyOwnerWindows.length) {
            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                io.setKeysDown(key, true);
                this.keyOwnerWindows[key] = windowId;
            } else if (action == GLFW_RELEASE) {
                io.setKeysDown(key, false);
                this.keyOwnerWindows[key] = 0;
            }
        }
    }

    public void cursorPosCallback(final long windowId, final double xpos, final double ypos) {
        if (AsyncFileDialogs.hasDialog()) return;

        this.rawMouseX = xpos;
        this.rawMouseY = ypos;

        if (!ReplayUI.isActive()) {
            if (this.prevUserCallbackCursorPos != null && windowId == this.mainWindowPtr) this.prevUserCallbackCursorPos.invoke(windowId, xpos, ypos);
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

        if (handledBy.allowGame() && this.prevUserCallbackCursorPos != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackCursorPos.invoke(windowId, ReplayUI.getNewMouseX(xpos), ReplayUI.getNewMouseY(ypos));
        }
        if (handledBy == MouseHandledBy.EDITOR_GRABBED) {
            grabbedCurrMouseX = xpos;
            grabbedCurrMouseY = ypos;
        }
    }

    /**
     * Method to set the {@link GLFWWindowFocusCallback}.
     *
     * @param windowId pointer to the window
     * @param focused  is window focused
     */
    public void windowFocusCallback(final long windowId, final boolean focused) {
        if (this.prevUserCallbackWindowFocus != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackWindowFocus.invoke(windowId, focused);
        }

        this.io.addFocusEvent(focused);
    }

    /**
     * Method to set the {@link GLFWCursorEnterCallback}.
     *
     * @param windowId pointer to the window
     * @param entered  is cursor entered
     */
    public void cursorEnterCallback(final long windowId, final boolean entered) {
        if (this.prevUserCallbackCursorEnter != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackCursorEnter.invoke(windowId, entered);
        }

        if (entered) {
            this.mouseWindowPtr = windowId;
        }
        if (!entered && this.mouseWindowPtr == windowId) {
            this.mouseWindowPtr = 0;
        }
    }

    /**
     * Method to set the {@link GLFWCharCallback}.
     *
     * @param windowId pointer to the window
     * @param c        pressed char
     */
    public void charCallback(final long windowId, final int c, final int mods) {
        if (AsyncFileDialogs.hasDialog()) return;

        if (!ReplayUI.isActive()) {
            if (this.prevUserCallbackChar != null && windowId == this.mainWindowPtr) this.prevUserCallbackChar.invoke(windowId, c, mods);
            return;
        }

        if (!this.io.getWantCaptureKeyboard() && !this.io.getWantTextInput() && this.prevUserCallbackChar != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackChar.invoke(windowId, c, mods);
        }

        if (!ImGuiHelper.addInputCharacter((char) c)) {
            this.io.addInputCharacter(c);
        }
    }

    /**
     * Method to set the {@link GLFWMonitorCallback}.
     *
     * @param windowId pointer to the window
     * @param event    monitor event type (ignored)
     */
    public void monitorCallback(final long windowId, final int event) {
        if (this.prevUserCallbackMonitor != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackMonitor.invoke(windowId, event);
        }
        this.wantUpdateMonitors = true;
    }

    private final int[] keyMap = new int[ImGuiKey.COUNT];
    {
        this.keyMap[ImGuiKey.Tab] = -1; // Note: Tab input forcibly unhooked
        this.keyMap[ImGuiKey.LeftArrow] = GLFW_KEY_LEFT;
        this.keyMap[ImGuiKey.RightArrow] = GLFW_KEY_RIGHT;
        this.keyMap[ImGuiKey.UpArrow] = GLFW_KEY_UP;
        this.keyMap[ImGuiKey.DownArrow] = GLFW_KEY_DOWN;
        this.keyMap[ImGuiKey.PageUp] = GLFW_KEY_PAGE_UP;
        this.keyMap[ImGuiKey.PageDown] = GLFW_KEY_PAGE_DOWN;
        this.keyMap[ImGuiKey.Home] = GLFW_KEY_HOME;
        this.keyMap[ImGuiKey.End] = GLFW_KEY_END;
        this.keyMap[ImGuiKey.Insert] = GLFW_KEY_INSERT;
        this.keyMap[ImGuiKey.Delete] = GLFW_KEY_DELETE;
        this.keyMap[ImGuiKey.Backspace] = GLFW_KEY_BACKSPACE;
        this.keyMap[ImGuiKey.Space] = GLFW_KEY_SPACE;
        this.keyMap[ImGuiKey.Enter] = GLFW_KEY_ENTER;
        this.keyMap[ImGuiKey.Escape] = GLFW_KEY_ESCAPE;
        this.keyMap[ImGuiKey.KeyPadEnter] = GLFW_KEY_KP_ENTER;
        this.keyMap[ImGuiKey.A] = GLFW_KEY_A;
        this.keyMap[ImGuiKey.C] = GLFW_KEY_C;
        this.keyMap[ImGuiKey.V] = GLFW_KEY_V;
        this.keyMap[ImGuiKey.X] = GLFW_KEY_X;
        this.keyMap[ImGuiKey.Y] = GLFW_KEY_Y;
        this.keyMap[ImGuiKey.Z] = GLFW_KEY_Z;
    }

    public void enableTabInput() {
        this.keyMap[ImGuiKey.Tab] = GLFW_KEY_TAB;
        this.io.setKeyMap(this.keyMap);
    }

    public void disableTabInput() {
        this.keyMap[ImGuiKey.Tab] = -1;
        this.io.setKeyMap(this.keyMap);
    }

    /**
     * Method to do an initialization of the {@link imgui.glfw.ImGuiImplGlfw} state. It SHOULD be called before calling the {@link imgui.glfw.ImGuiImplGlfw#newFrame()} method.
     * <p>
     * Method takes two arguments, which should be a valid GLFW window pointer and a boolean indicating whether or not to install callbacks.
     *
     * @param windowId         pointer to the window
     * @param installCallbacks should window callbacks be installed
     * @return true if everything initialized
     */
    public boolean init(final long windowId, final boolean installCallbacks, ImGuiIO imGuiIo) {
        this.mainWindowPtr = windowId;
        this.io = imGuiIo;

        this.detectGlfwVersionAndEnabledFeatures();

        io.addBackendFlags(ImGuiBackendFlags.HasMouseCursors | ImGuiBackendFlags.HasSetMousePos | ImGuiBackendFlags.PlatformHasViewports);
        io.setBackendPlatformName("imgui_java_impl_glfw");

        // Keyboard mapping. ImGui will use those indices to peek into the io.KeysDown[] array.
        io.setKeyMap(this.keyMap);

        io.setGetClipboardTextFn(new ImStrSupplier() {
            @Override
            public String get() {
                final String clipboardString = glfwGetClipboardString(windowId);
                return clipboardString != null ? clipboardString : "";
            }
        });

        io.setSetClipboardTextFn(new ImStrConsumer() {
            @Override
            public void accept(final String str) {
                glfwSetClipboardString(windowId, str);
            }
        });

        // Mouse cursors mapping. Disable errors whilst setting due to X11.
        final GLFWErrorCallback prevErrorCallback = glfwSetErrorCallback(null);
        this.mouseCursors[ImGuiMouseCursor.Arrow] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.TextInput] = glfwCreateStandardCursor(GLFW_IBEAM_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.ResizeAll] = glfwCreateStandardCursor(GLFW_RESIZE_ALL_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.ResizeNS] = glfwCreateStandardCursor(GLFW_RESIZE_NS_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.ResizeEW] = glfwCreateStandardCursor(GLFW_RESIZE_EW_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.ResizeNESW] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.ResizeNWSE] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.Hand] = glfwCreateStandardCursor(GLFW_POINTING_HAND_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.NotAllowed] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        glfwSetErrorCallback(prevErrorCallback);

        if (installCallbacks) {
            this.callbacksInstalled = true;
            this.prevUserCallbackWindowFocus = glfwSetWindowFocusCallback(windowId, this::windowFocusCallback);
            this.prevUserCallbackCursorEnter = glfwSetCursorEnterCallback(windowId, this::cursorEnterCallback);
            this.prevUserCallbackMouseButton = glfwSetMouseButtonCallback(windowId, this::mouseButtonCallback);
            this.prevUserCallbackScroll = glfwSetScrollCallback(windowId, this::scrollCallback);
            this.prevUserCallbackCursorPos = glfwSetCursorPosCallback(windowId, this::cursorPosCallback);
            this.prevUserCallbackKey = glfwSetKeyCallback(windowId, this::keyCallback);
            this.prevUserCallbackChar = glfwSetCharModsCallback(windowId, this::charCallback);
            this.prevUserCallbackMonitor = glfwSetMonitorCallback(this::monitorCallback);
        }

        // Calculate content scale
        glfwGetWindowSize(this.mainWindowPtr, this.winWidth, this.winHeight);
        glfwGetFramebufferSize(this.mainWindowPtr, this.fbWidth, this.fbHeight);

        io.setDisplaySize((float) this.winWidth[0], (float) this.winHeight[0]);
        if (this.winWidth[0] > 0 && this.winHeight[0] > 0) {
            final float scaleX = (float) this.fbWidth[0] / this.winWidth[0];
            final float scaleY = (float) this.fbHeight[0] / this.winHeight[0];
            io.setDisplayFramebufferScale(scaleX, scaleY);

            GLFW.glfwGetWindowContentScale(Minecraft.getInstance().getWindow().getWindow(),
                    this.windowScaleX, this.windowScaleY);
            this.contentScale = Math.max(this.windowScaleX[0] / scaleX, windowScaleY[0] / scaleY);
        }

        // Update monitors the first time (note: monitor callback are broken in GLFW 3.2 and earlier, see github.com/glfw/glfw/issues/784)
        this.updateMonitors();
        glfwSetMonitorCallback(this::monitorCallback);

        // Our mouse update function expect PlatformHandle to be filled for the main viewport
        final ImGuiViewport mainViewport = ImGui.getMainViewport();
        mainViewport.setPlatformHandle(this.mainWindowPtr);

        if (IS_WINDOWS) {
            mainViewport.setPlatformHandleRaw(GLFWNativeWin32.glfwGetWin32Window(windowId));
        }

        if (io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            this.initPlatformInterface();
        }

        return true;
    }

    /**
     * Updates {@link ImGuiIO} and {@link org.lwjgl.glfw.GLFW} state.
     */
    public void newFrame() {
        this.io.ptr = ImGui.getIO().ptr;

        glfwGetWindowSize(this.mainWindowPtr, this.winWidth, this.winHeight);
        glfwGetFramebufferSize(this.mainWindowPtr, this.fbWidth, this.fbHeight);

        this.io.setDisplaySize((float) this.winWidth[0], (float) this.winHeight[0]);
        if (this.winWidth[0] > 0 && this.winHeight[0] > 0) {
            final float scaleX = (float) this.fbWidth[0] / this.winWidth[0];
            final float scaleY = (float) this.fbHeight[0] / this.winHeight[0];
            this.io.setDisplayFramebufferScale(scaleX, scaleY);

            GLFW.glfwGetWindowContentScale(Minecraft.getInstance().getWindow().getWindow(),
                    this.windowScaleX, this.windowScaleY);
            this.contentScale = Math.max(this.windowScaleX[0] / scaleX, windowScaleY[0] / scaleY);
        }
        if (this.wantUpdateMonitors) {
            this.updateMonitors();
        }

        final double currentTime = glfwGetTime();
        this.io.setDeltaTime(this.time > 0.0 ? (float) (currentTime - this.time) : 1.0f / 60.0f);
        this.time = currentTime;

        if (AsyncFileDialogs.hasDialog()) {
            if (!this.releasedAllKeysBecauseOfDialog) {
                this.releasedAllKeysBecauseOfDialog = true;

                // Release for game
                for (int key = 0; key < this.keyPressedGame.length; key++) {
                    if (this.keyPressedGame[key]) {
                        int scancode = GLFW.glfwGetKeyScancode(key);
                        this.prevUserCallbackKey.invoke(this.mainWindowPtr, key, scancode, GLFW_RELEASE, 0);
                    }
                }

                // Release for imgui
                for (int key = 0; key < this.keyOwnerWindows.length; key++) {
                    this.io.setKeysDown(key, false);
                    this.keyOwnerWindows[key] = 0;
                }

                this.io.setKeyCtrl(false);
                this.io.setKeyShift(false);
                this.io.setKeyAlt(false);
                this.io.setKeySuper(false);
            }

            return;
        } else {
            this.releasedAllKeysBecauseOfDialog = false;
        }

        boolean shiftDown = false;
        boolean ctrlDown = false;
        boolean altDown = false;
        boolean superDown = false;

        final ImGuiPlatformIO platformIO = ImGui.getPlatformIO();
        for (int n = 0; n < platformIO.getViewportsSize(); n++) {
            final ImGuiViewport viewport = platformIO.getViewports(n);
            final long windowPtr = viewport.getPlatformHandle();

            if (glfwGetWindowAttrib(windowPtr, GLFW_FOCUSED) != 0) {
                shiftDown |= GLFW.glfwGetKey(windowPtr, GLFW_KEY_LEFT_SHIFT) != 0 || GLFW.glfwGetKey(windowPtr, GLFW_KEY_RIGHT_SHIFT) != 0;
                ctrlDown |= GLFW.glfwGetKey(windowPtr, GLFW_KEY_LEFT_CONTROL) != 0 || GLFW.glfwGetKey(windowPtr, GLFW_KEY_RIGHT_CONTROL) != 0;
                altDown |= GLFW.glfwGetKey(windowPtr, GLFW_KEY_LEFT_ALT) != 0 || GLFW.glfwGetKey(windowPtr, GLFW_KEY_RIGHT_ALT) != 0;
                superDown |= GLFW.glfwGetKey(windowPtr, GLFW_KEY_LEFT_SUPER) != 0 || GLFW.glfwGetKey(windowPtr, GLFW_KEY_RIGHT_SUPER) != 0;
            }
        }

        io.setKeyShift(shiftDown);
        io.setKeyCtrl(ctrlDown);
        io.setKeyAlt(altDown);
        io.setKeySuper(superDown);

        this.updateMousePosAndButtons();
        this.updateMouseCursor();
        this.updateGamepads();

    }

    public void updateReleaseAllKeys(boolean release) {
        if (release) {
            if (!this.releasedAllKeysBecauseOfDisable) {
                this.releasedAllKeysBecauseOfDisable = true;

                for (int key = 0; key < this.keyOwnerWindows.length; key++) {
                    this.io.setKeysDown(key, false);
                    this.keyOwnerWindows[key] = 0;
                }

                this.io.setKeyCtrl(false);
                this.io.setKeyShift(false);
                this.io.setKeyAlt(false);
                this.io.setKeySuper(false);

                final ImGuiPlatformIO platformIO = ImGui.getPlatformIO();
                for (int n = 0; n < platformIO.getViewportsSize(); n++) {
                    final long windowPtr = platformIO.getViewports(n).getPlatformHandle();

                    // Change cursor back to arrow
                    glfwSetCursor(windowPtr, this.mouseCursors[ImGuiMouseCursor.Arrow]);
                }
            }
        } else {
            this.releasedAllKeysBecauseOfDisable = false;
        }
    }

    private void detectGlfwVersionAndEnabledFeatures() {
        final int[] major = new int[1];
        final int[] minor = new int[1];
        final int[] rev = new int[1];
        glfwGetVersion(major, minor, rev);

        final int version = major[0] * 1000 + minor[0] * 100 + rev[0] * 10;

        this.glfwHawWindowTopmost = version >= 3200;
        this.glfwHasWindowAlpha = version >= 3300;
        this.glfwHasPerMonitorDpi = version >= 3300;
        this.glfwHasFocusWindow = version >= 3200;
        this.glfwHasFocusOnShow = version >= 3300;
        this.glfwHasMonitorWorkArea = version >= 3300;
    }

    private void updateMousePosAndButtons() {
        var mouseHandledBy = this.getMouseHandledBy();
        if (!mouseHandledBy.allowImgui() || AsyncFileDialogs.hasDialog()) {
            for (int i = 0; i < ImGuiMouseButton.COUNT; i++) {
                this.io.setMouseDown(i, false);
                this.mouseJustPressed[i] = false;
            }
            return;
        }

        for (int i = 0; i < ImGuiMouseButton.COUNT; i++) {
            // If a mouse press event came, always pass it as "mouse held this frame", so we don't miss click-release events that are shorter than 1 frame.
            this.io.setMouseDown(i, this.mouseJustPressed[i] || glfwGetMouseButton(this.mainWindowPtr, i) != 0);
            this.mouseJustPressed[i] = false;
        }

        this.io.getMousePos(this.mousePosBackup);
        this.io.setMousePos(-Float.MAX_VALUE, -Float.MAX_VALUE);
        this.io.setMouseHoveredViewport(0);

        final ImGuiPlatformIO platformIO = ImGui.getPlatformIO();

        for (int n = 0; n < platformIO.getViewportsSize(); n++) {
            final ImGuiViewport viewport = platformIO.getViewports(n);
            final long windowPtr = viewport.getPlatformHandle();

            final boolean focused = glfwGetWindowAttrib(windowPtr, GLFW_FOCUSED) != 0;

            // Update mouse buttons
            if (focused) {
                for (int i = 0; i < ImGuiMouseButton.COUNT; i++) {
                    if (!io.getMouseDown(i)) {
                        io.setMouseDown(i, glfwGetMouseButton(windowPtr, i) != 0);
                    }
                }
            }

            // Set OS mouse position from Dear ImGui if requested (rarely used, only when ImGuiConfigFlags_NavEnableSetMousePos is enabled by user)
            // (When multi-viewports are enabled, all Dear ImGui positions are same as OS positions)
            if (io.getWantSetMousePos() && focused) {
                glfwSetCursorPos(windowPtr, this.mousePosBackup.x - viewport.getPosX(), this.mousePosBackup.y - viewport.getPosY());
            }

            // Set Dear ImGui mouse position from OS position
            if (this.mouseWindowPtr == windowPtr || focused) {
                glfwGetCursorPos(windowPtr, this.mouseX, this.mouseY);

                if (io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                    // Multi-viewport mode: mouse position in OS absolute coordinates (io.MousePos is (0,0) when the mouse is on the upper-left of the primary monitor)
                    glfwGetWindowPos(windowPtr, this.windowX, this.windowY);
                    io.setMousePos((float) this.mouseX[0] + this.windowX[0], (float) this.mouseY[0] + this.windowY[0]);
                } else {
                    // Single viewport mode: mouse position in client window coordinates (io.MousePos is (0,0) when the mouse is on the upper-left corner of the app window)
                    io.setMousePos((float) this.mouseX[0], (float) this.mouseY[0]);
                }
            }
        }
    }

    private void updateMouseCursor() {
        if (AsyncFileDialogs.hasDialog()) return;

        final boolean noCursorChange = this.io.hasConfigFlags(ImGuiConfigFlags.NoMouseCursorChange);
        final boolean cursorDisabled = glfwGetInputMode(this.mainWindowPtr, GLFW_CURSOR) == GLFW_CURSOR_DISABLED;

        if (noCursorChange || cursorDisabled) {
            return;
        }

        final int imguiCursor = ImGui.getMouseCursor();
        final ImGuiPlatformIO platformIO = ImGui.getPlatformIO();

        for (int n = 0; n < platformIO.getViewportsSize(); n++) {
            final long windowPtr = platformIO.getViewports(n).getPlatformHandle();

            if (imguiCursor == ImGuiMouseCursor.None || io.getMouseDrawCursor()) {
                // Hide OS mouse cursor if imgui is drawing it or if it wants no cursor
                glfwSetInputMode(windowPtr, GLFW_CURSOR, GLFW_CURSOR_HIDDEN);
            } else {
                // Show OS mouse cursor
                // FIXME-PLATFORM: Unfocused windows seems to fail changing the mouse cursor with GLFW 3.2, but 3.3 works here.
                glfwSetCursor(windowPtr, this.mouseCursors[imguiCursor] != 0 ? this.mouseCursors[imguiCursor] : this.mouseCursors[ImGuiMouseCursor.Arrow]);
                glfwSetInputMode(windowPtr, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            }
        }
    }

    private void updateGamepads() {
        if (AsyncFileDialogs.hasDialog()) return;

        if (!this.io.hasConfigFlags(ImGuiConfigFlags.NavEnableGamepad)) {
            return;
        }

        this.io.setNavInputs(this.emptyNavInputs);

        final ByteBuffer buttons = glfwGetJoystickButtons(GLFW_JOYSTICK_1);
        final int buttonsCount = buttons.limit();

        final FloatBuffer axis = glfwGetJoystickAxes(GLFW_JOYSTICK_1);
        final int axisCount = axis.limit();

        this.mapButton(ImGuiNavInput.Activate, 0, buttons, buttonsCount, this.io);   // Cross / A
        this.mapButton(ImGuiNavInput.Cancel, 1, buttons, buttonsCount, this.io);     // Circle / B
        this.mapButton(ImGuiNavInput.Menu, 2, buttons, buttonsCount, this.io);       // Square / X
        this.mapButton(ImGuiNavInput.Input, 3, buttons, buttonsCount, this.io);      // Triangle / Y
        this.mapButton(ImGuiNavInput.DpadLeft, 13, buttons, buttonsCount, this.io);  // D-Pad Left
        this.mapButton(ImGuiNavInput.DpadRight, 11, buttons, buttonsCount, this.io); // D-Pad Right
        this.mapButton(ImGuiNavInput.DpadUp, 10, buttons, buttonsCount, this.io);    // D-Pad Up
        this.mapButton(ImGuiNavInput.DpadDown, 12, buttons, buttonsCount, this.io);  // D-Pad Down
        this.mapButton(ImGuiNavInput.FocusPrev, 4, buttons, buttonsCount, this.io);  // L1 / LB
        this.mapButton(ImGuiNavInput.FocusNext, 5, buttons, buttonsCount, this.io);  // R1 / RB
        this.mapButton(ImGuiNavInput.TweakSlow, 4, buttons, buttonsCount, this.io);  // L1 / LB
        this.mapButton(ImGuiNavInput.TweakFast, 5, buttons, buttonsCount, this.io);  // R1 / RB
        this.mapAnalog(ImGuiNavInput.LStickLeft, 0, -0.3f, -0.9f, axis, axisCount, this.io);
        this.mapAnalog(ImGuiNavInput.LStickRight, 0, +0.3f, +0.9f, axis, axisCount, this.io);
        this.mapAnalog(ImGuiNavInput.LStickUp, 1, +0.3f, +0.9f, axis, axisCount, this.io);
        this.mapAnalog(ImGuiNavInput.LStickDown, 1, -0.3f, -0.9f, axis, axisCount, this.io);

        if (axisCount > 0 && buttonsCount > 0) {
            this.io.addBackendFlags(ImGuiBackendFlags.HasGamepad);
        } else {
            this.io.removeBackendFlags(ImGuiBackendFlags.HasGamepad);
        }
    }

    private void mapButton(final int navNo, final int buttonNo, final ByteBuffer buttons, final int buttonsCount, final ImGuiIO io) {
        if (buttonsCount > buttonNo && buttons.get(buttonNo) == GLFW_PRESS) {
            io.setNavInputs(navNo, 1.0f);
        }
    }

    private void mapAnalog(
            final int navNo,
            final int axisNo,
            final float v0,
            final float v1,
            final FloatBuffer axis,
            final int axisCount,
            final ImGuiIO io
    ) {
        float v = axisCount > axisNo ? axis.get(axisNo) : v0;
        v = (v - v0) / (v1 - v0);
        if (v > 1.0f) {
            v = 1.0f;
        }
        if (io.getNavInputs(navNo) < v) {
            io.setNavInputs(navNo, v);
        }
    }

    private void updateMonitors() {
        final ImGuiPlatformIO platformIO = ImGui.getPlatformIO();
        final PointerBuffer monitors = glfwGetMonitors();

        platformIO.resizeMonitors(0);

        for (int n = 0; n < monitors.limit(); n++) {
            final long monitor = monitors.get(n);

            glfwGetMonitorPos(monitor, this.monitorX, this.monitorY);
            final GLFWVidMode vidMode = glfwGetVideoMode(monitor);
            final float mainPosX = this.monitorX[0];
            final float mainPosY = this.monitorY[0];
            final float mainSizeX = vidMode.width();
            final float mainSizeY = vidMode.height();

            if (this.glfwHasMonitorWorkArea) {
                glfwGetMonitorWorkarea(monitor, this.monitorWorkAreaX, this.monitorWorkAreaY, this.monitorWorkAreaWidth, this.monitorWorkAreaHeight);
            }

            float workPosX = 0;
            float workPosY = 0;
            float workSizeX = 0;
            float workSizeY = 0;

            // Workaround a small GLFW issue reporting zero on monitor changes: https://github.com/glfw/glfw/pull/1761
            if (this.glfwHasMonitorWorkArea && this.monitorWorkAreaWidth[0] > 0 && this.monitorWorkAreaHeight[0] > 0) {
                workPosX = this.monitorWorkAreaX[0];
                workPosY = this.monitorWorkAreaY[0];
                workSizeX = this.monitorWorkAreaWidth[0];
                workSizeY = this.monitorWorkAreaHeight[0];
            }

            // Warning: the validity of monitor DPI information on Windows depends on the application DPI awareness settings,
            // which generally needs to be set in the manifest or at runtime.
            if (this.glfwHasPerMonitorDpi) {
                glfwGetMonitorContentScale(monitor, this.monitorContentScaleX, this.monitorContentScaleY);
            }
            final float dpiScale = this.monitorContentScaleX[0];

            platformIO.pushMonitors(mainPosX, mainPosY, mainSizeX, mainSizeY, workPosX, workPosY, workSizeX, workSizeY, dpiScale);
        }

        this.wantUpdateMonitors = false;
    }

    //--------------------------------------------------------------------------------------------------------
    // MULTI-VIEWPORT / PLATFORM INTERFACE SUPPORT
    // This is an _advanced_ and _optional_ feature, allowing the back-end to create and handle multiple viewports simultaneously.
    // If you are new to dear imgui or creating a new binding for dear imgui, it is recommended that you completely ignore this section first..
    //--------------------------------------------------------------------------------------------------------

    private boolean viewportWindowsHidden = false;
    public void setViewportWindowsHidden(boolean viewportWindowsHidden) {
        if (this.viewportWindowsHidden == viewportWindowsHidden) return;

        if (!this.viewportWindowsHidden) {
            this.viewportWindowsHidden = true;

            final ImGuiPlatformIO platformIO = ImGui.getPlatformIO();
            for (int n = 0; n < platformIO.getViewportsSize(); n++) {
                final long windowPtr = platformIO.getViewports(n).getPlatformHandle();
                if (windowPtr != this.mainWindowPtr) glfwHideWindow(windowPtr);
            }
        } else {
            this.viewportWindowsHidden = false;

            final ImGuiPlatformIO platformIO = ImGui.getPlatformIO();
            for (int n = 0; n < platformIO.getViewportsSize(); n++) {
                final long windowPtr = platformIO.getViewports(n).getPlatformHandle();
                if (windowPtr != this.mainWindowPtr) glfwShowWindow(windowPtr);
            }
        }
    }

    private void windowCloseCallback(final long windowId) {
        final ImGuiViewport vp = ImGui.findViewportByPlatformHandle(windowId);
        vp.setPlatformRequestClose(true);
    }

    // GLFW may dispatch window pos/size events after calling glfwSetWindowPos()/glfwSetWindowSize().
    // However: depending on the platform the callback may be invoked at different time:
    // - on Windows it appears to be called within the glfwSetWindowPos()/glfwSetWindowSize() call
    // - on Linux it is queued and invoked during glfwPollEvents()
    // Because the event doesn't always fire on glfwSetWindowXXX() we use a frame counter tag to only
    // ignore recent glfwSetWindowXXX() calls.
    private void windowPosCallback(final long windowId, final int xPos, final int yPos) {
        final ImGuiViewport vp = ImGui.findViewportByPlatformHandle(windowId);
        final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
        final boolean ignoreEvent = (ImGui.getFrameCount() <= data.ignoreWindowPosEventFrame + 1);

        if (ignoreEvent) {
            return;
        }

        vp.setPlatformRequestMove(true);
    }

    private void windowSizeCallback(final long windowId, final int width, final int height) {
        final ImGuiViewport vp = ImGui.findViewportByPlatformHandle(windowId);
        final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
        final boolean ignoreEvent = (ImGui.getFrameCount() <= data.ignoreWindowSizeEventFrame + 1);

        if (ignoreEvent) {
            return;
        }

        vp.setPlatformRequestResize(true);
    }

    private final class CreateWindowFunction extends ImPlatformFuncViewport {
        @Override
        public void accept(final ImGuiViewport vp) {
            final ImGuiViewportDataGlfw data = new ImGuiViewportDataGlfw();

            vp.setPlatformUserData(data);

            // GLFW 3.2 unfortunately always set focus on glfwCreateWindow() if GLFW_VISIBLE is set, regardless of GLFW_FOCUSED
            // With GLFW 3.3, the hint GLFW_FOCUS_ON_SHOW fixes this problem
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
            if (CustomImGuiImplGlfw.this.glfwHasFocusOnShow) {
                glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
            }
            glfwWindowHint(GLFW_DECORATED, vp.hasFlags(ImGuiViewportFlags.NoDecoration) ? GLFW_FALSE : GLFW_TRUE);
            if (CustomImGuiImplGlfw.this.glfwHawWindowTopmost) {
                glfwWindowHint(GLFW_FLOATING, vp.hasFlags(ImGuiViewportFlags.TopMost) ? GLFW_TRUE : GLFW_FALSE);
            }

            data.window = glfwCreateWindow((int) vp.getSizeX(), (int) vp.getSizeY(), "No Title Yet", NULL, CustomImGuiImplGlfw.this.mainWindowPtr);
            data.windowOwned = true;

            vp.setPlatformHandle(data.window);

            if (IS_WINDOWS) {
                vp.setPlatformHandleRaw(GLFWNativeWin32.glfwGetWin32Window(data.window));
            }

            glfwSetWindowPos(data.window, (int) vp.getPosX(), (int) vp.getPosY());

            // Install GLFW callbacks for secondary viewports
            glfwSetMouseButtonCallback(data.window, CustomImGuiImplGlfw.this::mouseButtonCallback);
            glfwSetScrollCallback(data.window, CustomImGuiImplGlfw.this::scrollCallback);
            glfwSetKeyCallback(data.window, CustomImGuiImplGlfw.this::keyCallback);
            glfwSetCharModsCallback(data.window, CustomImGuiImplGlfw.this::charCallback);
            glfwSetWindowCloseCallback(data.window, CustomImGuiImplGlfw.this::windowCloseCallback);
            glfwSetWindowPosCallback(data.window, CustomImGuiImplGlfw.this::windowPosCallback);
            glfwSetWindowSizeCallback(data.window, CustomImGuiImplGlfw.this::windowSizeCallback);

            glfwMakeContextCurrent(data.window);
            glfwSwapInterval(0);
        }
    }

    private final class DestroyWindowFunction extends ImPlatformFuncViewport {
        @Override
        public void accept(final ImGuiViewport vp) {
            final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();

            if (data != null && data.windowOwned) {
                // Release any keys that were pressed in the window being destroyed and are still held down,
                // because we will not receive any release events after window is destroyed.
                for (int i = 0; i < CustomImGuiImplGlfw.this.keyOwnerWindows.length; i++) {
                    if (CustomImGuiImplGlfw.this.keyOwnerWindows[i] == data.window) {
                        CustomImGuiImplGlfw.this.keyCallback(data.window, i, 0, GLFW_RELEASE, 0); // Later params are only used for main viewport, on which this function is never called.
                    }
                }

                glfwDestroyWindow(data.window);
            }

            vp.setPlatformUserData(null);
            vp.setPlatformHandle(0);
        }
    }

    private final class ShowWindowFunction extends ImPlatformFuncViewport {
        @Override
        public void accept(final ImGuiViewport vp) {
            final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();

            if (IS_WINDOWS && vp.hasFlags(ImGuiViewportFlags.NoTaskBarIcon)) {
                ImGuiImplGlfwNative.win32hideFromTaskBar(vp.getPlatformHandleRaw());
            }

            if (!CustomImGuiImplGlfw.this.viewportWindowsHidden) glfwShowWindow(data.window);
        }
    }

    private static final class GetWindowPosFunction extends ImPlatformFuncViewportSuppImVec2 {
        private final int[] posX = new int[1];
        private final int[] posY = new int[1];

        @Override
        public void get(final ImGuiViewport vp, final ImVec2 dstImVec2) {
            final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
            glfwGetWindowPos(data.window, this.posX, this.posY);
            dstImVec2.x = this.posX[0];
            dstImVec2.y = this.posY[0];
        }
    }

    private static final class SetWindowPosFunction extends ImPlatformFuncViewportImVec2 {
        @Override
        public void accept(final ImGuiViewport vp, final ImVec2 imVec2) {
            final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
            data.ignoreWindowPosEventFrame = ImGui.getFrameCount();
            glfwSetWindowPos(data.window, (int) imVec2.x, (int) imVec2.y);
        }
    }

    private static final class GetWindowSizeFunction extends ImPlatformFuncViewportSuppImVec2 {
        private final int[] width = new int[1];
        private final int[] height = new int[1];

        @Override
        public void get(final ImGuiViewport vp, final ImVec2 dstImVec2) {
            final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
            glfwGetWindowSize(data.window, this.width, this.height);
            dstImVec2.x = this.width[0];
            dstImVec2.y = this.height[0];
        }
    }

    private final class SetWindowSizeFunction extends ImPlatformFuncViewportImVec2 {
        private final int[] x = new int[1];
        private final int[] y = new int[1];
        private final int[] width = new int[1];
        private final int[] height = new int[1];

        @Override
        public void accept(final ImGuiViewport vp, final ImVec2 imVec2) {
            final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
            // Native OS windows are positioned from the bottom-left corner on macOS, whereas on other platforms they are
            // positioned from the upper-left corner. GLFW makes an effort to convert macOS style coordinates, however it
            // doesn't handle it when changing size. We are manually moving the window in order for changes of size to be based
            // on the upper-left corner.
            if (IS_APPLE && !CustomImGuiImplGlfw.this.glfwHasOsxWindowPosFix) {
                glfwGetWindowPos(data.window, this.x, this.y);
                glfwGetWindowSize(data.window, this.width, this.height);
                glfwSetWindowPos(data.window, this.x[0], this.y[0] - this.height[0] + (int) imVec2.y);
            }
            data.ignoreWindowSizeEventFrame = ImGui.getFrameCount();
            glfwSetWindowSize(data.window, (int) imVec2.x, (int) imVec2.y);
        }
    }

    private static final class SetWindowTitleFunction extends ImPlatformFuncViewportString {
        @Override
        public void accept(final ImGuiViewport vp, final String str) {
            final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
            glfwSetWindowTitle(data.window, str);
        }
    }

    private final class SetWindowFocusFunction extends ImPlatformFuncViewport {
        @Override
        public void accept(final ImGuiViewport vp) {
            if (CustomImGuiImplGlfw.this.glfwHasFocusWindow) {
                final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
                glfwFocusWindow(data.window);
            }
        }
    }

    private static final class GetWindowFocusFunction extends ImPlatformFuncViewportSuppBoolean {
        @Override
        public boolean get(final ImGuiViewport vp) {
            final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
            return glfwGetWindowAttrib(data.window, GLFW_FOCUSED) != 0;
        }
    }

    private static final class GetWindowMinimizedFunction extends ImPlatformFuncViewportSuppBoolean {
        @Override
        public boolean get(final ImGuiViewport vp) {
            final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
            return glfwGetWindowAttrib(data.window, GLFW_ICONIFIED) != 0;
        }
    }

    private final class SetWindowAlphaFunction extends ImPlatformFuncViewportFloat {
        @Override
        public void accept(final ImGuiViewport vp, final float f) {
            if (CustomImGuiImplGlfw.this.glfwHasWindowAlpha) {
                final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
                glfwSetWindowOpacity(data.window, f);
            }
        }
    }

    private static final class RenderWindowFunction extends ImPlatformFuncViewport {
        @Override
        public void accept(final ImGuiViewport vp) {
            final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
            glfwMakeContextCurrent(data.window);
        }
    }

    private static final class SwapBuffersFunction extends ImPlatformFuncViewport {
        @Override
        public void accept(final ImGuiViewport vp) {
            final ImGuiViewportDataGlfw data = (ImGuiViewportDataGlfw) vp.getPlatformUserData();
            glfwMakeContextCurrent(data.window);
            glfwSwapBuffers(data.window);
        }
    }

    private void initPlatformInterface() {
        final ImGuiPlatformIO platformIO = ImGui.getPlatformIO();

        // Register platform interface (will be coupled with a renderer interface)
        platformIO.setPlatformCreateWindow(new CreateWindowFunction());
        platformIO.setPlatformDestroyWindow(new DestroyWindowFunction());
        platformIO.setPlatformShowWindow(new ShowWindowFunction());
        platformIO.setPlatformGetWindowPos(new GetWindowPosFunction());
        platformIO.setPlatformSetWindowPos(new SetWindowPosFunction());
        platformIO.setPlatformGetWindowSize(new GetWindowSizeFunction());
        platformIO.setPlatformSetWindowSize(new SetWindowSizeFunction());
        platformIO.setPlatformSetWindowTitle(new SetWindowTitleFunction());
        platformIO.setPlatformSetWindowFocus(new SetWindowFocusFunction());
        platformIO.setPlatformGetWindowFocus(new GetWindowFocusFunction());
        platformIO.setPlatformGetWindowMinimized(new GetWindowMinimizedFunction());
        platformIO.setPlatformSetWindowAlpha(new SetWindowAlphaFunction());
        platformIO.setPlatformRenderWindow(new RenderWindowFunction());
        platformIO.setPlatformSwapBuffers(new SwapBuffersFunction());

        // Register main window handle (which is owned by the main application, not by us)
        // This is mostly for simplicity and consistency, so that our code (e.g. mouse handling etc.) can use same logic for main and secondary viewports.
        final ImGuiViewport mainViewport = ImGui.getMainViewport();
        final ImGuiViewportDataGlfw data = new ImGuiViewportDataGlfw();
        data.window = this.mainWindowPtr;
        data.windowOwned = false;
        mainViewport.setPlatformUserData(data);
    }

    private static final class ImGuiViewportDataGlfw {
        long window;
        boolean windowOwned = false;
        int ignoreWindowPosEventFrame = -1;
        int ignoreWindowSizeEventFrame = -1;
    }
}

