package com.moulberry.flashback.editor.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.platform.Window;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.editor.ui.windows.ExportDoneWindow;
import com.moulberry.flashback.editor.ui.windows.ExportQueueWindow;
import com.moulberry.flashback.editor.ui.windows.ExportScreenshotWindow;
import com.moulberry.flashback.editor.ui.windows.MovementWindow;
import com.moulberry.flashback.editor.ui.windows.PlayerListWindow;
import com.moulberry.flashback.editor.ui.windows.PreferencesWindow;
import com.moulberry.flashback.editor.ui.windows.SelectedEntityPopup;
import com.moulberry.flashback.editor.ui.windows.WindowType;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.combo_options.Sizing;
import com.moulberry.flashback.editor.ui.windows.MainMenuBar;
import com.moulberry.flashback.editor.ui.windows.StartExportWindow;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.editor.ui.windows.VisualsWindow;
import imgui.*;
import imgui.flag.*;
import imgui.internal.ImGuiContext;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

public class ReplayUI {

    public static final CustomImGuiImplGlfw imguiGlfw = new CustomImGuiImplGlfw();
    private static final CustomImGuiImplGl3 imguiGl3 = new CustomImGuiImplGl3();
    private static boolean initialized = false;

    private static boolean isFrameFocused = false;
    private static boolean isFrameHovered = false;
    public static int frameX = 0;
    public static int frameY = 0;
    public static int frameWidth = 1;
    public static int frameHeight = 1;
    public static int viewportSizeX = 1;
    public static int viewportSizeY = 1;
    private static boolean activeLastFrame = false;

    public static int focusMainWindowCounter = 0;

    public static boolean hasAnyPopupOpen = false;

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

    private static ImGuiContext imGuiContext = null;
    private static ImGuiIO imGuiIo = null;

    private static boolean popupOpenLastFrame = false;

    private static String infoOverlayText = null;
    private static long infoOverlayEndMillis = 0;

    private static UUID selectedEntity = null;
    private static boolean openSelectedEntityPopup = false;

    private static int displayingTip = -1;
    private static boolean dontShowTipsOnStartupCheckbox = false;

    public static void init() {
        if (initialized) {
            throw new IllegalStateException("ReplayUI initialized twice");
        }
        initialized = true;

        // todo: introduce global scale option
        // newGlobalScale = globalScale = Configuration.internal.globalScale;

        // Initialize config so that everything starts nicely docked

        Path path = Flashback.getConfigDirectory().resolve("imgui.ini");
        if (!Files.exists(path)) {
            try {
                Files.writeString(path, ReplayUIDefaults.LAYOUT);
            } catch(IOException ignored) {}
        } else {
            try {
                String imguiIni = Files.readString(path);
                if (imguiIni.contains("[Window][Timeline]")) {
                    imguiIni = imguiIni.replace("[Window][Timeline]", "[Window][###Timeline]");
                    Files.writeString(path, imguiIni);
                }
            } catch (IOException ignored) {}
        }

        long oldImGuiContext = ImGui.getCurrentContext().ptr;
        imGuiContext = new ImGuiContext(ImGui.createContext().ptr);
        ImGui.setCurrentContext(imGuiContext);

        Path relativePath = FabricLoader.getInstance().getGameDir().relativize(path);
        ImGui.getIO().setIniFilename(relativePath.toString());

        // ImGui.getIO().addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        ImGui.getIO().addConfigFlags(ImGuiConfigFlags.DockingEnable);
        // ImGui.getIO().addConfigFlags(ImGuiConfigFlags.ViewportsEnable);
        ImGui.getIO().setConfigMacOSXBehaviors(Minecraft.ON_OSX);

        imguiGlfw.init(Minecraft.getInstance().getWindow().getWindow(), true, new ImGuiIO(ImGui.getIO().ptr));
        imguiGl3.init("#version 150");

        contentScale = imguiGlfw.contentScale;
        initFonts(languageCode);

        ReplayUIDefaults.applyStyle(ImGui.getStyle());

        ImGuiContext currentContext = ImGui.getCurrentContext();
        currentContext.ptr = oldImGuiContext;
        ImGui.setCurrentContext(currentContext);
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
        fontConfig.setGlyphOffset(0, 0);
        font = fonts.addFontFromMemoryTTF(loadFont("inter-medium.ttf"), size, fontConfig, glyphRanges);

        // Merge in Japanese/Korean/Chinese/etc. characters if needed
        fontConfig.setMergeMode(true);

        fontConfig.setGlyphOffset(0, (int)(5 * getUiScale()));
        io.getFonts().addFontFromMemoryTTF(loadFont("materialiconsround-regular.otf"), (int)(20 * getUiScale()), fontConfig, buildMaterialIconRanges());
        fontConfig.setGlyphOffset(0, 0);

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

    private static short[] buildMaterialIconRanges() {
        ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
        builder.addChar('\ue04b');
        builder.addChar('\ue577');
        builder.addChar('\uefeb');
        builder.addChar('\ue3af');
        builder.addChar('\ue9e4');
        builder.addChar('\ue422');
        builder.addChar('\ue518');
        builder.addChar('\ue945');
        builder.addChar('\ue8f4');
        builder.addChar('\ue8f5');
        builder.addChar('\ue148');
        builder.addChar('\ue5d2');
        builder.addChar('\ue872');
        builder.addChar('\ue3c9');
        builder.addChar('\ue40a');
        builder.addChar('\ue92b');
        builder.addChar('\ueb3b');
        builder.addChar('\ue14a');
        builder.addChar('\ue55f');
        return builder.buildRanges();
    }

    private static byte[] loadFont(String name) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(Flashback.createResourceLocation(name));
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
        return new Vec3(forwards.x(), -forwards.y(), forwards.z()).normalize();
    }

