package com.moulberry.flashback.screen;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class SelectReplayScreen extends Screen {

    protected final Screen lastScreen;
    private Button selectButton;
    private Button editButton;
    private Button deleteButton;
    protected EditBox searchBox;
    private ReplaySelectionList list;

    public SelectReplayScreen(Screen screen) {
        super(Component.translatable("flashback.select_replay"));
        this.lastScreen = screen;
    }

    @Override
    protected void init() {
        this.searchBox = new EditBox(this.font, this.width / 2 - 100, 22, 200, 20, this.searchBox, Component.translatable("selectWorld.search"));
        this.searchBox.setResponder(string -> this.list.updateFilter(string));
        this.addWidget(this.searchBox);
        this.list = this.addRenderableWidget(new ReplaySelectionList(this, this.minecraft,
                 this.width, this.height - 112, 48, 36, this.searchBox.getValue(), this.list));
        this.selectButton = this.addRenderableWidget(Button.builder(Component.translatable("flashback.select_replay.open"), this::tryOpenReplay)
                .bounds(this.width / 2 - 154, this.height - 52, 308, 20).build());
        this.editButton = this.addRenderableWidget(Button.builder(Component.translatable("selectWorld.edit"), this::tryEditReplay)
                .bounds(this.width / 2 - 154, this.height - 28, 150, 20).build());
        this.deleteButton = this.addRenderableWidget(Button.builder(Component.translatable("selectWorld.delete"), this::tryDeleteReplay)
                                                           .bounds(this.width / 2 + 4, this.height - 28, 72, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.minecraft.setScreen(this.lastScreen))
                                                           .bounds(this.width / 2 + 82, this.height - 28, 72, 20).build());

        if (this.width/2 > 154 + 128) {
            this.addRenderableWidget(Button.builder(Component.literal("Flashback Settings"), btn -> Minecraft.getInstance().setScreen(new ConfigScreen(this)))
                                           .bounds(this.width - 120 - 8, this.height - 28, 120, 20).build());
        }
        this.updateButtonStatus(null);
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.searchBox);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        super.render(guiGraphics, i, j, f);
        this.searchBox.render(guiGraphics, i, j, f);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
    }

    public void updateButtonStatus(@Nullable ReplaySummary replaySummary) {
        if (this.selectButton == null) {
            return;
        }

        if (replaySummary == null) {
            this.selectButton.active = false;
            this.editButton.active = false;
            this.deleteButton.active = false;
        } else {
            this.selectButton.active = replaySummary.canOpen();
            this.editButton.active = true;
            this.deleteButton.active = true;
        }
    }

    @Override
    public void removed() {
        if (this.list != null) {
            this.list.children().forEach(ReplaySelectionList.Entry::close);
        }
    }

    private void tryOpenReplay(Button button) {
        ReplaySelectionList.ReplayListEntry entry = this.list.getReplayListEntry();
        if (entry != null) {
            entry.openReplay();
        }
    }

    private void tryEditReplay(Button button) {
        ReplaySelectionList.ReplayListEntry entry = this.list.getReplayListEntry();
        if (entry != null) {
            entry.editReplay();
        }
    }

    private void tryDeleteReplay(Button button) {
        ReplaySelectionList.ReplayListEntry entry = this.list.getReplayListEntry();
        if (entry != null) {
            entry.deleteReplay();
        }
    }
}
