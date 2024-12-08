package com.moulberry.flashback.exporting.taskbar;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.COMInvoker;
import com.sun.jna.platform.win32.W32Errors;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;

public class WindowsTaskbar extends COMInvoker implements ITaskbar {

    private final WinDef.HWND hwnd;
    WindowsTaskbar(Pointer ptr, WinDef.HWND hwnd) {
        this.setPointer(ptr);
        this.hwnd = hwnd;
        this.invokeNative(3); // HrInit
    }

    private void invokeNative(int ventry, Object... objects) {
        Object[] args = new Object[objects.length+1];
        args[0] = this.getPointer();
        System.arraycopy(objects, 0, args, 1, objects.length);
        if (W32Errors.FAILED((WinNT.HRESULT) this._invokeNativeObject(ventry, args, WinNT.HRESULT.class))) {
            throw new IllegalStateException("Failed to invoke vtable: " + ventry);
        }
    }

    @Override
    public void close() {
        this.reset();
        this.invokeNative(2); // Release
    }

    @Override
    public void reset() {
        this.invokeNative(10, this.hwnd, 0); // SetProgressState TBPF_NOPROGRESS (0x00000000)
    }

    @Override
    public void setProgress(long count, long outOf) {
        this.invokeNative(9, this.hwnd, count, outOf); // SetProgressValue
    }

    @Override
    public void setPaused() {
        this.invokeNative(10, this.hwnd, 8); // SetProgressState TBPF_PAUSED (0x00000008)
    }

    @Override
    public void setNormal() {
        this.invokeNative(10, this.hwnd, 2); // SetProgressState TBPF_NORMAL (0x00000002)
    }
}
