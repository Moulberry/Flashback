package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.resources.ResourceLocation;

public class ShaderManager {

    public static final ShaderManager INSTANCE = new ShaderManager();

    public static final ShaderProgram blitScreenRoundAlpha = new ShaderProgram(
        ResourceLocation.parse("flashback:core/blit_screen_round_alpha"), DefaultVertexFormat.BLIT_SCREEN, ShaderDefines.EMPTY
    );
    public static final ShaderProgram blitScreenFlip = new ShaderProgram(
            ResourceLocation.parse("flashback:core/blit_screen_flip"), DefaultVertexFormat.BLIT_SCREEN, ShaderDefines.EMPTY
    );

    public void register() {
        CoreShaders.getProgramsToPreload().add(blitScreenRoundAlpha);
        CoreShaders.getProgramsToPreload().add(blitScreenFlip);
    }

}