    @Nullable
    public static Vec3 getMouseLookVectorFromForwards(Vec3 forwards) {
        if (forwards == null) return null;

        // Apply the view quaternion to forwards vector to get view vector
        var view = forwards.toVector3f();
        view.rotate(lastViewQuaternion);

        return new Vec3(view.x(), view.y(), view.z()).normalize();
    }

    @Nullable
    public static Vec3 getMouseLookVector(float mouseX, float mouseY) {
        return getMouseLookVectorFromForwards(getMouseForwardsVector(mouseX, mouseY));
    }

    @Nullable
    public static Vec3 getMouseLookVector() {
        return getMouseLookVectorFromForwards(getMouseForwardsVector());
    }

    public static boolean isEntitySelected(UUID uuid) {
        return isActive() && uuid.equals(selectedEntity);
    }

    public static boolean isMovingCamera() {
        return imguiGlfw.isGrabbed() && imguiGlfw.getMouseHandledBy() == CustomImGuiImplGlfw.MouseHandledBy.GAME;
    }

    public static void setInfoOverlay(String text) {
        infoOverlayText = text;
        infoOverlayEndMillis = System.currentTimeMillis() + 5000;
    }

    public synchronized static void setInfoOverlayShort(String text) {
        infoOverlayText = text;
        infoOverlayEndMillis = System.currentTimeMillis() + 1000;
    }

    public static void setupMainViewport() {
        var window = Minecraft.getInstance().getWindow();

        int frameBottom = window.height - (frameY + frameHeight);
        GlStateManager._viewport(frameX * window.getWidth() / window.getScreenWidth(),
            frameBottom * window.getHeight() / window.getScreenHeight(),
            Math.max(1, frameWidth * window.getWidth() / window.getScreenWidth()),
            Math.max(1, frameHeight * window.getHeight() / window.getScreenHeight()));
    }

    public static float getUiScale() {
        return globalScale * contentScale;
    }

