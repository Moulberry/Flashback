package com.moulberry.flashback;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.EXTFramebufferMultisampleBlitScaled;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;

public class FramebufferUtils {

    private static int dynamicReadFbo = -1;

    public static int bindColour(RenderTarget renderTarget) {
        if (dynamicReadFbo == -1) {
            dynamicReadFbo = GlStateManager.glGenFramebuffers();
        }
        int textureId = ((GlTexture)renderTarget.getColorTexture()).glId();
        GlStateManager._glBindFramebuffer(GL32.GL_READ_FRAMEBUFFER, dynamicReadFbo);
        GlStateManager._glFramebufferTexture2D(GL32.GL_READ_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT0, 3553, textureId, 0);
        GlStateManager._glFramebufferTexture2D(GL32.GL_READ_FRAMEBUFFER, GL32.GL_DEPTH_ATTACHMENT, 3553, 0, 0);
        return dynamicReadFbo;
    }

    private static int blitFunction = -1;
    private static int filterParameter = -1;

    public static void blitToScreenPartial(RenderTarget renderTarget, int width, int height, float x1, float y1, float x2, float y2) {
        GlStateManager._viewport(0, 0, width, height);

        int oldReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        int readFbo = FramebufferUtils.bindColour(renderTarget);

        if (blitFunction == -1) {
            GLCapabilities cap = GL.createCapabilities();
            int samples = GL30C.glGetInteger(GL30C.GL_SAMPLE_BUFFERS);
            Flashback.LOGGER.info("Read framebuffer has sample count {}", samples);
            if (cap.GL_ARB_direct_state_access) {
                blitFunction = 1;
                Flashback.LOGGER.info("Using blit function glBlitNamedFramebuffer");
            } else {
                blitFunction = 0;
                Flashback.LOGGER.info("Using blit function glBlitFrameBuffer");
            }
            if (samples > 0 && cap.GL_EXT_framebuffer_multisample_blit_scaled) {
                filterParameter = EXTFramebufferMultisampleBlitScaled.GL_SCALED_RESOLVE_FASTEST_EXT;
                Flashback.LOGGER.info("Using filter parameter SCALED_RESOLVE_FASTEST_EXT");
            } else {
                filterParameter = GL11.GL_LINEAR;
                Flashback.LOGGER.info("Using filter parameter GL_LINEAR");
            }
        }

        int x = (int)(width*x1);
        int y = (int)(height*y1);
        int w = (int)(width*x2 - width*x1 + 1);
        int h = (int)(height*y2 - height*y1 + 1);
        if (Math.abs(renderTarget.width - w) <= 1) {
            w = renderTarget.width;
        }
        if (Math.abs(renderTarget.height - h) <= 1) {
            h = renderTarget.height;
        }

        if (blitFunction == 0) {
            GlStateManager._glBindFramebuffer(GL32.GL_DRAW_FRAMEBUFFER, 0);
            GlStateManager._glBlitFrameBuffer(0, 0, renderTarget.width, renderTarget.height,
                x, height - (y+h), x+w, height - y, GL11.GL_COLOR_BUFFER_BIT, filterParameter);
        } else if (blitFunction == 1) {
            ARBDirectStateAccess.glBlitNamedFramebuffer(readFbo, 0, 0, 0, renderTarget.width, renderTarget.height,
                x, height - (y+h), x+w, height - y, GL11.GL_COLOR_BUFFER_BIT, filterParameter);
        }

        GlStateManager._glBindFramebuffer(GL32.GL_READ_FRAMEBUFFER, oldReadFbo);
        GlStateManager._glBindFramebuffer(GL32.GL_DRAW_FRAMEBUFFER, oldDrawFbo);
    }

}
