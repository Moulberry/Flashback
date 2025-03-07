package com.moulberry.flashback.mixin.ui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.screen.FlashbackButton;
import com.moulberry.flashback.screen.select_replay.SelectReplayScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = TitleScreen.class, priority = 1300)
public class MixinTitleScreen extends Screen {

    private MixinTitleScreen() {
        super(null);
    }

    @Unique
    private final List<AbstractWidget> normalMenuWidgets = new ArrayList<>();

    @Unique
    private AbstractWidget openSelectReplayScreenButton = null;

    @Unique
    private boolean checkedPositionOfReplayButton = false;

    @Inject(method = "init", at = @At("RETURN"))
    public void init(CallbackInfo ci) {
        this.createOrPositionOpenSelectReplayScreenButton(true);
        this.checkedPositionOfReplayButton = false;
    }

    // We do this on tick to ensure that our button is added after ever other mod's
    // Our button logic has automatic repositioning, whereas other mods (i.e. ModMenu)
    // might not implement repositioning logic, causing our button to render under theirs
    @Inject(method = "tick", at = @At("RETURN"))
    public void tick(CallbackInfo ci) {
        if (!this.checkedPositionOfReplayButton) {
            this.checkedPositionOfReplayButton = true;
            this.createOrPositionOpenSelectReplayScreenButton(false);
        }
    }

    @Unique
    private void createOrPositionOpenSelectReplayScreenButton(boolean allowCreating) {
        // Set button to null if not in renderables list
        if (this.openSelectReplayScreenButton != null && !this.renderables.contains(this.openSelectReplayScreenButton)) {
            this.openSelectReplayScreenButton = null;
        }

        // Exit early if the button doesn't exist and we aren't allowed to create it
        if (!allowCreating && this.openSelectReplayScreenButton == null) {
            return;
        }

        // If the button already exists, check if it overlaps with anything
        // If there's no overlap, no need to move it
        if (this.openSelectReplayScreenButton != null) {
            int x = this.openSelectReplayScreenButton.getX();
            int y = this.openSelectReplayScreenButton.getY();
            int width = this.openSelectReplayScreenButton.getWidth();
            int height = this.openSelectReplayScreenButton.getHeight();

            boolean overlapsWithExistingButton = false;

            for (Renderable renderable : this.renderables) {
                if (renderable == this.openSelectReplayScreenButton) {
                    continue;
                }
                if (renderable instanceof AbstractWidget otherWidget) {
                    if (x < otherWidget.getRight() && x+width > otherWidget.getX() &&
                        y < otherWidget.getBottom() && y+height > otherWidget.getY()) {
                        overlapsWithExistingButton = true;
                        break;
                    }
                }
            }

            if (!overlapsWithExistingButton) {
                return;
            }
        }

        for (int offsetX = 0; offsetX < 5; offsetX++) {
            for (int i = this.normalMenuWidgets.size()-1; i >= 0; i--) {
                AbstractWidget widget = this.normalMenuWidgets.get(i);

                // Some mods which add custom menu functionality will run TitleScreen#createNormalMenuOptions
                // but then later clear all the renderables. Simple fix is to scan the renderables list to
                // ensure that the normal menu widget is still actually present
                if (!this.renderables.contains(widget)) {
                    continue;
                }

                int size = widget.getHeight();
                int x = widget.getRight() + 4 + size * offsetX;
                int y = widget.getY();

                boolean overlapsWithExistingButton = false;

                // Check for overlaps with any existing buttons
                for (Renderable renderable : this.renderables) {
                    if (renderable == this.openSelectReplayScreenButton) {
                        continue;
                    }
                    if (renderable instanceof AbstractWidget otherWidget) {
                        if (x < otherWidget.getRight() && x+size > otherWidget.getX() &&
                            y < otherWidget.getBottom() && y+size > otherWidget.getY()) {
                            overlapsWithExistingButton = true;
                            break;
                        }
                    }
                }

                if (!overlapsWithExistingButton) {
                    if (this.openSelectReplayScreenButton == null) {
                        this.openSelectReplayScreenButton = new FlashbackButton(x, y, size, size, Component.literal("Open Replays"), button -> {
                            List<String> incompatibleMods = Screen.hasShiftDown() ? List.of() : Flashback.getReplayIncompatibleMods();

                            if (incompatibleMods.isEmpty()) {
                                this.minecraft.setScreen(new SelectReplayScreen(this, Flashback.getReplayFolder()));
                            } else {
                                String mods = StringUtils.join(incompatibleMods, ", ");
                                String description = """
                                You have mods which are known to cause crashes when loading replays
                                Please remove the following mods in order to be able to load replays:

                                """;
                                this.minecraft.setScreen(new AlertScreen(() -> Minecraft.getInstance().setScreen(this),
                                    Component.literal("Incompatible Mods"), Component.literal(description).append(Component.literal(mods).withStyle(ChatFormatting.RED))));
                            }

                        });
                        this.addRenderableWidget(this.openSelectReplayScreenButton);
                    } else {
                        this.openSelectReplayScreenButton.setPosition(x, y);
                    }
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
    public void createNormalMenuOptions(int i, int j, CallbackInfoReturnable<Integer> cir) {
        this.normalMenuWidgets.clear();
    }

}
