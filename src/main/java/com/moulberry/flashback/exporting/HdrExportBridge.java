package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Filled in by an HDR mod at runtime; Flashback itself has no dependency on one. While nothing is
 * registered the HDR export option is not offered and the 8-bit path is used unchanged.
 */
public class HdrExportBridge {

    public interface ColorTransform {
        /**
         * Render {@code source} through the HDR colour transform (BT.2020 primaries + PQ transfer)
         * into a texture that reads back as 16-bit normalised RGBA, keeping GL's bottom-up
         * orientation. The returned texture must stay valid until the next call.
         */
        GpuTexture transform(RenderTarget source, int width, int height);
    }

    private static volatile ColorTransform transform;

    /** Mirrors the per-export checkbox; set by the export window. */
    public static volatile boolean requested;

    public static void register(ColorTransform colorTransform) {
        transform = colorTransform;
    }

    public static boolean available() {
        return transform != null;
    }

    public static boolean active() {
        return requested && transform != null;
    }

    public static ColorTransform get() {
        return transform;
    }
}
