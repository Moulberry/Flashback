package com.moulberry.flashback.screen.select_replay;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.configuration.FlashbackConfig;
import com.moulberry.flashback.screen.ConfigScreen;
import com.moulberry.flashback.screen.ReplaySummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class SelectReplayScreen extends Screen {

    protected final Screen lastScreen;
    public final Path path;
    private Button selectButton;
    private Button editButton;
    private Button deleteButton;
    protected EditBox searchBox;
    private ReplaySelectionList list;

    public SelectReplayScreen(Screen screen, Path path) {
        super(Component.translatable("flashback.select_replay"));
        this.lastScreen = screen;
        this.path = path;
    }

    @Override
    protected void init() {
        this.searchBox = new EditBox(this.font, this.width / 2 - 128, 22, 126, 20, this.searchBox, Component.translatable("selectWorld.search"));
        this.searchBox.setResponder(string -> {
            FlashbackConfig config = Flashback.getConfig();
            this.list.updateFilter(string, config.replaySorting, config.sortDescending);
        });
        this.addWidget(this.searchBox);

        FlashbackConfig config = Flashback.getConfig();

        this.addRenderableWidget(CycleButton.builder(ReplaySorting::component)
            .withValues(ReplaySorting.values())
            .withInitialValue(config.replaySorting)
            .create(this.width / 2 + 2, 22, 125, 20, Component.literal("Sort"), (button, sorting) -> {
                FlashbackConfig configuration = Flashback.getConfig();
                configuration.replaySorting = sorting;
                configuration.delayedSaveToDefaultFolder();
                this.list.updateFilter(this.searchBox.getValue(), configuration.replaySorting, configuration.sortDescending);
            })
        );

        this.addRenderableWidget(new SortDirectionButton(this.width / 2 + 131, 22, 20, 20, Component.literal("Sort Direction"),
            sortDescending -> {
                FlashbackConfig configuration = Flashback.getConfig();
                configuration.sortDescending = sortDescending;
                configuration.delayedSaveToDefaultFolder();
                this.list.updateFilter(this.searchBox.getValue(), configuration.replaySorting, configuration.sortDescending);
            }, config.sortDescending));

        this.list = this.addRenderableWidget(new ReplaySelectionList(this, this.minecraft,
                 this.width, this.height - 112, 48, 36, this.searchBox.getValue(), config.replaySorting, config.sortDescending, this.list));
        this.selectButton = this.addRenderableWidget(Button.builder(Component.translatable("flashback.select_replay.open"), this::tryOpenReplay)
                .bounds(this.width / 2 - 151, this.height - 52, 302, 20).build());
        this.editButton = this.addRenderableWidget(Button.builder(Component.translatable("selectWorld.edit"), this::tryEditReplay)
                .bounds(this.width / 2 - 151, this.height - 28, 98, 20).build());
        this.deleteButton = this.addRenderableWidget(Button.builder(Component.translatable("selectWorld.delete"), this::tryDeleteReplay)
                                                           .bounds(this.width / 2 - 49, this.height - 28, 98, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.minecraft.setScreen(this.lastScreen))
                                                           .bounds(this.width / 2 + 53, this.height - 28, 98, 20).build());

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
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
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
            this.list.clearEntries();
        }
    }

    private void tryOpenReplay(Button button) {
        ReplaySelectionEntry.ReplayListEntry entry = this.list.getReplayListEntry();
        if (entry != null) {
            entry.openReplay();
        }
    }

    private void tryEditReplay(Button button) {
        ReplaySelectionEntry.ReplayListEntry entry = this.list.getReplayListEntry();
        if (entry != null) {
            entry.editReplay();
        }
    }

    private void tryDeleteReplay(Button button) {
        ReplaySelectionEntry.ReplayListEntry entry = this.list.getReplayListEntry();
        if (entry != null) {
            entry.deleteReplay();
        }
    }
}
