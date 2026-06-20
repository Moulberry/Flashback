package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public class ShaderManager {

    public static final RenderPipeline BLIT_SCREEN = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("flashback", "pipeline/blit_screen"))
            .withVertexShader("core/screenquad")
            .withFragmentShader("core/blit_screen")
            .withBindGroupLayout(BindGroupLayout.builder()
                .withSampler("InSampler")
                .build())
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    public static final RenderPipeline BLIT_SCREEN_WITH_UV = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("flashback", "pipeline/blit_screen_with_uv"))
            .withVertexShader(Identifier.fromNamespaceAndPath("flashback", "core/blit_screen_old"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("flashback", "core/blit_screen_old"))
            .withBindGroupLayout(BindGroupLayout.builder()
                .withSampler("InSampler")
                .build())
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .build())
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    public static final RenderPipeline BLIT_SCREEN_ROUND_ALPHA = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("flashback", "pipeline/flashback_blit_screen_flip"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("flashback", "core/blit_screen_round_alpha"))
            .withBindGroupLayout(BindGroupLayout.builder()
                .withSampler("InSampler")
                .build())
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    public static final RenderPipeline BLIT_SCREEN_FLIP = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("flashback", "pipeline/flashback_blit_screen_flip"))
            .withVertexShader(Identifier.fromNamespaceAndPath("flashback", "core/screenquad_flip"))
            .withFragmentShader("core/blit_screen")
            .withBindGroupLayout(BindGroupLayout.builder()
                .withSampler("InSampler")
                .build())
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

}
