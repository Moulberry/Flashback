package com.moulberry.flashback.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class BottomTextWidget extends AbstractWidget {

    private final Font font;
    public float horizontalAlignment = 0.5f;

    public BottomTextWidget(int width, int height, Component component, Font font) {
        super(0, 0, width, height, component);
        this.font = font;
        this.active = false;
    }

    public BottomTextWidget alignLeft() {
        this.horizontalAlignment = 0.0f;
        return this;
    }

    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        Component component = this.getMessage();

        int width = this.getWidth();
        int textWidth = font.width(component);
        int x = this.getX() + Math.round((width - textWidth) * this.horizontalAlignment);
        int y = this.getY() + this.getHeight() - this.font.lineHeight + 1;

        guiGraphics.drawString(font, component.getVisualOrderText(), x, y, -1);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