    public static int scaleUi(int v) {
        return (int)(v * getUiScale());
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
        if (!Flashback.isInReplay()) {
            return false;
        }

        if (Flashback.EXPORT_JOB != null) {
            return false;
        }

        if (Minecraft.getInstance().options.hideGui) {
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

    public static boolean isActive() {
        return activeLastFrame;
    }

    public static boolean shouldModifyViewport() {
        EditorState editorState = EditorStateManager.getCurrent();
        return isActive() && editorState != null && editorState.replayVisuals.sizing != Sizing.UNDERLAY;
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
        long oldImGuiContext = ImGui.getCurrentContext().ptr;
        ImGui.setCurrentContext(imGuiContext);

        try {
            drawOverlayInternal();
        } finally {
            ImGuiContext currentContext = ImGui.getCurrentContext();
            currentContext.ptr = oldImGuiContext;
            ImGui.setCurrentContext(currentContext);
        }
    }

    public static void drawOverlayInternal() {
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

        if (!isActiveInternal()) {
            transitionActiveState(false);
            imguiGlfw.updateReleaseAllKeys(true);
            focusMainWindowCounter = 5;
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
                    initFonts(languageCode);
                }
            }
        }

        imguiGlfw.newFrame();
        ImGui.newFrame();

        hasAnyPopupOpen = ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopup);

        navClose = hasAnyPopupOpen && ImGui.getIO().getNavInputs(ImGuiNavInput.Cancel) != 0.0f;
        if (wasNavClose != navClose) {
            wasNavClose = navClose;
        } else if (wasNavClose) {
            navClose = false;
        }

        if (hasAnyPopupOpen) {
            ImGui.getIO().addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        } else {
            ImGui.getIO().removeConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        }

        MainMenuBar.render();

        // The main menu bar can disconnect us, so make sure to check if the UI should still be active here
        if (!isActiveInternal()) {
            ImGui.render();
            ImGuiHelper.endFrame();

            transitionActiveState(false);
            imguiGlfw.updateReleaseAllKeys(true);
            focusMainWindowCounter = 5;
            return;
        }

        // Setup docking
        ImGui.setNextWindowBgAlpha(0);
        int mainDock = ImGui.dockSpaceOverViewport(ImGui.getMainViewport(), ImGuiDockNodeFlags.NoDockingInCentralNode);
        imgui.internal.ImGui.dockBuilderGetCentralNode(mainDock).addLocalFlags(imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar);

