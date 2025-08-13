package com.moulberry.flashback.screen.select_replay;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.RegistryMetaHelper;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.screen.ReplaySummary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class SelectReplayScreen extends Screen {

    protected final Screen lastScreen;
    public final Path path;
    private Button selectButton;
    private Button editButton;
    private Button deleteButton;
    protected EditBox searchBox;
    private ReplaySelectionList list;
    private LinkedHashMap<String, LinkedHashSet<String>> currentNamespacesForRegistries;

    public SelectReplayScreen(Screen screen, Path path) {
        super(Component.translatable("flashback.select_replay"));
        this.lastScreen = screen;
        this.path = path;
        this.currentNamespacesForRegistries = RegistryMetaHelper.calculateNamespacesForRegistries();
    }

    @Override
    protected void init() {
        this.searchBox = new EditBox(this.font, this.width / 2 - 128, 22, 126, 20, this.searchBox, Component.translatable("selectWorld.search"));
        this.searchBox.setResponder(string -> {
            FlashbackConfigV1 config = Flashback.getConfig();
            this.list.updateFilter(string, config.internal.replaySorting, config.internal.sortDescending);
        });
        this.addWidget(this.searchBox);

        FlashbackConfigV1 config = Flashback.getConfig();

        this.addRenderableWidget(CycleButton.builder(ReplaySorting::component)
            .withValues(ReplaySorting.values())
            .withInitialValue(config.internal.replaySorting)
            .create(this.width / 2 + 2, 22, 125, 20, Component.translatable("flashback.sort"), (button, sorting) -> {
                FlashbackConfigV1 configuration = Flashback.getConfig();
                configuration.internal.replaySorting = sorting;
                configuration.delayedSaveToDefaultFolder();
                this.list.updateFilter(this.searchBox.getValue(), configuration.internal.replaySorting, configuration.internal.sortDescending);
            })
        );

        this.addRenderableWidget(new SortDirectionButton(this.width / 2 + 131, 22, 20, 20, Component.translatable("flashback.sort_direction"),
            sortDescending -> {
                FlashbackConfigV1 configuration = Flashback.getConfig();
                configuration.internal.sortDescending = sortDescending;
                configuration.delayedSaveToDefaultFolder();
                this.list.updateFilter(this.searchBox.getValue(), configuration.internal.replaySorting, configuration.internal.sortDescending);
            }, config.internal.sortDescending));

        this.list = this.addRenderableWidget(new ReplaySelectionList(this, this.minecraft,
                 this.width, this.height - 112, 48, 36, this.searchBox.getValue(), config.internal.replaySorting, config.internal.sortDescending,
                 this.currentNamespacesForRegistries, this.list));

        this.selectButton = this.addRenderableWidget(Button.builder(Component.translatable("flashback.select_replay.open"), this::tryOpenReplay)
                                                           .bounds(this.width / 2 - 151, this.height - 52, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("flashback.flashback_options"), btn -> Flashback.openConfigScreen(this))
                                       .bounds(this.width / 2 + 1, this.height - 52, 150, 20).build());

        this.editButton = this.addRenderableWidget(Button.builder(Component.translatable("selectWorld.edit"), this::tryEditReplay)
                .bounds(this.width / 2 - 151, this.height - 28, 98, 20).build());
        this.deleteButton = this.addRenderableWidget(Button.builder(Component.translatable("selectWorld.delete"), this::tryDeleteReplay)
                                                           .bounds(this.width / 2 - 49, this.height - 28, 98, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.minecraft.setScreen(this.lastScreen))
                                                           .bounds(this.width / 2 + 53, this.height - 28, 98, 20).build());

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
