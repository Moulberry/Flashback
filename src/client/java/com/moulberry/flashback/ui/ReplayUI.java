package com.moulberry.flashback.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.platform.Window;
import com.moulberry.flashback.FlashbackClient;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.ui.windows.Timeline;
import imgui.*;
import imgui.flag.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReplayUI {

    public static final CustomImGuiImplGlfw imguiGlfw = new CustomImGuiImplGlfw();
    private static final CustomImGuiImplGl3 imguiGl3 = new CustomImGuiImplGl3();
    private static boolean initialized = false;
    private static boolean enabled = false;

    private static boolean isFrameHovered = false;
    public static int frameX = 0;
    public static int frameY = 0;
    public static int frameWidth = 1;
    public static int frameHeight = 1;
    private static boolean activeLastFrame = false;

    public static Matrix4f lastProjectionMatrix = null;
    public static Quaternionf lastViewQuaternion = null;

    private static ImFont font = null;
    public static ImFont icons = null;

    private static String languageCode = null;
    private static boolean wasNavClose = false;
    private static boolean navClose = false;

    private static final Lock deferredCloseLock = new ReentrantLock();
    private static final IntList deferredCloseTextureIds = new IntArrayList();
    private static final List<AutoCloseable> deferredClose = new ArrayList<>();

    private static float globalScale = 1.0f;
    public static float newGlobalScale = 1.0f;
    private static float contentScale = 1.0f;

    public static void init() {
        if (initialized) {
            throw new IllegalStateException("EditorUI initialized twice");
        }
        initialized = true;

        // todo:
        // newGlobalScale = globalScale = Configuration.internal.globalScale;

        // Initialize config so that everything starts nicely docked

        // todo:
//        Path path = Axiom.getInstance().getConfigDirectory().resolve("imgui.ini");
//        if (!Files.exists(path)) {
//            try {
//                Files.write(path, InternalConfiguration.DEFAULT_LAYOUT.getBytes());
//            } catch(IOException ignored) {}
//        }

        ImGui.createContext();

        // todo:
//        Path relativePath = FabricLoader.getInstance().getGameDir().relativize(path);
//        Axiom.dbg("Using DearImGui config: " + relativePath);
//        ImGui.getIO().setIniFilename(relativePath.toString());

        ImGui.getIO().addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        ImGui.getIO().addConfigFlags(ImGuiConfigFlags.DockingEnable);
        // ImGui.getIO().addConfigFlags(ImGuiConfigFlags.ViewportsEnable);
        ImGui.getIO().setConfigMacOSXBehaviors(Minecraft.ON_OSX);

        imguiGlfw.init(Minecraft.getInstance().getWindow().getWindow(), true);
        imguiGl3.init("#version 150");

//        contentScale = imguiGlfw.contentScale;
        initFonts(languageCode);
    }

    public static void initFonts(String languageCode) {
        if (languageCode != null) {
            ReplayUI.languageCode = languageCode;
        } else {
            languageCode = "en_us";
        }

        if (!initialized) {
            return;
        }

        ImGuiIO io = ImGui.getIO();
        ImFontAtlas fonts = io.getFonts();
        fonts.clear();

        int size = (int)(16 * getUiScale());

        ImFontGlyphRangesBuilder rangesBuilder = new ImFontGlyphRangesBuilder();
        rangesBuilder.addRanges(fonts.getGlyphRangesDefault());

        if (languageCode.startsWith("uk") || languageCode.startsWith("ru") || languageCode.startsWith("bg")) {
            rangesBuilder.addRanges(fonts.getGlyphRangesCyrillic());
        } else if (languageCode.startsWith("tr")) {
            rangesBuilder.addText("ÇçĞğİıÖöŞşÜü");
        } else if (languageCode.startsWith("pl")) {
            rangesBuilder.addRanges(new short[]{0x0100, 0x017F, 0});
        } else if (languageCode.startsWith("cs")) {
            rangesBuilder.addText("ÁáČčĎďÉéĚěÍíŇňÓóŘřŠšŤťÚúŮůÝýŽž");
        }

        rangesBuilder.addChar('\u2318'); // Mac CMD
        rangesBuilder.addChar('\u2303'); // CTRL
        rangesBuilder.addChar('\u2387'); // Alt
        rangesBuilder.addChar('\u21E7'); // Shift (up arrow)
        rangesBuilder.addChar('\u2756'); // Super
        rangesBuilder.addChar('\u26A0'); // Warning symbol
        rangesBuilder.addChar('\u2190'); // Left Arrow
        rangesBuilder.addChar('\u2191'); // Up Arrow
        rangesBuilder.addChar('\u2192'); // Right Arrow
        rangesBuilder.addChar('\u2193'); // Down Arrow

        // Make sure every printable key on the keyboard is present
        for (int i = GLFW.GLFW_KEY_SPACE; i <= GLFW.GLFW_KEY_LAST; i++) {
            int scancode = GLFW.glfwGetKeyScancode(i);
            if (scancode != -1) {
                String key = GLFW.glfwGetKeyName(i, -1);
                if (key != null) {
                    rangesBuilder.addText(key);
                    rangesBuilder.addText(key.toLowerCase());
                    rangesBuilder.addText(key.toUpperCase());
                    rangesBuilder.addText(key.toLowerCase(Locale.ROOT));
                    rangesBuilder.addText(key.toUpperCase(Locale.ROOT));
                }
            }
        }

        short[] glyphRanges = rangesBuilder.buildRanges();

        // Font config for additional fonts
        final ImFontConfig fontConfig = new ImFontConfig();
        fontConfig.setOversampleH(2);
        fontConfig.setOversampleV(2);

        fontConfig.setName("Inter (Medium), 16px");
        font = fonts.addFontFromMemoryTTF(loadFont("inter-medium.ttf"), size, fontConfig, glyphRanges);

        // Merge in Japanese/Korean/Chinese/etc. characters if needed
        fontConfig.setMergeMode(true);
        if (languageCode.startsWith("he")) {
            short[] hebrewRanges = new short[]{(short)'\u0590', (short)'\u05FF', (short)'\uFB1D', (short)'\uFB4F', 0};
            io.getFonts().addFontFromMemoryTTF(loadFont("heebo-medium.ttf"), size, fontConfig, hebrewRanges);
        } else if (languageCode.startsWith("ja")) {
            io.getFonts().addFontFromMemoryTTF(loadFont("notosansjp-medium.ttf"), size,
                fontConfig, GlyphRanges.getJapaneseRanges());
        } else if (languageCode.startsWith("zh")) {
            io.getFonts().addFontFromMemoryTTF(loadFont("notosanssc-medium.otf"), size,
                fontConfig, GlyphRanges.getChineseFullRanges());
            io.getFonts().addFontFromMemoryTTF(loadFont("notosanstc-medium.otf"), size,
                fontConfig, GlyphRanges.getChineseFullRanges());
        }
        fontConfig.setMergeMode(false);

        fonts.build();
        imguiGl3.updateFontsTexture();

        fontConfig.destroy();
        fonts.clearTexData();
    }

    private static byte[] loadFont(String name) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation("flashback", name));
            if (resource.isEmpty()) throw new MissingResourceException("Missing font: " + name, "Font", "");
            try (InputStream is = resource.get().open()) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Vec3 getMouseForwardsVector() {
        return getMouseForwardsVector(ImGui.getMousePosX(), ImGui.getMousePosY());
    }

    public static Vec3 getMouseForwardsVector(float mouseX, float mouseY) {
        if (!isActive() || (!isFrameHovered && !isMovingCamera()) || lastProjectionMatrix == null || lastViewQuaternion == null) return null;
        return getForwardsVector(mouseX, mouseY);
    }

    public static Vec2 getMouseViewportFraction() {
        float x = (ImGui.getMousePosX() - ImGui.getMainViewport().getPosX() - frameX) / frameWidth;
        float y = (ImGui.getMousePosY() - ImGui.getMainViewport().getPosY() - frameY) / frameHeight;
        return new Vec2(x, y);
    }

    public static Vec2 getMouseViewportFraction(float mouseX, float mouseY) {
        float x = (mouseX - ImGui.getMainViewport().getPosX() - frameX) / frameWidth;
        float y = (mouseY - ImGui.getMainViewport().getPosY() - frameY) / frameHeight;
        return new Vec2(x, y);
    }

    public static Vec3 getForwardsVector(float mouseX, float mouseY) {
        float x = (mouseX - ImGui.getMainViewport().getPosX() - frameX) / frameWidth * 2 - 1;
        float y = (mouseY - ImGui.getMainViewport().getPosY() - frameY) / frameHeight * 2 - 1;
        return getForwardsVectorRaw(x, y);
    }

    public static Vec3 getForwardsVectorRaw(float x, float y) {
        if (!isMovingCamera() && (x < -1 || x > 1 || y < -1 || y > 1)) return null;

        var matrix = new Matrix4f(lastProjectionMatrix);
        matrix.invert();

        // Apply inverse projection matrix to get forwards vector
        var forwards = new Vector4f(x, y, 0f, 1f);
        forwards.mul(matrix);

        // Negate and normalize to match MC coordinate space
        //#if MC>=12100
        return new Vec3(forwards.x(), -forwards.y(), forwards.z()).normalize();
        //#else
        //$$ return new Vec3(-forwards.x(), -forwards.y(), -forwards.z()).normalize();
        //#endif
    }

    public static Vec3 getMouseLookVectorFromForwards(Vec3 forwards) {
        if (forwards == null) return null;

        // Apply the view quaternion to forwards vector to get view vector
        var view = forwards.toVector3f();
        view.rotate(lastViewQuaternion);

        return new Vec3(view.x(), view.y(), view.z()).normalize();
    }

    public static Vec3 getMouseLookVector(float mouseX, float mouseY) {
        return getMouseLookVectorFromForwards(getMouseForwardsVector(mouseX, mouseY));
    }

    public static Vec3 getMouseLookVector() {
        return getMouseLookVectorFromForwards(getMouseForwardsVector());
    }

    public static boolean isMovingCamera() {
        return imguiGlfw.isGrabbed() && imguiGlfw.getMouseHandledBy() == CustomImGuiImplGlfw.MouseHandledBy.GAME;
    }

    public static void setupMainViewport() {
        var window = Minecraft.getInstance().getWindow();

        int frameBottom = (int) ImGui.getMainViewport().getSizeY() - (frameY + frameHeight);
        GlStateManager._viewport(frameX * window.getWidth() / window.getScreenWidth(),
            frameBottom * window.getHeight() / window.getScreenHeight(),
            Math.max(1, frameWidth * window.getWidth() / window.getScreenWidth()),
            Math.max(1, frameHeight * window.getHeight() / window.getScreenHeight()));
    }

    public static float getUiScale() {
        return globalScale * contentScale;
    }

    public static double getNewMouseX(double x) {
        return x - frameX;
    }

    public static double getNewMouseY(double y) {
        return y - frameY;
    }

    public static int getNewGameWidth(float scale) {
        return Math.max(1, Math.round(frameWidth * scale));
    }

    public static int getNewGameHeight(float scale) {
        return Math.max(1, Math.round(frameHeight * scale));
    }

    private static boolean isActiveInternal() {
        if (!FlashbackClient.isInReplay() || !enabled) {
            return false;
        }

        if (FlashbackClient.EXPORT_JOB != null) {
            return false;
        }

        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        if (gameMode == null) return false;
        if (gameMode.getPlayerMode() != GameType.SPECTATOR) return false;
        if (Minecraft.getInstance().level == null) return false;
        if (Minecraft.getInstance().player == null) return false;
        if (Minecraft.getInstance().getOverlay() != null) return false;
        return true;
    }

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    public static void toggleEnabled() {
        enabled = !enabled;
    }

    public static void deferredCloseTextureId(int textureId) {
        deferredCloseLock.lock();
        try {
            deferredCloseTextureIds.add(textureId);
        } finally {
            deferredCloseLock.unlock();
        }
    }

    public static void deferredClose(AutoCloseable autoCloseable) {
        deferredCloseLock.lock();
        try {
            deferredClose.add(autoCloseable);
        } finally {
            deferredCloseLock.unlock();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isActive() {
        return activeLastFrame;
    }

    private static void transitionActiveState(boolean active) {
        if (activeLastFrame == active) return;
        activeLastFrame = active;

        // Recalculate the size of the gameplay window
        Window window = Minecraft.getInstance().getWindow();
        if (window.getWidth() > 0 && window.getWidth() <= 32768 && window.getHeight() > 0 && window.getHeight() <= 32768) {
            Minecraft.getInstance().resizeDisplay();
        }
        imguiGlfw.ungrab();

        if (!activeLastFrame) {
            // Make sure the vanilla grab state is correct
            if (Minecraft.getInstance().gameMode != null) {
                if (Minecraft.getInstance().screen == null) {
                    Minecraft.getInstance().mouseHandler.releaseMouse();
                    Minecraft.getInstance().mouseHandler.grabMouse();
                } else {
                    Minecraft.getInstance().mouseHandler.grabMouse();
                    Minecraft.getInstance().mouseHandler.releaseMouse();
                }
                Minecraft.getInstance().mouseHandler.setIgnoreFirstMove();
            }
        } else {
            // Forcefully ungrab the cursor
            long handle = ImGui.getMainViewport().getPlatformHandle();
            if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
                GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                GLFW.glfwSetCursorPos(handle, ImGui.getMainViewport().getSizeX()/2f, ImGui.getMainViewport().getSizeY()/2f);
            }
        }

        imguiGlfw.setViewportWindowsHidden(!activeLastFrame);
    }

    public static void drawOverlay() {
        int oldFrameX = frameX;
        int oldFrameY = frameY;
        int oldFrameWidth = frameWidth;
        int oldFrameHeight = frameHeight;

        if (!initialized) {
            throw new IllegalStateException("Tried to use EditorUI while it was not initialized");
        }

        deferredCloseLock.lock();
        try {
            for (int id : deferredCloseTextureIds) {
                TextureUtil.releaseTextureId(id);
            }
            deferredCloseTextureIds.clear();

            for (AutoCloseable closeable : deferredClose) {
                try {
                    closeable.close();
                } catch (Exception e) {}
            }
            deferredClose.clear();
        } finally {
            deferredCloseLock.unlock();
        }

        if (Minecraft.getInstance().screen instanceof ProgressScreen || Minecraft.getInstance().screen instanceof ReceivingLevelScreen) {
            return;
        }

        enabled = true;

        if (!isActiveInternal()) {
            transitionActiveState(false);
            if (!FlashbackClient.isInReplay()) {
                enabled = false;
            }
            imguiGlfw.updateReleaseAllKeys(true);
            return;
        } else {
            imguiGlfw.updateReleaseAllKeys(false);
        }

        if (!ImGui.isAnyMouseDown()) {
            newGlobalScale = ((int)(newGlobalScale * 16))/16f;
            if (newGlobalScale < 0.25) newGlobalScale = 0.25f;
            if (newGlobalScale > 4) newGlobalScale = 4f;

            float newContentScale = ((int)(imguiGlfw.contentScale * 16))/16f;
            if (newContentScale < 0.125) newContentScale = 0.125f;
            if (newContentScale > 8) newContentScale = 8f;

            if (globalScale != newGlobalScale || contentScale != newContentScale) {
                int oldFontSize = (int)(16 * getUiScale());
                globalScale = newGlobalScale;
                contentScale = newContentScale;
                if (oldFontSize != (int)(16 * getUiScale())) {
                    // todo:
                    // Axiom.dbg("Resizing EditorUI fonts from: " + oldFontSize + " to: " + (int)(16 * getUiScale()));
                    initFonts(languageCode);
                }
            }
        }

        imguiGlfw.newFrame();
        ImGui.newFrame();

        navClose = ImGui.getIO().getNavInputs(ImGuiNavInput.Cancel) != 0;
        if (wasNavClose != navClose) {
            wasNavClose = navClose;
        } else if (wasNavClose) {
            navClose = false;
        }

        if (ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopup)) {
            ImGui.getIO().addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        } else {
            ImGui.getIO().removeConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        }

        char controlIcon = Minecraft.ON_OSX ? '\u2318' : '\u2303';

        // todo:
        // MainMenuBar.render();
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.menuItem("Hello")) {
                FlashbackClient.EXPORT_JOB = new ExportJob(20, 100);
            }

            ImGui.endMainMenuBar();
        }

        // Setup docking
        ImGui.setNextWindowBgAlpha(0);
        int mainDock = ImGui.dockSpaceOverViewport(ImGui.getMainViewport(), ImGuiDockNodeFlags.NoDockingInCentralNode);
        imgui.internal.ImGui.dockBuilderGetCentralNode(mainDock).addLocalFlags(imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar);

        isFrameHovered = false;
        ImGui.setNextWindowDockID(mainDock);
        ImGuiHelper.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);

        boolean fireCancelNavInput = false;

        if (ImGui.begin("Main", ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoNavInputs | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoCollapse |
                ImGuiWindowFlags.NoSavedSettings)) {
            ImGuiHelper.popStyleVar();

            float minX = ImGui.getWindowContentRegionMinX();
            float maxX = ImGui.getWindowContentRegionMaxX();
            float minY = ImGui.getWindowContentRegionMinY();
            float maxY = ImGui.getWindowContentRegionMaxY();

            if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                frameX = (int) (ImGui.getWindowPosX() - ImGui.getWindowViewport().getPosX() + minX);
                frameY = (int) (ImGui.getWindowPosY() - ImGui.getWindowViewport().getPosY() + minY);
            } else {
                frameX = (int) (ImGui.getWindowPosX() + minX);
                frameY = (int) (ImGui.getWindowPosY() + minY);
            }
            frameWidth = (int) Math.max(1, maxX - minX);
            frameHeight = (int) Math.max(1, maxY - minY);

            if (true) {
                float aspectRatio = ImGui.getMainViewport().getSizeX() / ImGui.getMainViewport().getSizeY();

                float currentAspectRatio = frameWidth / (float) frameHeight;

                if (currentAspectRatio < aspectRatio) {
                    int newHeight = (int)(frameWidth / aspectRatio);
                    frameY += (frameHeight - newHeight)/2;
                    frameHeight = newHeight;
                } else if (currentAspectRatio > aspectRatio) {
                    int newWidth = (int)(frameHeight * aspectRatio);
                    frameX += (frameWidth - newWidth)/2;
                    frameWidth = newWidth;
                }
            }

            if (frameX != oldFrameX || frameY != oldFrameY || frameWidth != oldFrameWidth || frameHeight != oldFrameHeight) {
                Minecraft.getInstance().resizeDisplay();
            }

            if (ImGui.isWindowHovered() && ImGui.getMousePosY() > ImGui.getWindowPosY()) {
                isFrameHovered = true;

                if (Minecraft.getInstance().screen != null) {
                    ImGui.captureMouseFromApp(false);
                } else {
                    if (ImGui.getIO().getWantCaptureMouse() && !isMovingCamera()) {
//                        if (!adjustSpeed) {
//                            handleScroll();
//                        }
                        fireCancelNavInput |= handleBasicInputs();
                    }
                }
            }
        } else {
            ImGuiHelper.popStyleVar();
        }
        ImGui.end();

        // VisualsWindow.render();
        Timeline.render();

        if (ImGui.begin("There")) {
            ImGui.text("Hello!");
        }
        ImGui.end();

        ImGui.render();
        ImGuiHelper.endFrame();

        if (fireCancelNavInput) {
            ImGui.getIO().setNavInputs(ImGuiNavInput.Input, 1.0f);
            ImGui.getIO().setNavInputs(ImGuiNavInput.Cancel, 1.0f);
        }

        long ctx = GLFW.glfwGetCurrentContext();
        ImGui.updatePlatformWindows();
        ImGui.renderPlatformWindowsDefault();
        GLFW.glfwMakeContextCurrent(ctx);

        var drawData = ImGui.getDrawData();
        if (drawData != null) {
            imguiGl3.renderDrawData(drawData);
        }

        transitionActiveState(true);
    }

    public static boolean isMainFrameHovered() {
        return isFrameHovered;
    }

    private static boolean handleBasicInputs() {
//        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && UserAction.LEFT_MOUSE.call(null) == UserAction.ActionResult.USED_STOP) {
//            return true;
//        }

        if (ImGui.isMouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            int key = -GLFW.GLFW_MOUSE_BUTTON_LEFT-1;
            if (key != 0) {
                imguiGlfw.setGrabbed(true, key, frameX + frameWidth / 2f, frameY + frameHeight / 2f);
            }
            return true;
        }

//        if (Keybinds.ADJUST_RADIUS.isPressed(false)) {
//            if (ToolManager.isToolActive() && ToolManager.getCurrentTool().initiateAdjustment()) {
//                int key = Keybinds.ADJUST_RADIUS.getKey();
//                if (key != 0) {
//                    imguiGlfw.setGrabbed(false, key, -1, -1);
//                }
//
//                adjustingTool = true;
//                adjustingToolOffsetX = 0;
//                adjustingToolOffsetY = 0;
//
//                return true;
//            }
//        } else if (Keybinds.ROTATE_CAMERA.isPressed(false)) {
//            movementControls = EditorMovementControls.rotate();
//            return true;
//        } else if (Keybinds.PICK_BLOCK.isPressed(false)) {
//            RayCaster.RaycastResult result = Tool.raycastBlock();
//            if (result != null) {
//                BlockState blockState = Minecraft.getInstance().level.getBlockState(result.blockPos());
//                CustomBlockState customBlockState = ServerCustomBlocks.getCustomStateFor(blockState);
//                activeBlockHistory.setActive(Objects.requireNonNullElse(customBlockState, (CustomBlockState) blockState));
//                return true;
//            }
//        } else if (Keybinds.USE_TOOL.isPressed(false)) {
//            if (UserAction.RIGHT_MOUSE.call(null) != UserAction.ActionResult.NOT_HANDLED || ToolManager.isToolActive()) {
//                return true;
//            }
//        } else if (Keybinds.ARCBALL_CAMERA.isPressed(false)) {
//            Tool currentTool = ToolManager.isToolActive() ? ToolManager.getCurrentTool() : null;
//            if (currentTool instanceof RulerTool || currentTool instanceof ModifyTool || Configuration.keybind.useCenterOfScreenForArcball) {
//                movementControls = EditorMovementControls.arcballFromRaycast();
//                if (movementControls != EditorMovementControls.none()) {
//                    int key = Keybinds.ARCBALL_CAMERA.getKey();
//                    if (key != 0) {
//                        imguiGlfw.setGrabbed(false, key, frameX + frameWidth / 2f, frameY + frameHeight / 2f);
//                    }
//                    return true;
//                }
//            } else {
//                pendingDepthActions.add(PendingDepthAction.ARCBALL);
//                return true;
//            }
//        } else if (Keybinds.PAN_CAMERA.isPressed(false)) {
//            movementControls = EditorMovementControls.pan();
//            return true;
//        }

//        if (Keybinds.CROSSHAIR_CAMERA.isPressed(false)) {
//            int key = Keybinds.CROSSHAIR_CAMERA.getKey();
//            if (key != 0) {
//                imguiGlfw.setGrabbed(true, key, frameX + frameWidth / 2f, frameY + frameHeight / 2f);
//            }
//            return true;
//        }
        return false;
    }

    public static boolean consumeNavClose() {
        boolean navClose = ReplayUI.navClose;
        ReplayUI.navClose = false;
        return navClose;
    }

    public static boolean isMoveQuickDown() {
        return ImGui.isKeyDown(Minecraft.getInstance().options.keySprint.key.getValue());
    }

    public static boolean isCtrlOrCmdDown() {
        return Minecraft.ON_OSX ? ImGui.getIO().getKeySuper() : ImGui.getIO().getKeyCtrl();
    }

}
