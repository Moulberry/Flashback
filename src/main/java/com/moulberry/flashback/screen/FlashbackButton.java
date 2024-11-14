package com.moulberry.flashback.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class FlashbackButton extends Button {

    private static final ResourceLocation ICON_PIXELATED = ResourceLocation.parse("flashback:icon_pixelated.png");

    public FlashbackButton(int x, int y, int width, int height, Component component, OnPress onPress) {
        super(x, y, width, height, component, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        super.renderWidget(guiGraphics, i, j, f);

        final int size = 16;
        int paddingX = (this.getWidth() - size) / 2;
        int paddingY = (this.getHeight() - size) / 2;

        int x = this.getX() + paddingX;
        int y = this.getY() + paddingY;

        guiGraphics.blit(RenderType::guiTextured, ICON_PIXELATED, x, y, 0f, 0f, size, size, size, size);

    }

    @Override
    public void renderString(GuiGraphics guiGraphics, Font font, int i) {
    }

}
