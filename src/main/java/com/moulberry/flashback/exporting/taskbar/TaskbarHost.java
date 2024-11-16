package com.moulberry.flashback.exporting.taskbar;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.PointerByReference;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWNativeWin32;

import static com.moulberry.flashback.editor.ui.CustomImGuiImplGlfw.IS_WINDOWS;

public class TaskbarHost {
    public static ITaskbar createTaskbar() {
        if (IS_WINDOWS) {
            return createWindowsInterface();
        } else {
            return new NoopTaskbar();
        }
    }

    private static WindowsTaskbar createWindowsInterface() {
        var itaskbar3res = new PointerByReference();

        if (W32Errors.FAILED(Ole32.INSTANCE.CoCreateInstance(new Guid.GUID("56FDF344-FD6D-11d0-958A-006097C9A090"),
                null,
                WTypes.CLSCTX_SERVER,
                new Guid.GUID("EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF"),
                itaskbar3res))) {
            throw new IllegalStateException("Failed to create ITaskbar3");
        }


        var hwnd = new WinDef.HWND(new Pointer(GLFWNativeWin32.glfwGetWin32Window(Minecraft.getInstance().getWindow().getWindow())));
        return new WindowsTaskbar(itaskbar3res.getValue(), hwnd);
    }
}
