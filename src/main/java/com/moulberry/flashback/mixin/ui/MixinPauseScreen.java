package com.moulberry.flashback.mixin.ui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackTextComponents;
import com.moulberry.flashback.combo_options.RecordingControlsLocation;
import com.moulberry.flashback.screen.BottomTextWidget;
import com.moulberry.flashback.screen.FlashbackButton;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.IntSupplier;

@Mixin(PauseScreen.class)
public abstract class MixinPauseScreen extends Screen {

    protected MixinPauseScreen(Component component) {
        super(component);
    }

    @WrapOperation(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V"))
    public void createPauseMenuBelow(GridLayout instance, Operation<Void> original, @Local GridLayout.RowHelper rowHelper) {
        var controls = Flashback.getConfig().recordingControls.controlsLocation;
        if (!Flashback.isInReplay() && controls == RecordingControlsLocation.BELOW) {
            rowHelper.addChild(new BottomTextWidget(204, 30, FlashbackTextComponents.FLASHBACK, Minecraft.getInstance().font), 2);
            if (Flashback.RECORDER == null) {
                rowHelper.addChild(Button.builder(Component.translatable("flashback.recording_controls.start"), btn -> {
                    Flashback.startRecordingReplay();
                    Minecraft.getInstance().setScreen(null);
                }).width(204).build(), 2);
            } else {
                rowHelper.addChild(Button.builder(Component.translatable("flashback.recording_controls.finish"), btn -> {
                    Flashback.finishRecordingReplay();
                    Minecraft.getInstance().setScreen(null);
                }).width(204).build(), 2);

                if (Flashback.RECORDER.isPaused()) {
                    rowHelper.addChild(Button.builder(Component.translatable("flashback.recording_controls.unpause"), btn -> {
                        Flashback.pauseRecordingReplay(false);
                        Minecraft.getInstance().setScreen(null);
                    }).width(98).build());
                } else {
                    rowHelper.addChild(Button.builder(Component.translatable("flashback.recording_controls.pause"), btn -> {
                        Flashback.pauseRecordingReplay(true);
                        Minecraft.getInstance().setScreen(null);
                    }).width(98).build());
                }

                rowHelper.addChild(Button.builder(Component.translatable("flashback.recording_controls.cancel"), btn -> {
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

        original.call(instance);
    }

    @Inject(method = "createPauseMenu", at = @At(value = "RETURN"))
    public void createPauseMenuSide(CallbackInfo ci) {
        var controls = Flashback.getConfig().recordingControls.controlsLocation;
        if (Flashback.isInReplay() || (controls != RecordingControlsLocation.RIGHT && controls != RecordingControlsLocation.LEFT)) {
            return;
        }

        boolean useRight = controls == RecordingControlsLocation.RIGHT;
        int x = this.width/2;
        IntSortedSet heightSet = new IntAVLTreeSet();
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof AbstractStringWidget) {
                continue;
            }
            if (renderable instanceof AbstractWidget otherWidget) {
                if (useRight) {
                    int newX = otherWidget.getRight()+4;
                    if (newX > x) {
                        x = newX;
                        heightSet.clear();
                    }
                } else {
                    int newX = otherWidget.getX()-20-4;
                    if (newX < x) {
                        x = newX;
                        heightSet.clear();
                    }
                }

                heightSet.add(otherWidget.getY());
            }
        }

        IntSupplier nextHeight = new IntSupplier() {
            private final int[] heights = heightSet.toIntArray();
            private int heightIndex = heights.length >= 4 ? 1 : 0;
            private int lastHeight = Integer.MIN_VALUE;

            @Override
            public int getAsInt() {
                if (this.heightIndex >= heights.length) {
                    if (this.lastHeight == Integer.MIN_VALUE) {
                        this.lastHeight = MixinPauseScreen.this.height / 2;
                        return this.lastHeight;
                    } else {
                        this.lastHeight += 24;
                        return this.lastHeight;
                    }
                }

                int height = this.heights[this.heightIndex];
                this.heightIndex += 1;

                if (this.lastHeight != Integer.MIN_VALUE) {
                    height = Math.max(height, this.lastHeight + 24);
                }

                this.lastHeight = height;
                return this.lastHeight;
            }
        };

        if (Flashback.RECORDER == null) {
            int y = nextHeight.getAsInt();
            this.addRenderableWidget(new FlashbackButton(x, y, 20, 20, Component.translatable("flashback.recording_controls.start"), btn -> {
                Flashback.startRecordingReplay();
                Minecraft.getInstance().setScreen(null);
            }, Identifier.fromNamespaceAndPath("flashback", "icon_pixelated_start.png")).flashbackWithTooltip());
        } else {
            int y = nextHeight.getAsInt();
            this.addRenderableWidget(new FlashbackButton(x, y, 20, 20, Component.translatable("flashback.recording_controls.finish"), btn -> {
                Flashback.finishRecordingReplay();
                Minecraft.getInstance().setScreen(null);
            }, Identifier.fromNamespaceAndPath("flashback", "icon_pixelated_finish.png")).flashbackWithTooltip());

            if (Flashback.RECORDER.isPaused()) {
                y = nextHeight.getAsInt();
                this.addRenderableWidget(new FlashbackButton(x, y, 20, 20, Component.translatable("flashback.recording_controls.unpause"), btn -> {
                    Flashback.pauseRecordingReplay(false);
                    Minecraft.getInstance().setScreen(null);
                }, Identifier.fromNamespaceAndPath("flashback", "icon_pixelated_start.png")).flashbackWithTooltip());
            } else {
                y = nextHeight.getAsInt();
                this.addRenderableWidget(new FlashbackButton(x, y, 20, 20, Component.translatable("flashback.recording_controls.pause"), btn -> {
                    Flashback.pauseRecordingReplay(true);
                    Minecraft.getInstance().setScreen(null);
                }, Identifier.fromNamespaceAndPath("flashback", "icon_pixelated_pause.png")).flashbackWithTooltip());
            }

            y = nextHeight.getAsInt();
            this.addRenderableWidget(new FlashbackButton(x, y, 20, 20, Component.translatable("flashback.recording_controls.cancel"), btn -> {
                Minecraft.getInstance().setScreen(new ConfirmScreen(value -> {
                    if (value) {
                        Flashback.cancelRecordingReplay();
                        Minecraft.getInstance().setScreen(null);
                    } else {
                        Minecraft.getInstance().setScreen(new PauseScreen(true));
                    }
                }, Component.translatable("flashback.confirm_cancel_recording"), Component.translatable("flashback.confirm_cancel_recording_description")));
            }, Identifier.fromNamespaceAndPath("flashback", "icon_pixelated_cancel.png")).flashbackWithTooltip());
        }
    }

    @WrapOperation(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/FrameLayout;alignInRectangle(Lnet/minecraft/client/gui/layouts/LayoutElement;IIIIFF)V"))
    public void createPauseMenu_alignInRectangle(LayoutElement layoutElement, int i, int j, int k, int l, float f, float g, Operation<Void> original) {
        if (!Flashback.isInReplay() && Flashback.getConfig().recordingControls.controlsLocation == RecordingControlsLocation.BELOW) {
            g += 0.05f;
        }
        original.call(layoutElement, i, j, k, l, f, g);
    }

}
