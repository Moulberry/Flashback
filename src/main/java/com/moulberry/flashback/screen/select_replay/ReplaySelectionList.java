/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package com.moulberry.flashback.screen.select_replay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.SneakyThrow;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.screen.ReplaySummary;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ReplaySelectionList extends ObjectSelectionList<ReplaySelectionEntry> {
    private final SelectReplayScreen screen;
    private CompletableFuture<List<PendingSelectionEntry>> pendingReplays;
    @Nullable
    private List<PendingSelectionEntry> currentlyDisplayedReplays;
    private String filter;
    private ReplaySorting replaySorting;
    private boolean sortDescending;
    private final ReplaySelectionEntry.LoadingHeader loadingHeader;
    private final ReplaySelectionEntry.LoadFromDeviceHeader loadFromDeviceHeader;

    public ReplaySelectionList(SelectReplayScreen selectReplayScreen, Minecraft minecraft, int i, int j, int k, int l,
            String filter, ReplaySorting replaySorting, boolean sortDescending,
            @Nullable ReplaySelectionList replaySelectionList) {
        super(minecraft, i, j, k, l);
        this.screen = selectReplayScreen;
        this.loadingHeader = new ReplaySelectionEntry.LoadingHeader(minecraft);
        this.loadFromDeviceHeader = new ReplaySelectionEntry.LoadFromDeviceHeader(minecraft);
        this.filter = filter;
        this.replaySorting = replaySorting;
        this.sortDescending = sortDescending;
        this.pendingReplays = replaySelectionList != null ? replaySelectionList.pendingReplays : this.loadReplays();
        this.fillLoadingReplays();
    }

    @Override
    protected void clearEntries() {
        this.children().forEach(ReplaySelectionEntry::close);
        super.clearEntries();
    }

    @Nullable
    private List<PendingSelectionEntry> pollReplaysIgnoreErrors() {
        try {
            return this.pendingReplays.getNow(null);
        } catch (CancellationException | CompletionException runtimeException) {
            return null;
        }
    }

    public void reloadReplayList() {
        this.pendingReplays = this.loadReplays();
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        if (CommonInputs.selected(i)) {
            ReplaySelectionEntry.ReplayListEntry replayListEntry = this.getReplayListEntry();
            if (replayListEntry != null && replayListEntry.canOpen()) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                replayListEntry.openReplay();
                return true;
            }
        }

        return super.keyPressed(i, j, k);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        List<PendingSelectionEntry> list = this.pollReplaysIgnoreErrors();
        if (this.currentlyDisplayedReplays != list) {
            if (list == null) {
                this.fillLoadingReplays();
            } else {
                list.sort(this.replaySorting.comparator(this.sortDescending));
                this.fillReplays(this.filter, list);
            }

            this.currentlyDisplayedReplays = list;
        }
        super.renderWidget(guiGraphics, i, j, f);
    }

    public void updateFilter(String filter, ReplaySorting replaySorting, boolean sortDescending) {
        if (this.currentlyDisplayedReplays != null) {
            if (this.replaySorting != replaySorting || this.sortDescending != sortDescending) {
                this.currentlyDisplayedReplays.sort(replaySorting.comparator(sortDescending));
                this.fillReplays(filter, this.currentlyDisplayedReplays);
            } else if (!filter.equals(this.filter)) {
                this.fillReplays(filter, this.currentlyDisplayedReplays);
            }
        }
        this.filter = filter;
        this.replaySorting = replaySorting;
        this.sortDescending = sortDescending;
    }

    private CompletableFuture<List<PendingSelectionEntry>> loadReplays() {
        Path replayDir = this.screen.path;

        if (!Files.exists(replayDir) || !Files.isDirectory(replayDir)) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        ArrayList<CompletableFuture<PendingSelectionEntry>> futures = new ArrayList<>();

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(replayDir)) {
            for (Path path : directoryStream) {
                if (Files.isDirectory(path)) {
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            BasicFileAttributeView attributeView = Files.getFileAttributeView(path, BasicFileAttributeView.class);
                            BasicFileAttributes basicFileAttributes = attributeView.readAttributes();

                            long lastModified = Math.max(basicFileAttributes.creationTime().toMillis(), basicFileAttributes.lastModifiedTime().toMillis());

                            int replaysInFolder = 0;
                            try (DirectoryStream<Path> filesInFolder = Files.newDirectoryStream(path)) {
                                for (Path fileInFolder : filesInFolder) {
                                    if (fileInFolder.toString().endsWith(".zip")) {
                                        replaysInFolder += 1;
                                    }
                                }
                            }

                            return new PendingSelectionEntry.Folder(path, lastModified, replaysInFolder);
                        } catch (IOException e) {
                            Flashback.LOGGER.error("Failed to load replay folder", e);
                        }
                        return null;
                    }, Util.backgroundExecutor()));
                    continue;
                }

                if (!path.toString().endsWith(".zip")) {
                    continue;
                }

                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        String fileName = path.getFileName().toString();

                        BasicFileAttributeView attributeView = Files.getFileAttributeView(path, BasicFileAttributeView.class);
                        BasicFileAttributes basicFileAttributes = attributeView.readAttributes();

                        long lastModified = Math.max(basicFileAttributes.creationTime().toMillis(), basicFileAttributes.lastModifiedTime().toMillis());
                        long filesize = basicFileAttributes.size();

                        byte[] iconBytes = null;
                        String metadataString = null;

                        try (FileSystem fs = FileSystems.newFileSystem(path)) {
                            Path iconPath = fs.getPath("/icon.png");
                            if (Files.exists(iconPath)) {
                                iconBytes = Files.readAllBytes(iconPath);
                            }

                            Path metadataPath = fs.getPath("/metadata.json");
                            if (Files.exists(metadataPath)) {
                                metadataString = Files.readString(metadataPath);
                            }
                        } catch (IOException e) {
                            SneakyThrow.sneakyThrow(e);
                        }

                        if (metadataString == null) {
                            return null;
                        }

                        JsonObject metadataJson = new Gson().fromJson(metadataString, JsonObject.class);
                        FlashbackMeta metadata = FlashbackMeta.fromJson(metadataJson);
                        if (metadata != null) {
                            ReplaySummary summary = new ReplaySummary(path, metadata, fileName, lastModified, filesize, iconBytes);
                            return new PendingSelectionEntry.Replay(summary);
                        }
                    } catch (IOException e) {
                        Flashback.LOGGER.error("Failed to load replay", e);
                    }
                    return null;
                }, Util.backgroundExecutor()));
            }
        } catch (IOException e) {
            SneakyThrow.sneakyThrow(e);
        }

        return Util.sequenceFailFastAndCancel(futures).thenApply(list -> {
            list.removeIf(Objects::isNull);
            return list;
        });
    }

    private void fillReplays(String filter, List<PendingSelectionEntry> list) {
        this.clearEntries();
        this.addEntry(this.loadFromDeviceHeader);
        filter = filter.toLowerCase(Locale.ROOT);
        for (PendingSelectionEntry entry : list) {
            if (entry.matchesFilter(filter)) {
                this.addEntry(entry.createEntry(this, this.minecraft));
            }
        }
        this.notifyListUpdated();
    }

    private void fillLoadingReplays() {
        this.clearEntries();
        this.addEntry(this.loadFromDeviceHeader);
        this.addEntry(this.loadingHeader);
        this.notifyListUpdated();
    }

    private void notifyListUpdated() {
        this.refreshScrollAmount();
        this.screen.triggerImmediateNarration(true);
        this.screen.updateButtonStatus(null);
    }

    @Override
    public int getRowWidth() {
        return 270;
    }

    @Override
    public void setSelected(@Nullable ReplaySelectionEntry entry) {
        if (entry instanceof ReplaySelectionEntry.LoadFromDeviceHeader) {
            Flashback.openReplayFromFileBrowser();
            return;
        }

        super.setSelected(entry);

        if (entry instanceof ReplaySelectionEntry.ReplayListEntry replayListEntry) {
            this.screen.updateButtonStatus(replayListEntry.summary);
        } else {
            this.screen.updateButtonStatus(null);
        }
    }

    @Nullable
    public ReplaySelectionEntry.ReplayListEntry getReplayListEntry() {
        if (this.getSelected() instanceof ReplaySelectionEntry.ReplayListEntry replayListEntry) {
            return replayListEntry;
        } else {
            return null;
        }
    }

    public SelectReplayScreen getScreen() {
        return this.screen;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        if (this.children().contains(this.loadingHeader)) {
            this.loadingHeader.updateNarration(narrationElementOutput);
            return;
        }
        super.updateWidgetNarration(narrationElementOutput);
    }

}

