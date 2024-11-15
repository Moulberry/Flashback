package com.moulberry.flashback.screen.select_replay;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public class SortDirectionButton extends Button {

    private static final ResourceLocation DOWN_ARROW = ResourceLocation.parse("flashback:down_arrow.png");
    private static final ResourceLocation UP_ARROW = ResourceLocation.parse("flashback:up_arrow.png");

    public boolean sortDescending;

    public SortDirectionButton(int x, int y, int width, int height, Component component, Consumer<Boolean> changed, boolean initialSortDescending) {
        super(x, y, width, height, component, button -> {
            SortDirectionButton sortButton = (SortDirectionButton) button;
            sortButton.sortDescending = !sortButton.sortDescending;
            changed.accept(sortButton.sortDescending);
        }, DEFAULT_NARRATION);
        this.sortDescending = initialSortDescending;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        super.renderWidget(guiGraphics, i, j, f);

        final int size = 16;
        int paddingX = (this.getWidth() - size) / 2;
        int paddingY = (this.getHeight() - size) / 2;

        int x = this.getX() + paddingX;
        int y = this.getY() + paddingY;

        if (this.sortDescending) {
            guiGraphics.blit(RenderType::guiTextured, DOWN_ARROW, x, y, 0f, 0f, size, size, size, size);
        } else {
            guiGraphics.blit(RenderType::guiTextured, UP_ARROW, x, y, 0f, 0f, size, size, size, size);
        }
    }

    @Override
    public void renderString(GuiGraphics guiGraphics, Font font, int i) {
    }

}
