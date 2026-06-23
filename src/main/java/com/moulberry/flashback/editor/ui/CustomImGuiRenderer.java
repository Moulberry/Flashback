package com.moulberry.flashback.editor.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import imgui.moulberry90.ImDrawData;

public interface CustomImGuiRenderer {

    void init();
    RenderTarget renderDrawData(final ImDrawData drawData);
    long getTextureId(GpuTextureView gpuTextureView);
    void setSampleLinear(long id);
    void setSampleNearest(long id);
    void updateFontsTexture();

}
