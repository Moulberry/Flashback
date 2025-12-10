package com.moulberry.flashback.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class FlashbackButton extends Button {

    private static final Identifier ICON_PIXELATED = Identifier.parse("flashback:icon_pixelated.png");

    private final Identifier icon;

    public FlashbackButton(int x, int y, int width, int height, Component component, OnPress onPress) {
        this(x, y, width, height, component, onPress, ICON_PIXELATED);
    }

    public FlashbackButton(int x, int y, int width, int height, Component component, OnPress onPress, Identifier icon) {
        super(x, y, width, height, component, onPress, DEFAULT_NARRATION);
        this.icon = icon;
    }

    public FlashbackButton flashbackWithTooltip() {
        this.setTooltip(Tooltip.create(this.getMessage()));
        return this;
    }

    @Override
    protected void renderContents(GuiGraphics guiGraphics, int i, int j, float f) {
        super.renderDefaultSprite(guiGraphics);

        final int size = 16;
        int paddingX = (this.getWidth() - size) / 2;
        int paddingY = (this.getHeight() - size) / 2;

        int x = this.getX() + paddingX;
        int y = this.getY() + paddingY;

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.icon, x, y, 0f, 0f, size, size, size, size, ((int)(this.alpha * 0xFF) << 24) | 0xFFFFFF);
    }

}
