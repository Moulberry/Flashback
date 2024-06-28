package com.moulberry.flashback.mixin.client.ui;

import com.moulberry.flashback.FlashbackClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MixinTitleScreen extends Screen {

    private MixinTitleScreen() {
        super(null);
    }

    @Inject(method = "createNormalMenuOptions", at = @At("RETURN"))
    public void createNormalMenuOptions(int i, int j, CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(Component.literal("Replay"), button -> {
            FlashbackClient.testReplayWorld();
        }).bounds(this.width / 2 - 100, i + j * 3, 200, 20).build());
    }

}
