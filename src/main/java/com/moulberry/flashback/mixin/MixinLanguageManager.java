package com.moulberry.flashback.mixin;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LanguageManager.class)
public class MixinLanguageManager {

    @Shadow
    private String currentCode;

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    public void onReload(ResourceManager resourceManager, CallbackInfo ci) {
        ReplayUI.initFonts(this.currentCode);
        ImGuiHelper.clearEnumComboTextCache();
    }

}
