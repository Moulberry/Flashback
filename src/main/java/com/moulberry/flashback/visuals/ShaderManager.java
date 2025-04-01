package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

public class ShaderManager {

    public static final RenderPipeline BLIT_SCREEN = RenderPipelines.register(
        RenderPipeline.builder()
                      .withLocation(ResourceLocation.fromNamespaceAndPath("flashback", "pipeline/blit_screen"))
                      .withVertexShader("core/blit_screen")
                      .withFragmentShader("core/blit_screen")
                      .withSampler("InSampler")
                      .withDepthWrite(false)
                      .withCull(false)
                      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                      .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
                      .build()
    );

    public static RenderPipeline BLIT_SCREEN_ROUND_ALPHA = RenderPipelines.register(
        RenderPipeline.builder()
                      .withLocation(ResourceLocation.fromNamespaceAndPath("flashback", "pipeline/flashback_blit_screen_flip"))
                      .withVertexShader(ResourceLocation.fromNamespaceAndPath("flashback", "core/blit_screen_round_alpha"))
                      .withFragmentShader(ResourceLocation.fromNamespaceAndPath("flashback", "core/blit_screen_round_alpha"))
                      .withSampler("InSampler")
                      .withDepthWrite(false)
                      .withCull(false)
                      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                      .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
                      .build()
    );

    public static RenderPipeline BLIT_SCREEN_FLIP = RenderPipelines.register(
        RenderPipeline.builder()
                      .withLocation(ResourceLocation.fromNamespaceAndPath("flashback", "pipeline/flashback_blit_screen_flip"))
                      .withVertexShader(ResourceLocation.fromNamespaceAndPath("flashback", "core/blit_screen_flip"))
                      .withFragmentShader("core/blit_screen")
                      .withSampler("InSampler")
                      .withDepthWrite(false)
                      .withCull(false)
                      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                      .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
                      .build()
    );

}
