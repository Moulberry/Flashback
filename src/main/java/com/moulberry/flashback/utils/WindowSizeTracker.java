package com.moulberry.flashback.utils;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

public class WindowSizeTracker {

    /**
     * Some mods (e.g. qdaa) like to manipulate the framebuffer width
     * This can cause rendering issues since we don't know the real width
     * This helper will cache and calculate the real framebuffer width to avoid this issue
     */

    private static int lastFramebufferWidth;
    private static int lastFramebufferHeight;
    private static int realFramebufferWidth;
    private static int realFramebufferHeight;

    public static int getWidth(Window window) {
        if (lastFramebufferWidth != window.framebufferWidth) {
            recalculate(window);
        }

        return realFramebufferWidth;
    }


    public static int getHeight(Window window) {
        if (lastFramebufferHeight != window.framebufferHeight) {
            recalculate(window);
        }

        return realFramebufferHeight;
    }

    private static void recalculate(Window window) {
        // Calculate real framebuffer width/height
        var size = window.queryFramebufferSize();
        realFramebufferWidth = Math.max(1, size.width());
        realFramebufferHeight = Math.max(1, size.height());

        // Update cached values
        lastFramebufferWidth = window.framebufferWidth;
        lastFramebufferHeight = window.framebufferHeight;
    }

    public static void getFramebufferSizeRaw(long handle, int[] width, int[] height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuf = stack.callocInt(1);
            IntBuffer heightBuf = stack.callocInt(1);
            if (!SDLVideo.SDL_GetWindowSizeInPixels(handle, widthBuf, heightBuf)) {
                throw new RuntimeException(SDLError.SDL_GetError());
            }
            width[0] = widthBuf.get();
            height[0] = heightBuf.get();
        }
    }

    public static void getWindowSizeRaw(long handle, int[] width, int[] height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuf = stack.callocInt(1);
            IntBuffer heightBuf = stack.callocInt(1);
            if (!SDLVideo.SDL_GetWindowSize(handle, widthBuf, heightBuf)) {
                throw new RuntimeException(SDLError.SDL_GetError());
            }
            width[0] = widthBuf.get();
            height[0] = heightBuf.get();
        }
    }

}
