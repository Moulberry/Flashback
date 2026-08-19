package com.moulberry.flashback.mixin.export;

import com.mojang.blaze3d.textures.TextureFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TextureFormat.class)
public enum MixinTextureFormat {
    FLASHBACK_R32_FLOAT(4);

    @Shadow
    MixinTextureFormat(int pixelSize) {
    }
}
