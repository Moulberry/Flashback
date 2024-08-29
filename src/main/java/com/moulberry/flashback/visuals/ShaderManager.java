package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.moulberry.flashback.Flashback;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.impl.client.rendering.FabricShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

public class ShaderManager implements SimpleSynchronousResourceReloadListener {

    public static final ShaderManager INSTANCE = new ShaderManager();

    public static ShaderInstance blitScreenRoundAlpha;
    public static ShaderInstance blitScreenFlip;

    @Override
    public ResourceLocation getFabricId() {
        return ResourceLocation.parse("flashback:shaders");
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Flashback.LOGGER.info("Reloading shaders...");

        if (blitScreenRoundAlpha != null) {
            blitScreenRoundAlpha.close();
            blitScreenRoundAlpha = null;
        }
        if (blitScreenFlip != null) {
            blitScreenFlip.close();
            blitScreenFlip = null;
        }

        try {
            blitScreenRoundAlpha = new FabricShaderProgram(Minecraft.getInstance().getResourceManager(),
                ResourceLocation.parse("flashback:blit_screen_round_alpha"), DefaultVertexFormat.BLIT_SCREEN);
        } catch (IOException e) {
            Flashback.LOGGER.error("Failed to load flashback:blit_screen_round_alpha shader", e);
        }
        try {
            blitScreenFlip = new FabricShaderProgram(Minecraft.getInstance().getResourceManager(),
                ResourceLocation.parse("flashback:blit_screen_flip"), DefaultVertexFormat.BLIT_SCREEN);
        } catch (IOException e) {
            Flashback.LOGGER.error("Failed to load flashback:blit_screen_flip shader", e);
        }
    }

}
