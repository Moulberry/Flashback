package com.moulberry.flashback.mixin.povscreen;

import com.moulberry.flashback.ext.ScreenExt;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Screen.class)
public class MixinScreen implements ScreenExt {
    @Unique
    private boolean fake = false;

    @Override
    public boolean flashback$isFake() {
        return this.fake;
    }

    @Override
    public void flashback$setFake(boolean fake) {
        this.fake = fake;
    }
}
