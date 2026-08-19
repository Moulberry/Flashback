package com.moulberry.flashback.mixin.export;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlConst.class)
public class MixinGlConst {

    @Inject(method = "Lcom/mojang/blaze3d/opengl/GlConst;toGlInternalId(Lcom/mojang/blaze3d/textures/TextureFormat;)I", at = @At("HEAD"), cancellable = true)
    private static void toGlInternalId(TextureFormat textureFormat, CallbackInfoReturnable<Integer> cir) {
        if (textureFormat == TextureFormat.FLASHBACK_R32_FLOAT) {
            cir.setReturnValue(GL30.GL_R32F);
        }
    }

    @Inject(method = "Lcom/mojang/blaze3d/opengl/GlConst;toGlExternalId(Lcom/mojang/blaze3d/textures/TextureFormat;)I", at = @At("HEAD"), cancellable = true)
    private static void toGlExternalId(TextureFormat textureFormat, CallbackInfoReturnable<Integer> cir) {
        if (textureFormat == TextureFormat.FLASHBACK_R32_FLOAT) {
            cir.setReturnValue(GL11.GL_RED);
        }
    }

    @Inject(method = "Lcom/mojang/blaze3d/opengl/GlConst;toGlType(Lcom/mojang/blaze3d/textures/TextureFormat;)I", at = @At("HEAD"), cancellable = true)
    private static void toGlType(TextureFormat textureFormat, CallbackInfoReturnable<Integer> cir) {
        if (textureFormat == TextureFormat.FLASHBACK_R32_FLOAT) {
            cir.setReturnValue(GL11.GL_FLOAT);
        }
    }

}
