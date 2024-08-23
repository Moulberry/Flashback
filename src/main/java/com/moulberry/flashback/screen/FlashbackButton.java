package com.moulberry.flashback.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class FlashbackButton extends Button {

    public FlashbackButton(int x, int y, int width, int height, Component component, OnPress onPress) {
        super(x, y, width, height, component, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        super.renderWidget(guiGraphics, i, j, f);

        final int size = 14;
        int paddingX = (this.getWidth() - size) / 2;
        int paddingY = (this.getHeight() - size) / 2;

        int x = this.getX() + paddingX;
        int y = this.getY() + paddingY;

        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int realSize = (int)(size * guiScale);

        ResourceLocation icon;
        if (realSize == 14) {
            icon = ResourceLocation.parse("flashback:icon_14x.png");
        } else if (realSize == 28) {
            icon = ResourceLocation.parse("flashback:icon_28x.png");
        } else if (realSize == 42) {
            icon = ResourceLocation.parse("flashback:icon_42x.png");
        } else if (realSize == 56) {
            icon = ResourceLocation.parse("flashback:icon_56x.png");
        } else {
            icon = ResourceLocation.parse("flashback:icon.png");
        }

        guiGraphics.blit(icon, x, y, 0f, 0f, size, size, size, size);

    }

    @Override
    public void renderString(GuiGraphics guiGraphics, Font font, int i) {
    }

}
