package com.moulberry.flashback.mixin.ui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackTextComponents;
import com.moulberry.flashback.screen.BottomTextWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public class MixinPauseScreen {

    @Inject(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V", shift = At.Shift.BEFORE))
    public void createPauseMenu(CallbackInfo ci, @Local GridLayout.RowHelper rowHelper) {
        if (!Flashback.isInReplay() && !Flashback.getConfig().hidePauseMenuControls) {
            rowHelper.addChild(new BottomTextWidget(204, 30, FlashbackTextComponents.FLASHBACK, Minecraft.getInstance().font), 2);
            if (Flashback.RECORDER == null) {
                rowHelper.addChild(Button.builder(Component.translatable("flashback.recording_controls.start"), (button) -> {
                    Flashback.startRecordingReplay();
                    Minecraft.getInstance().setScreen(null);
                }).width(204).build(), 2);
            } else {
                rowHelper.addChild(Button.builder(Component.translatable("flashback.recording_controls.finish"), (button) -> {
                    Flashback.finishRecordingReplay();
                    Minecraft.getInstance().setScreen(null);
                }).width(204).build(), 2);

                if (Flashback.RECORDER.isPaused()) {
                    rowHelper.addChild(Button.builder(Component.translatable("flashback.recording_controls.unpause"), (button) -> {
                        Flashback.pauseRecordingReplay(false);
                        Minecraft.getInstance().setScreen(null);
                    }).width(98).build());
                } else {
                    rowHelper.addChild(Button.builder(Component.translatable("flashback.recording_controls.pause"), (button) -> {
                        Flashback.pauseRecordingReplay(true);
                        Minecraft.getInstance().setScreen(null);
                    }).width(98).build());
                }

                rowHelper.addChild(Button.builder(Component.translatable("flashback.recording_controls.cancel"), (button) -> {
                    Minecraft.getInstance().setScreen(new ConfirmScreen(value -> {
                        if (value) {
                            Flashback.cancelRecordingReplay();
                            Minecraft.getInstance().setScreen(null);
                        } else {
                            Minecraft.getInstance().setScreen(new PauseScreen(true));
                        }
                    }, Component.translatable("flashback.confirm_cancel_recording"), Component.translatable("flashback.confirm_cancel_recording_description")));
                }).width(98).build());
            }
        }
    }

    @WrapOperation(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/FrameLayout;alignInRectangle(Lnet/minecraft/client/gui/layouts/LayoutElement;IIIIFF)V"))
    public void createPauseMenu_alignInRectangle(LayoutElement layoutElement, int i, int j, int k, int l, float f, float g, Operation<Void> original) {
        if (!Flashback.isInReplay() && !Flashback.getConfig().hidePauseMenuControls) {
            g += 0.05f;
        }
        original.call(layoutElement, i, j, k, l, f, g);
    }

}
