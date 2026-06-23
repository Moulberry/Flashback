package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public class ShaderManager {

    public static final RenderPipeline BLIT_SCREEN = RenderPipelines.register(
        RenderPipeline.builder()
                      .withLocation(Identifier.fromNamespaceAndPath("flashback", "pipeline/blit_screen"))
                      .withVertexShader("core/screenquad")
                      .withFragmentShader("core/blit_screen")
                      .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                      .withDepthStencilState(Optional.empty())
                      .withCull(false)
                      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                      .build()
    );

    public static final RenderPipeline BLIT_SCREEN_WITH_UV = RenderPipelines.register(
        RenderPipeline.builder()
                      .withLocation(Identifier.fromNamespaceAndPath("flashback", "pipeline/blit_screen_with_uv"))
                      .withVertexShader(Identifier.fromNamespaceAndPath("flashback", "core/blit_screen_old"))
                      .withFragmentShader(Identifier.fromNamespaceAndPath("flashback", "core/blit_screen_old"))
                      .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                      .withDepthStencilState(Optional.empty())
                      .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA)))
                      .withCull(false)
                      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                      .withPrimitiveTopology(PrimitiveTopology.QUADS)
                      .build()
    );

    public static final RenderPipeline BLIT_SCREEN_ROUND_ALPHA = RenderPipelines.register(
        RenderPipeline.builder()
                      .withLocation(Identifier.fromNamespaceAndPath("flashback", "pipeline/flashback_blit_screen_flip"))
                      .withVertexShader("core/screenquad")
                      .withFragmentShader(Identifier.fromNamespaceAndPath("flashback", "core/blit_screen_round_alpha"))
                      .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                      .withDepthStencilState(Optional.empty())
                      .withCull(false)
                      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                      .withPrimitiveTopology(PrimitiveTopology.QUADS)
                      .build()
    );

    public static final RenderPipeline BLIT_SCREEN_FLIP = RenderPipelines.register(
        RenderPipeline.builder()
                      .withLocation(Identifier.fromNamespaceAndPath("flashback", "pipeline/flashback_blit_screen_flip"))
                      .withVertexShader(Identifier.fromNamespaceAndPath("flashback", "core/screenquad_flip"))
                      .withFragmentShader("core/blit_screen")
                      .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                      .withDepthStencilState(Optional.empty())
                      .withCull(false)
                      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                      .build()
    );

}
