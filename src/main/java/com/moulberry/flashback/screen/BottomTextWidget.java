package com.moulberry.flashback.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.network.chat.Component;

public class BottomTextWidget extends AbstractStringWidget {

    public float horizontalAlignment = 0.5f;

    public BottomTextWidget(int width, int height, Component component, Font font) {
        super(0, 0, width, height, component, font);
        this.active = false;
    }

    public BottomTextWidget alignLeft() {
        this.horizontalAlignment = 0.0f;
        return this;
    }

    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        Component component = this.getMessage();

        Font font = this.getFont();

        int width = this.getWidth();
        int textWidth = font.width(component);
        int x = this.getX() + Math.round((width - textWidth) * this.horizontalAlignment);
        int y = this.getY() + this.getHeight() - this.getFont().lineHeight + 1;

        guiGraphics.drawString(font, component.getVisualOrderText(), x, y, this.getColor());
    }

}
