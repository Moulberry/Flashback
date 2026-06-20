package com.moulberry.flashback.mixin;

import com.mojang.blaze3d.platform.Window;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.WindowExt;
import com.moulberry.flashback.editor.ui.ReplayUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class MixinWindow implements WindowExt {

    // Window fields became methods in 26.2
    @Shadow public abstract int getWidth();
    @Shadow public abstract int getHeight();
    @Shadow public abstract void setWidth(int width);
    @Shadow public abstract void setHeight(int height);
    @Shadow public abstract int getScreenWidth();
    @Shadow public abstract int getScreenHeight();
    @Shadow public abstract long handle();

    @Unique
    private int lastModifiedFramebufferWidth = -1;
    @Unique
    private int lastModifiedFramebufferHeight = -1;

    // Guard against recursive getWidth/getHeight calls during injection
    @Unique
    private boolean flashback$inGetWidth = false;
    @Unique
    private boolean flashback$inGetHeight = false;

    @Unique
    private float calculateWidthScaleFactor() {
        // When called during getWidth injection, use screen width as fallback
        if (flashback$inGetWidth) return 1.0f;
        return Math.max(1/8f, Math.min(8f, (float) this.getWidth() / this.getScreenWidth()));
    }

    @Unique
    private float calculateHeightScaleFactor() {
        if (flashback$inGetHeight) return 1.0f;
        return Math.max(1/8f, Math.min(8f, (float) this.getHeight() / this.getScreenHeight()));
    }

    @Unique
    private int calculateNewFramebufferWidth() {
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.shouldChangeFramebufferSize()) {
            return Flashback.EXPORT_JOB.getWidth();
        } else if (ReplayUI.shouldModifyViewport()) {
            return ReplayUI.getNewGameWidth(this.calculateWidthScaleFactor());
        } else {
            return -1;
        }
    }

    @Unique
    private int calculateNewFramebufferHeight() {
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.shouldChangeFramebufferSize()) {
            return Flashback.EXPORT_JOB.getHeight();
        } else if (ReplayUI.shouldModifyViewport()) {
            return ReplayUI.getNewGameHeight(this.calculateHeightScaleFactor());
        } else {
            return -1;
        }
    }

    @Override
    public void flashback$checkForOverrideResize() {
        int overrideWidth = calculateNewFramebufferWidth();
        int overrideHeight = calculateNewFramebufferHeight();
        if (this.lastModifiedFramebufferWidth != overrideWidth || this.lastModifiedFramebufferHeight != overrideHeight) {
            this.lastModifiedFramebufferWidth = overrideWidth;
            this.lastModifiedFramebufferHeight = overrideHeight;
            if (overrideWidth != -1) this.setWidth(overrideWidth);
            if (overrideHeight != -1) this.setHeight(overrideHeight);
        }
    }

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    public void getWidth(CallbackInfoReturnable<Integer> cir) {
        if (flashback$inGetWidth) return;
        flashback$inGetWidth = true;
        try {
            int width = calculateNewFramebufferWidth();
            if (width != -1) {
                cir.setReturnValue(width);
            }
        } finally {
            flashback$inGetWidth = false;
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    public void getHeight(CallbackInfoReturnable<Integer> cir) {
        if (flashback$inGetHeight) return;
        flashback$inGetHeight = true;
        try {
            int height = calculateNewFramebufferHeight();
            if (height != -1) {
                cir.setReturnValue(height);
            }
        } finally {
            flashback$inGetHeight = false;
        }
    }

    @Inject(method = "getScreenWidth", at = @At("HEAD"), cancellable = true)
    public void getScreenWidth(CallbackInfoReturnable<Integer> cir) {
        if (ReplayUI.shouldModifyViewport()) {
            cir.setReturnValue(ReplayUI.getNewGameWidth(1));
        }
    }

    @Inject(method = "getScreenHeight", at = @At("HEAD"), cancellable = true)
    public void getScreenHeight(CallbackInfoReturnable<Integer> cir) {
        if (ReplayUI.shouldModifyViewport()) {
            cir.setReturnValue(ReplayUI.getNewGameHeight(1));
        }
    }

    @Inject(method = "onResize", at = @At("HEAD"), cancellable = true)
    public void onResize(long l, int i, int j, CallbackInfo ci) {
        if (l != this.handle()) {
            ci.cancel();
        }
    }

    @Inject(method = "calculateScale", at = @At("HEAD"), cancellable = true)
    public void calculateScale(int scale, boolean forceEven, CallbackInfoReturnable<Integer> cir) {
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.shouldChangeFramebufferSize()) {
            int fbw = Flashback.EXPORT_JOB.getWidth();
            int fbh = Flashback.EXPORT_JOB.getHeight();
            int j = 1;
            while (j != scale && j < fbw && j < fbh && fbw / (j + 1) >= 320 && fbh / (j + 1) >= 240) { j++; }
            if (forceEven && j % 2 != 0) { j++; }
            cir.setReturnValue(j);
        }
    }
}
