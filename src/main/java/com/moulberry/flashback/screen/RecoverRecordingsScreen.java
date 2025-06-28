package com.moulberry.flashback.screen;

import com.moulberry.flashback.Flashback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.time.Duration;
import java.util.function.Consumer;

public class RecoverRecordingsScreen extends Screen {

    private Screen lastScreen;
    private final Component title;
    private final Component messageText;
    private MultiLineLabel message = MultiLineLabel.EMPTY;
    private Consumer<RecoveryOption> handler;

    public enum RecoveryOption {
        RECOVER,
        SKIP,
        DELETE
    }

    public RecoverRecordingsScreen(Screen lastScreen, Component title, Component message, Consumer<RecoveryOption> handler) {
        super(title);
        this.lastScreen = lastScreen;
        this.title = title;
        this.messageText = message;
        this.handler = handler;
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(super.getNarrationMessage(), this.messageText);
    }

    @Override
    protected void init() {
        super.init();

        this.message = MultiLineLabel.create(this.font, this.messageText, this.width - 50);
        int lines = this.message.getLineCount();
        int height = lines * this.font.lineHeight;
        int buttonY = Mth.clamp(90 + height + 12, this.height / 6 + 96, this.height - 24);

        this.addRenderableWidget(Button.builder(Component.literal("Recover").withStyle(ChatFormatting.GREEN), b -> {
            Minecraft.getInstance().setScreen(this.lastScreen);
            this.handler.accept(RecoveryOption.RECOVER);
        }).bounds((this.width - 304) / 2, buttonY, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Skip").withStyle(ChatFormatting.YELLOW), b -> {
            Minecraft.getInstance().setScreen(this.lastScreen);
            this.handler.accept(RecoveryOption.SKIP);
        }).bounds((this.width - 304) / 2 + 104, buttonY, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Delete").withStyle(ChatFormatting.RED), b -> {
            Minecraft.getInstance().setScreen(this.lastScreen);
            this.handler.accept(RecoveryOption.DELETE);
        }).bounds((this.width - 304)/2 + 208, buttonY, 96, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        super.render(guiGraphics, i, j, f);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 70, 0xFFFFFFFF);
        this.message.renderCentered(guiGraphics, this.width / 2, 90);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

}
