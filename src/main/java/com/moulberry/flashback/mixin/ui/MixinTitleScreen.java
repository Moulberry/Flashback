package com.moulberry.flashback.mixin.ui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.moulberry.flashback.screen.SelectReplayScreen;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = TitleScreen.class, priority = 1300)
public class MixinTitleScreen extends Screen {

    private MixinTitleScreen() {
        super(null);
    }

    @Unique
    private final List<AbstractWidget> normalMenuWidgets = new ArrayList<>();

    @Inject(method = "init", at = @At("RETURN"))
    public void init(CallbackInfo ci) {
        for (int offsetX = 0; offsetX < 5; offsetX++) {
            for (int i = this.normalMenuWidgets.size()-1; i >= 0; i--) {
                AbstractWidget widget = this.normalMenuWidgets.get(i);
                int size = widget.getHeight();
                int x = widget.getRight() + 4 + size * offsetX;
                int y = widget.getY();

                boolean overlapsWithExistingButton = false;

                // Check for overlaps with any existing buttons
                for (Renderable renderable : this.renderables) {
                    if (renderable instanceof AbstractWidget otherWidget) {
                        if (x < otherWidget.getRight() && x+size > otherWidget.getX() &&
                                y < otherWidget.getBottom() && y+size > otherWidget.getY()) {
                            overlapsWithExistingButton = true;
                            break;
                        }
                    }
                }

                if (!overlapsWithExistingButton) {
                    this.addRenderableWidget(Button.builder(Component.literal("R"), button -> {
                        this.minecraft.setScreen(new SelectReplayScreen(this));
                    }).bounds(x, y, size, size).build());
                    return;
                }
            }
        }
    }

    @WrapOperation(method = "createNormalMenuOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
    public GuiEventListener addNormalMenuOption(TitleScreen instance, GuiEventListener guiEventListener, Operation<GuiEventListener> original) {
        GuiEventListener option = original.call(instance, guiEventListener);
        if (option instanceof AbstractWidget widget) {
            this.normalMenuWidgets.add(widget);
        }
        return option;
    }

    @Inject(method = "createNormalMenuOptions", at = @At("HEAD"))
    public void createNormalMenuOptions(int y, int buttonHeight, CallbackInfo ci) {
        this.normalMenuWidgets.clear();
    }

}
