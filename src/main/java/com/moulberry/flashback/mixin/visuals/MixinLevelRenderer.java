package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.visuals.WorldRenderHook;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    // renderBuffers field removed in 26.2
    // @Shadow @Final private RenderBuffers renderBuffers;

    // LevelTargetBundle removed in 26.2
    // @Shadow @Final private LevelTargetBundle targets;

    // renderLevel renamed to render in 26.2, targets and addLateDebugPass removed
    // @Inject(method="renderLevel", at=@At(
    //     value = "INVOKE",
    //     target = "Lnet/minecraft/client/renderer/LevelRenderer;addLateDebugPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4fc;)V",
    //     shift = At.Shift.BEFORE
    // ), require = 0)
    // public void renderLevelPost(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline,
    //     CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky,
    //     ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci,
    //     @Local FrameGraphBuilder frameGraphBuilder
    // ) {
    //     // Entire method disabled - renderLevel, targets, addLateDebugPass all removed in 26.2
    // }

}
