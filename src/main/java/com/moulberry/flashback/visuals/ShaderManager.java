package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.moulberry.flashback.Flashback;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.impl.client.rendering.FabricShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

public class ShaderManager {

    public static final ShaderManager INSTANCE = new ShaderManager();

    public static ShaderInstance blitScreenRoundAlpha;
    public static ShaderInstance blitScreenFlip;

    public void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(ResourceLocation.parse("flashback:blit_screen_round_alpha"), DefaultVertexFormat.BLIT_SCREEN, shaderInstance -> {
                blitScreenRoundAlpha = shaderInstance;
            });
            context.register(ResourceLocation.parse("flashback:blit_screen_flip"), DefaultVertexFormat.BLIT_SCREEN, shaderInstance -> {
                blitScreenFlip = shaderInstance;
            });
        });
    }

}