        isFrameFocused = false;
        isFrameHovered = false;
        ImGui.setNextWindowDockID(mainDock);
        ImGuiHelper.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);

        boolean fireCancelNavInput = false;

        if (ImGui.begin("Main", ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoNavInputs | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoCollapse |
                ImGuiWindowFlags.NoSavedSettings)) {
            if (focusMainWindowCounter > 0) {
                focusMainWindowCounter -= 1;
                ImGui.setWindowFocus();
            }

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
            viewportSizeX = (int) ImGui.getMainViewport().getSizeX();
            viewportSizeY = (int) ImGui.getMainViewport().getSizeY();

            EditorState editorState = EditorStateManager.getCurrent();
            Sizing sizing = editorState == null ? Sizing.KEEP_ASPECT_RATIO : editorState.replayVisuals.sizing;

            if (sizing == Sizing.KEEP_ASPECT_RATIO || sizing == Sizing.CHANGE_ASPECT_RATIO) {
                float aspectRatio;
                if (sizing == Sizing.KEEP_ASPECT_RATIO) {
                    aspectRatio = ImGui.getMainViewport().getSizeX() / ImGui.getMainViewport().getSizeY();
                } else {
                    aspectRatio = editorState.replayVisuals.changeAspectRatio.aspectRatio();
                }

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

            if (ImGui.isWindowFocused()) {
                ImGui.getWindowDrawList().addRect(frameX+1, frameY+1, frameX+frameWidth-1, frameY+frameHeight-1, 0xFFC25823);
                isFrameFocused = true;
            }

            String showText = null;
            int showTextBorderColour = -1;

            if (infoOverlayText != null) {
                long currentMillis = System.currentTimeMillis();
                if (currentMillis < infoOverlayEndMillis - 30000 || currentMillis > infoOverlayEndMillis) {
                    infoOverlayText = null;
                    infoOverlayEndMillis = 0;
                } else {
                    showText = infoOverlayText;
                    showTextBorderColour = 0xFF00FFFF;
                }
            }

            if (showText != null) {
                ImGui.setNextWindowPos(frameX + frameWidth*0.5f, frameY + frameHeight*0.75f, ImGuiCond.Always, 0.5f, 0.5f);
                ImGui.setNextWindowSizeConstraints(0, 0, 550, frameHeight*0.45f);

                ImGuiHelper.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 2);
                ImGuiHelper.pushStyleColor(ImGuiCol.Border, showTextBorderColour);

                if (ImGui.begin("##InfoOverlay", ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoFocusOnAppearing |
                    ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse |
                    ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoSavedSettings)) {

                    ImGui.text(showText);
                }
                ImGui.end();

                ImGuiHelper.popStyleColor();
                ImGuiHelper.popStyleVar();
            }

            if (sizing == Sizing.UNDERLAY) {
                frameX = 0;
                frameY = 0;
                frameWidth = Minecraft.getInstance().getWindow().getScreenWidth();
                frameHeight = Minecraft.getInstance().getWindow().getScreenHeight();
            }

            if (Minecraft.getInstance().screen == null && Minecraft.getInstance().getOverlay() == null) {
                if (editorState != null && editorState.replayVisuals.ruleOfThirdsGuide) {
                    ImDrawList drawList = ImGui.getBackgroundDrawList();
                    drawList.removeImDrawListFlags(ImDrawListFlags.AntiAliasedLines);
                    drawList.addLine(frameX + frameWidth/3, frameY, frameX + frameWidth/3, frameY+frameHeight, 0xAAFFFFFF, 2);
                    drawList.addLine(frameX + frameWidth*2/3, frameY, frameX + frameWidth*2/3, frameY+frameHeight, 0xAAFFFFFF, 2);
                    drawList.addLine(frameX, frameY + frameHeight/3, frameX + frameWidth, frameY + frameHeight/3, 0xAAFFFFFF, 2);
                    drawList.addLine(frameX, frameY + frameHeight*2/3, frameX + frameWidth, frameY + frameHeight*2/3, 0xAAFFFFFF, 2);
                    drawList.addImDrawListFlags(ImDrawListFlags.AntiAliasedLines);
                }

                if (editorState != null && editorState.replayVisuals.centerGuide) {
                    ImDrawList drawList = ImGui.getBackgroundDrawList();
                    drawList.removeImDrawListFlags(ImDrawListFlags.AntiAliasedLines);
                    drawList.addLine(frameX + frameWidth/2, frameY, frameX + frameWidth/2, frameY+frameHeight, 0xAAFFFFFF, 2);
                    drawList.addLine(frameX, frameY + frameHeight/2, frameX + frameWidth, frameY + frameHeight/2, 0xAAFFFFFF, 2);
                    drawList.addImDrawListFlags(ImDrawListFlags.AntiAliasedLines);
                }
            }

            if (displayingTip == -1) {
                FlashbackConfig config = Flashback.getConfig();
                if (!config.showTipOfTheDay) {
                    displayingTip = 0;
                } else {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime >= config.nextTipOfTheDay - TimeUnit.DAYS.toMillis(2) && currentTime <= config.nextTipOfTheDay) {
                        displayingTip = 0;
                    } else {
                        displayingTip = Integer.numberOfTrailingZeros(~config.viewedTipsOfTheDay) + 1;
                        config.viewedTipsOfTheDay |= 1 << (displayingTip - 1);
                        config.nextTipOfTheDay = currentTime + TimeUnit.DAYS.toMillis(1);
                        config.delayedSaveToDefaultFolder();
                    }
                }
            } else if (displayingTip > 0) {
                int tip = displayingTip - 1;
                if (tip >= DailyTips.TIPS.length) {
                    displayingTip = 0;
                } else {
                    ImGuiViewport viewport = ImGui.getMainViewport();
                    ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing, 0.5f, 0.5f);
                    if (ImGui.begin("Tip of the day", ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.AlwaysAutoResize)) {
                        float oldPositionY = ImGui.getCursorPosY();

                        ImGui.pushTextWrapPos(scaleUi(375));
                        ImGui.text(DailyTips.TIPS[tip]);
                        ImGui.popTextWrapPos();

                        float verticalSize = ImGui.getCursorPosY() - oldPositionY;
                        int minimumVerticalSize = scaleUi(100);
                        if (verticalSize < minimumVerticalSize) {
                            ImGui.setCursorPosY(ImGui.getCursorPosY() + minimumVerticalSize - verticalSize);
                        }

                        if (ImGui.checkbox("Don't show tips on startup", dontShowTipsOnStartupCheckbox)) {
                            dontShowTipsOnStartupCheckbox = !dontShowTipsOnStartupCheckbox;
                        }
                        ImGui.sameLine();
                        ImGui.dummy(scaleUi(20), 0);
                        ImGui.sameLine();
                        if (ImGui.button("Close")) {
                            if (dontShowTipsOnStartupCheckbox) {
                                FlashbackConfig config = Flashback.getConfig();
                                config.showTipOfTheDay = false;
                                config.delayedSaveToDefaultFolder();
                            }
                            displayingTip = 0;
                        }
                        ImGui.sameLine();
                        boolean canShowPrev = displayingTip > 1;
                        if (!canShowPrev) ImGui.beginDisabled();
                        if (ImGui.button("Back") && canShowPrev) {
                            FlashbackConfig config = Flashback.getConfig();
                            displayingTip -= 1;
                            config.viewedTipsOfTheDay |= 1 << (displayingTip - 1);
                            config.delayedSaveToDefaultFolder();
                        }
                        if (!canShowPrev) ImGui.endDisabled();
                        ImGui.sameLine();
                        boolean canShowNext = displayingTip < DailyTips.TIPS.length;
                        if (!canShowNext) ImGui.beginDisabled();
                        if (ImGui.button("Next") && canShowNext) {
                            FlashbackConfig config = Flashback.getConfig();
                            displayingTip += 1;
                            config.viewedTipsOfTheDay |= 1 << (displayingTip - 1);
                            config.delayedSaveToDefaultFolder();
                        }
                        if (!canShowNext) ImGui.endDisabled();
                    }
                    ImGui.end();
                }
            }

            if (selectedEntity != null) {
                Entity entity = Minecraft.getInstance().level.getEntities().get(selectedEntity);
                if (entity == null || editorState == null) {
                    selectedEntity = null;
                } else if (entity instanceof Player && !editorState.replayVisuals.renderPlayers) {
                    selectedEntity = null;
                } else if (!(entity instanceof Player) && !editorState.replayVisuals.renderEntities) {
                    selectedEntity = null;
                } else {
                    if (openSelectedEntityPopup) {
                        ImGui.openPopup("###EntityPopup");
                        SelectedEntityPopup.open(entity, editorState);
                    }

                    if (ImGuiHelper.beginPopup("###EntityPopup")) {
                        SelectedEntityPopup.render(entity, editorState);
                        ImGui.endPopup();
                    }

                    if (!ImGui.isPopupOpen("###EntityPopup")) {
                        selectedEntity = null;
                    }
                }
            }

            openSelectedEntityPopup = false;

            if (ImGui.isWindowHovered() && ImGui.getMousePosY() > ImGui.getWindowPosY()) {
                isFrameHovered = true;

                if (Minecraft.getInstance().screen != null) {
                    ImGui.captureMouseFromApp(false);
                } else {
                    boolean isMovingCamera = isMovingCamera();
                    if (isFrameFocused || isMovingCamera) {
                        LocalPlayer player = Minecraft.getInstance().player;
                        if (player != null) {
                            int wheelY = (int) Math.signum(ImGui.getIO().getMouseWheel());
                            if (wheelY != 0) {
                                final float defaultFlyingSpeed = 0.05f;
                                float flyingSpeed = Mth.clamp(player.getAbilities().getFlyingSpeed() + (float)wheelY * defaultFlyingSpeed / 10f,
                                    defaultFlyingSpeed / 10f, defaultFlyingSpeed * 10.0f);
                                setInfoOverlay(String.format("Flying Speed: %.1f", flyingSpeed / defaultFlyingSpeed));
                                player.getAbilities().setFlyingSpeed(flyingSpeed);
                            }
                        }
                    }
                    if (!isMovingCamera && ImGui.getIO().getWantCaptureMouse() && !popupOpenLastFrame && !ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopup)) {
                        fireCancelNavInput |= handleBasicInputs();
                    }
                }
            }
        } else {
            ImGuiHelper.popStyleVar();
        }
        ImGui.end();

        VisualsWindow.render();
        TimelineWindow.render();
        StartExportWindow.render();
        ExportScreenshotWindow.render();
        PreferencesWindow.render();
        ExportQueueWindow.render();

        WindowType.renderAll();

        ExportDoneWindow.render();

        popupOpenLastFrame = ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopup);

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

        if (frameX != oldFrameX || frameY != oldFrameY || frameWidth != oldFrameWidth || frameHeight != oldFrameHeight) {
            Minecraft.getInstance().resizeDisplay();
        }

        transitionActiveState(true);
    }

    public static boolean isMainFrameActive() {
        return isFrameFocused;
    }

    private static boolean handleBasicInputs() {
        if (ImGui.isMouseClicked(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            HitResult result = getLookTarget();
            if (result instanceof EntityHitResult entityHitResult) {
                if (Minecraft.getInstance().player == Minecraft.getInstance().cameraEntity) {
                    Minecraft.getInstance().player.setDeltaMovement(Vec3.ZERO);
                }
                selectedEntity = entityHitResult.getEntity().getUUID();
                openSelectedEntityPopup = true;
            }
            return true;
        }

        if (ImGui.isMouseClicked(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            int key = -GLFW.GLFW_MOUSE_BUTTON_LEFT-1;
            if (key != 0) {
                imguiGlfw.setGrabbed(true, key, true, frameX + frameWidth / 2f, frameY + frameHeight / 2f);
            }
            return true;
        }

        return false;
    }

    @Nullable
    private static HitResult getLookTarget() {
        Vec3 look = getMouseLookVector();
        if (look == null) {
            return null;
        }

        Entity cameraEntity = Minecraft.getInstance().cameraEntity;
        if (cameraEntity == null) {
            return null;
        }

        float distance = 64f;

        Vec3 from = cameraEntity.getEyePosition();
        Vec3 to = from.add(look.scale(distance));

        BlockHitResult blockResult = null;
        EntityHitResult entityResult = null;

        EditorState editorState = EditorStateManager.getCurrent();

        if (editorState == null || editorState.replayVisuals.renderBlocks) {
            ClipContext clipContext = new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty());
            blockResult = Minecraft.getInstance().level.clip(clipContext);

            if (blockResult.getType() != HitResult.Type.MISS) {
                distance = (float) blockResult.getLocation().distanceTo(from);
            }
        }

        if (editorState == null || editorState.replayVisuals.renderEntities || editorState.replayVisuals.renderPlayers) {
            AABB boundingBox = new AABB(from.subtract(0.5f, 0.5f, 0.5f), from.add(0.5f, 0.5f, 0.5f));
            boundingBox = boundingBox.expandTowards(look.scale(distance));

            Predicate<Entity> predicate;
            if (editorState.replayVisuals.renderEntities && editorState.replayVisuals.renderPlayers) {
                predicate = entity -> true;
            } else if (editorState.replayVisuals.renderPlayers) {
                predicate = entity -> entity instanceof Player;
            } else if (editorState.replayVisuals.renderEntities) {
                predicate = entity -> !(entity instanceof Player);
            } else {
                throw new IllegalStateException();
            }

            entityResult = ProjectileUtil.getEntityHitResult(cameraEntity, from, from.add(look.scale(distance)),
                    boundingBox, predicate, distance*distance);
        }

        if (entityResult != null && entityResult.getLocation().distanceTo(from) < distance) {
            return entityResult;
        } else if (blockResult != null && blockResult.getType() != HitResult.Type.MISS) {
            return blockResult;
        } else {
            return null;
        }
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
