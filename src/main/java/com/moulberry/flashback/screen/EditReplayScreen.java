package com.moulberry.flashback.screen;

import com.google.gson.JsonObject;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.record.FlashbackMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class EditReplayScreen extends Screen {

    @Nullable
    private final Screen lastScreen;

    private final FlashbackMeta metadata;
    private String changedReplayName;
    private Button saveChangesButton;

    private final Path replayPath;

    public EditReplayScreen(@Nullable Screen lastScreen, ReplaySummary summary) {
        super(Component.literal("Edit Replay"));
        this.lastScreen = lastScreen;
        this.metadata = summary.getReplayMetadata();
        this.changedReplayName = summary.getReplayName();
        this.replayPath = summary.getPath();
    }

    @Override
    protected void setInitialFocus() {
    }

    @Override
    protected void init() {
        super.init();

        GridLayout gridLayout = new GridLayout();
        gridLayout.defaultCellSetting().padding(4, 4, 4, 0);
        GridLayout.RowHelper rowHelper = gridLayout.createRowHelper(2);

        rowHelper.addChild(new StringWidget(204, 20, Component.literal("Edit Replay"), this.font), 2);

        rowHelper.addChild(new BottomTextWidget(204, 10, Component.literal("Replay Name"), this.font).alignLeft(), 2);

        EditBox replayNameEditBox = new EditBox(this.font, 0, 0, 204, 20, Component.literal(this.changedReplayName));
        replayNameEditBox.setMaxLength(128);
        replayNameEditBox.setValue(this.changedReplayName);
        replayNameEditBox.setResponder(this::setReplayName);
        rowHelper.addChild(replayNameEditBox, 2);

        rowHelper.addChild(new BottomTextWidget(204, 10, Component.literal(""), this.font), 2);

        rowHelper.addChild(Button.builder(Component.literal("Combine With Other Replay"), button -> {
            this.minecraft.setScreen(new CombineReplayScreen(this, this.replayPath, null, null));
        }).width(204).build(), 2);

        this.saveChangesButton = rowHelper.addChild(Button.builder(Component.literal("Save Changes"), button -> {
            this.applyChanges();
        }).width(98).build());
        this.saveChangesButton.active = false;
        rowHelper.addChild(Button.builder(Component.literal("Back"), button -> {
            this.minecraft.setScreen(this.lastScreen);
        }).width(98).build());
        gridLayout.arrangeElements();
        FrameLayout.alignInRectangle(gridLayout, 0, 0, this.width, this.height, 0.5f, 0.5f);
        gridLayout.visitWidgets(this::addRenderableWidget);

        this.updateSaveChangesActive();
        this.setInitialFocus(replayNameEditBox);
    }

    private void applyChanges() {
        this.metadata.name = this.changedReplayName;

        try (FileSystem fileSystem = FileSystems.newFileSystem(this.replayPath)) {
            Path metadataPath = fileSystem.getPath("/metadata.json");

            // Update
            String metadataJson = FlashbackGson.PRETTY.toJson(this.metadata.toJson());
            Files.writeString(metadataPath, metadataJson);
        } catch (IOException e) {
            Flashback.LOGGER.error("Unable to edit replay", e);
            Minecraft.getInstance().setScreen(new AlertScreen(() -> Minecraft.getInstance().setScreen(this.lastScreen),
                Component.literal("Unable to edit replay"), Component.literal(e.toString())));
        }

        this.updateSaveChangesActive();
        this.clearFocus();
    }

    private void setReplayName(String name) {
        this.changedReplayName = name;
        this.updateSaveChangesActive();
    }

    private void updateSaveChangesActive() {
        this.saveChangesButton.active = !Objects.equals(this.metadata.name, this.changedReplayName);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}
