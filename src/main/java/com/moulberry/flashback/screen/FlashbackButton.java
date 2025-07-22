package com.moulberry.flashback.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
<<<<<<< HEAD
=======
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
>>>>>>> d0c062e (Convert config to Lattice, add option for recording controls location)
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class FlashbackButton extends Button {

    private static final ResourceLocation ICON_PIXELATED = ResourceLocation.parse("flashback:icon_pixelated.png");

    private final ResourceLocation icon;

    public FlashbackButton(int x, int y, int width, int height, Component component, OnPress onPress) {
        this(x, y, width, height, component, onPress, ICON_PIXELATED);
    }

    public FlashbackButton(int x, int y, int width, int height, Component component, OnPress onPress, ResourceLocation icon) {
        super(x, y, width, height, component, onPress, DEFAULT_NARRATION);
        this.icon = icon;
    }

    public FlashbackButton flashbackWithTooltip() {
        this.setTooltip(Tooltip.create(this.getMessage()));
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        super.renderWidget(guiGraphics, i, j, f);

        final int size = 16;
        int paddingX = (this.getWidth() - size) / 2;
        int paddingY = (this.getHeight() - size) / 2;

        int x = this.getX() + paddingX;
        int y = this.getY() + paddingY;

        guiGraphics.blit(RenderType::guiTextured, ICON_PIXELATED, x, y, 0f, 0f, size, size, size, size, ((int)(this.alpha * 0xFF) << 24) | 0xFFFFFF);
    }

    @Override
    public void renderString(GuiGraphics guiGraphics, Font font, int i) {
    }

}
