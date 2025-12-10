package com.moulberry.flashback.screen;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackTextComponents;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.record.ReplayExporter;
import net.minecraft.util.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SaveReplayScreen extends Screen {

    private Path recordFolder;

    private String replayName;
    private Path savePath = null;
    private boolean generateFileFromReplayName = true;

    private Button filePathButton;
    private Screen previousScreen;

    public SaveReplayScreen(Screen previousScreen, Path recordFolder, String replayName) {
        super(FlashbackTextComponents.SAVE_REPLAY);
        this.recordFolder = recordFolder;
        this.setReplayName(replayName);

        if (this.savePath == null) {
            this.savePath = Flashback.getReplayFolder().resolve(UUID.randomUUID() + ".zip");
        }

        if (!(previousScreen instanceof SaveReplayScreen)) {
            this.previousScreen = previousScreen;
        }
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

        rowHelper.addChild(new StringWidget(204, 20, FlashbackTextComponents.SAVE_REPLAY, this.font), 2);

        rowHelper.addChild(new BottomTextWidget(204, 10, FlashbackTextComponents.REPLAY_NAME, this.font).alignLeft(), 2);

        EditBox replayNameEditBox = new EditBox(this.font, 0, 0, 204, 20, Component.literal(this.replayName));
        replayNameEditBox.setMaxLength(128);
        replayNameEditBox.setValue(this.replayName);
        replayNameEditBox.setResponder(this::setReplayName);
        rowHelper.addChild(replayNameEditBox, 2);

        rowHelper.addChild(new BottomTextWidget(204, 10, FlashbackTextComponents.FILENAME, this.font).alignLeft(), 2);

        this.filePathButton = Button.builder(Component.literal(this.savePath.getFileName().toString()), button -> {
            CompletableFuture<String> future = AsyncFileDialogs.saveFileDialog(this.savePath.getParent().toString(), this.savePath.getFileName().toString(),
                "Replay Archive", "zip");
            future.thenAccept(pathStr -> {
                if (pathStr != null) {
                    this.savePath = Path.of(pathStr);
                    this.generateFileFromReplayName = false;
                }
            });
        }).width(204).build();
        rowHelper.addChild(this.filePathButton, 2);

        rowHelper.addChild(new BottomTextWidget(204, 10, CommonComponents.EMPTY, this.font), 2);

        rowHelper.addChild(Button.builder(FlashbackTextComponents.SAVE_REPLAY, button -> {
            this.saveReplay();
            Minecraft.getInstance().setScreen(this.previousScreen);
        }).width(98).build(), 1);
        rowHelper.addChild(Button.builder(FlashbackTextComponents.DELETE_REPLAY, button -> {
            Minecraft.getInstance().setScreen(new ConfirmScreen(value -> {
                if (value) {
                    this.deleteReplay();
                    Minecraft.getInstance().setScreen(this.previousScreen);
                } else {
                    Minecraft.getInstance().setScreen(SaveReplayScreen.this);
                }
            }, FlashbackTextComponents.CONFIRM_DELETE_REPLAY, FlashbackTextComponents.CONFIRM_DELETE_REPLAY_MESSAGE));
        }).width(98).build(), 1);
        gridLayout.arrangeElements();
        FrameLayout.alignInRectangle(gridLayout, 0, 0, this.width, this.height, 0.5f, 0.5f);
        gridLayout.visitWidgets(this::addRenderableWidget);

        this.setInitialFocus(replayNameEditBox);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (!Files.exists(this.recordFolder)) {
            Minecraft.getInstance().setScreen(new AlertScreen(() -> Minecraft.getInstance().setScreen(this.previousScreen),
                FlashbackTextComponents.ERROR_SAVING_REPLAY, FlashbackTextComponents.RECORDING_FOLDER_MISSING));
        }
    }

    private void saveReplay() {
        if (!Files.exists(this.recordFolder)) {
            Minecraft.getInstance().setScreen(new AlertScreen(() -> Minecraft.getInstance().setScreen(this.previousScreen),
                FlashbackTextComponents.ERROR_SAVING_REPLAY, FlashbackTextComponents.RECORDING_FOLDER_MISSING));
            return;
        }

        ReplayExporter.export(this.recordFolder, this.savePath, this.replayName);
        Flashback.removePendingReplaySave(this.recordFolder);
    }

    private void deleteReplay() {
        try {
            FileUtils.deleteDirectory(this.recordFolder.toFile());
        } catch (Exception e) {
            Flashback.LOGGER.error("Exception deleting record folder", e);
        }

        Flashback.removePendingReplaySave(this.recordFolder);
    }

    private void setReplayName(String replayName) {
        this.replayName = replayName;

        if (!this.replayName.isEmpty() && (this.savePath == null || this.generateFileFromReplayName)) {
            Path replayDir = Flashback.getReplayFolder();

            if (!Files.exists(replayDir)) {
                try {
                    Files.createDirectories(replayDir);
                } catch (IOException ignored) {}
            }

            String filename;

            try {
                filename = FileUtil.findAvailableName(replayDir, this.replayName.replace(' ', '_'), ".zip");
            } catch (IOException e) {
                Flashback.LOGGER.error("Error while trying to determine filename", e);
                filename = UUID.randomUUID() + ".zip";
            }

            this.savePath = replayDir.resolve(filename);
            if (this.filePathButton != null) {
                this.filePathButton.setMessage(Component.literal(this.savePath.getFileName().toString()));
            }
        }
    }


}
