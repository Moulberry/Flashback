package com.moulberry.flashback.mixin;

import com.mojang.blaze3d.platform.Window;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ReplayVisuals;
import com.moulberry.flashback.combo_options.Sizing;
import com.moulberry.flashback.editor.ui.ReplayUI;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class MixinWindow {

    @Shadow private int framebufferWidth;
    @Shadow private int framebufferHeight;

    @Shadow private int width;
    @Shadow private int height;

    @Shadow private double guiScale;

    @Shadow private int guiScaledWidth;

    @Shadow private int guiScaledHeight;

    @Shadow
    private int windowedWidth;

    @Shadow
    @Final
    private long window;

    @Inject(method = "getWidth", at=@At("HEAD"), cancellable = true)
    public void getWidth(CallbackInfoReturnable<Integer> cir) {
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.shouldChangeFramebufferSize()) {
            cir.setReturnValue(Flashback.EXPORT_JOB.getWidth());
        } else if (ReplayUI.isActive() && ReplayVisuals.sizing != Sizing.UNDERLAY) {
            cir.setReturnValue(ReplayUI.getNewGameWidth((float) this.framebufferWidth / this.width));
        }
    }

    @Inject(method = "getHeight", at=@At("HEAD"), cancellable = true)
    public void getHeight(CallbackInfoReturnable<Integer> cir) {
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.shouldChangeFramebufferSize()) {
            cir.setReturnValue(Flashback.EXPORT_JOB.getHeight());
        } else if (ReplayUI.isActive() && ReplayVisuals.sizing != Sizing.UNDERLAY) {
            cir.setReturnValue(ReplayUI.getNewGameHeight((float) this.framebufferHeight / this.height));
        }
    }

    @Inject(method = "getScreenWidth", at=@At("HEAD"), cancellable = true)
    public void getScreenWidth(CallbackInfoReturnable<Integer> cir) {
        if (ReplayUI.isActive() && ReplayVisuals.sizing != Sizing.UNDERLAY) {
            cir.setReturnValue(ReplayUI.getNewGameWidth(1));
        }
    }

    @Inject(method = "getScreenHeight", at=@At("HEAD"), cancellable = true)
    public void getScreenHeight(CallbackInfoReturnable<Integer> cir) {
        if (ReplayUI.isActive() && ReplayVisuals.sizing != Sizing.UNDERLAY) {
            cir.setReturnValue(ReplayUI.getNewGameHeight(1));
        }
    }

    @Inject(method = "onFramebufferResize", at=@At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/WindowEventHandler;resizeDisplay()V", shift = At.Shift.BEFORE))
    public void onFramebufferResize(long l, int i, int j, CallbackInfo ci) {
        int[] widthB = new int[1];
        int[] heightB = new int[1];
        GLFW.glfwGetWindowSize(this.window, widthB, heightB);
        if (widthB[0] > 0 && heightB[0] > 0) {
            this.width = widthB[0];
            this.height = heightB[0];
        }
    }

    @Inject(method = "onResize", at=@At("HEAD"), cancellable = true)
    public void onResize(long l, int i, int j, CallbackInfo ci) {
        if (l != this.window) {
            ci.cancel();
            return;
        }

        int[] widthB = new int[1];
        int[] heightB = new int[1];
        GLFW.glfwGetFramebufferSize(this.window, widthB, heightB);
        if (widthB[0] > 0 && heightB[0] > 0) {
            this.framebufferWidth = widthB[0];
            this.framebufferHeight = heightB[0];
        }
    }

    @Inject(method = "calculateScale", at=@At("HEAD"), cancellable = true)
    public void calculateScale(int scale, boolean forceEven, CallbackInfoReturnable<Integer> cir) {
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.shouldChangeFramebufferSize()) {
            int fbw = Flashback.EXPORT_JOB.getWidth();
            int fbh = Flashback.EXPORT_JOB.getHeight();

            int j = 1;
            while (j != scale && j < fbw && j < fbh && fbw / (j + 1) >= 320 && fbh / (j + 1) >= 240) {
                j++;
            }
            if (forceEven && j % 2 != 0) {
                j++;
            }
            cir.setReturnValue(j);
        } else if (ReplayUI.isActive() && ReplayVisuals.sizing != Sizing.UNDERLAY) {
            int fbw = ReplayUI.getNewGameWidth((float) this.framebufferWidth / this.width);
            int fbh = ReplayUI.getNewGameHeight((float) this.framebufferHeight / this.height);

            int j = 1;
            while (j != scale && j < fbw && j < fbh && fbw / (j + 1) >= 320 && fbh / (j + 1) >= 240) {
                j++;
            }
            if (forceEven && j % 2 != 0) {
                j++;
            }
            cir.setReturnValue(j);
        }
    }

    @Inject(method = "setGuiScale", at=@At("HEAD"), cancellable = true)
    public void setGuiScale(double d, CallbackInfo ci) {
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.shouldChangeFramebufferSize()) {
            int fbw = Flashback.EXPORT_JOB.getWidth();
            int fbh = Flashback.EXPORT_JOB.getHeight();

            this.guiScale = d;
            int i = (int)((double)fbw / d);
            this.guiScaledWidth = (double)fbw / d > (double)i ? i + 1 : i;
            int j = (int)((double)fbh / d);
            this.guiScaledHeight = (double)fbh / d > (double)j ? j + 1 : j;

            ci.cancel();
        } else if (ReplayUI.isActive() && ReplayVisuals.sizing != Sizing.UNDERLAY) {
            int fbw = ReplayUI.getNewGameWidth((float) this.framebufferWidth / this.width);
            int fbh = ReplayUI.getNewGameHeight((float) this.framebufferHeight / this.height);

            this.guiScale = d;
            int i = (int)((double)fbw / d);
            this.guiScaledWidth = (double)fbw / d > (double)i ? i + 1 : i;
            int j = (int)((double)fbh / d);
            this.guiScaledHeight = (double)fbh / d > (double)j ? j + 1 : j;

            ci.cancel();
        }
    }

}
