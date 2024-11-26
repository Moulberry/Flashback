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
        try {
            this.invokeNative(3);//HrInit
        } finally {
            this.invokeNative(2);//Release
        }
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
    public void setProgress(long count, long outOf) {
        this.invokeNative(9, this.hwnd, count, outOf);//SetProgressValue
    }

    @Override
    public void close() {
        this.invokeNative(10, this.hwnd, 0);//SetProgressState TBPF_NOPROGRESS
        this.invokeNative(2);//Release
    }
}
