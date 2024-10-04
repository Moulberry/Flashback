/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package com.moulberry.flashback.screen;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.SneakyThrow;
import com.moulberry.flashback.record.FlashbackMeta;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ReplaySelectionList extends ObjectSelectionList<ReplaySelectionList.Entry> {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault());
    private static final ResourceLocation ERROR_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("world_list/error_highlighted");
    private static final ResourceLocation ERROR_SPRITE = ResourceLocation.withDefaultNamespace("world_list/error");
    private static final ResourceLocation WARNING_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("world_list/warning_highlighted");
    static private final ResourceLocation WARNING_SPRITE = ResourceLocation.withDefaultNamespace("world_list/warning");
    private static final ResourceLocation JOIN_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("world_list/join_highlighted");
    private static final ResourceLocation JOIN_SPRITE = ResourceLocation.withDefaultNamespace("world_list/join");
    private static final Logger LOGGER = LogUtils.getLogger();
    private final SelectReplayScreen screen;
    private CompletableFuture<List<ReplaySummary>> pendingReplays;
    @Nullable
    private List<ReplaySummary> currentlyDisplayedReplays;
    private String filter;
    private final LoadingHeader loadingHeader;
    private final LoadFromDeviceHeader loadFromDeviceHeader;

    private Map<UUID, Long> lastOpenTimes = null;

    public ReplaySelectionList(SelectReplayScreen selectReplayScreen, Minecraft minecraft, int i, int j, int k, int l, String string, @Nullable ReplaySelectionList replaySelectionList) {
        super(minecraft, i, j, k, l);
        this.screen = selectReplayScreen;
        this.loadingHeader = new LoadingHeader(minecraft);
        this.loadFromDeviceHeader = new LoadFromDeviceHeader(minecraft);
        this.filter = string;
        this.pendingReplays = replaySelectionList != null ? replaySelectionList.pendingReplays : this.loadReplays();
        this.handleNewReplays(this.pollReplaysIgnoreErrors());
    }

    @Override
    protected void clearEntries() {
        this.children().forEach(Entry::close);
        super.clearEntries();
    }

    @Nullable
    private List<ReplaySummary> pollReplaysIgnoreErrors() {
        try {
            return this.pendingReplays.getNow(null);
        } catch (CancellationException | CompletionException runtimeException) {
            return null;
        }
    }

    private void reloadWorldList() {
        this.pendingReplays = this.loadReplays();
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        if (CommonInputs.selected(i)) {
            ReplayListEntry replayListEntry = this.getReplayListEntry();
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
        List<ReplaySummary> list = this.pollReplaysIgnoreErrors();
        if (list != this.currentlyDisplayedReplays) {
            this.handleNewReplays(list);
        }
        super.renderWidget(guiGraphics, i, j, f);
    }

    private void handleNewReplays(@Nullable List<ReplaySummary> list) {
        if (list == null) {
            this.fillLoadingReplays();
        } else {
            this.fillReplays(this.filter, list);
        }
        this.currentlyDisplayedReplays = list;
    }

    public void updateFilter(String string) {
        if (this.currentlyDisplayedReplays != null && !string.equals(this.filter)) {
            this.fillReplays(string, this.currentlyDisplayedReplays);
        }
        this.filter = string;
    }

//    private Path getOpenTimeCachePath() {
//        Path flashbackDir = Flashback.getDataDirectory();
//        Path replayDir = flashbackDir.resolve("replays");
//        return replayDir.resolve(".lastopened.json");
//    }
//
//    private synchronized long getOpenTime(UUID uuid) {
//        if (this.lastOpenTimes == null) {
//            Path path = getOpenTimeCachePath();
//
//            if (Files.exists(path)) {
//                try {
//                    String serialized = Files.readString(path);
//                    TypeToken<Map<UUID, Long>> typeToken = (TypeToken<Map<UUID, Long>>) TypeToken.getParameterized(Map.class, UUID.class, Long.class);
//                    this.lastOpenTimes = FlashbackGson.PRETTY.fromJson(serialized, typeToken);
//                    return this.lastOpenTimes.getOrDefault(uuid, 0L);
//                } catch (IOException e) {
//                    Flashback.LOGGER.error("Unable to read last opened cache", e);
//                }
//            }
//
//            this.lastOpenTimes = new HashMap<>();
//        }
//
//        return this.lastOpenTimes.getOrDefault(uuid, 0L);
//    }
//
//    private synchronized void setOpenTime(UUID uuid, long time) {
//        this.lastOpenTimes.put(uuid, time);
//    }
//
//    private synchronized void saveOpenTimeCache() {
//        String serialized = FlashbackGson.PRETTY.toJson(this.lastOpenTimes);
//        try {
//            Files.writeString(getOpenTimeCachePath(), serialized, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
//                StandardOpenOption.CREATE);
//        } catch (IOException e) {
//            Flashback.LOGGER.error("Unable to write last opened cache", e);
//        }
//    }

    private CompletableFuture<List<ReplaySummary>> loadReplays() {
        Path flashbackDir = Flashback.getDataDirectory();
        Path replayDir = flashbackDir.resolve("replays");

        if (!Files.exists(replayDir) || !Files.isDirectory(replayDir)) {
            return CompletableFuture.completedFuture(List.of());
        }

        ArrayList<CompletableFuture<ReplaySummary>> futures = new ArrayList<>();

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(replayDir)) {
            for (Path path : directoryStream) {
                if (!path.toString().endsWith(".zip")) {
                    continue;
                }

                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        String fileName = path.getFileName().toString();

                        BasicFileAttributeView attributeView = Files.getFileAttributeView(path, BasicFileAttributeView.class);
                        BasicFileAttributes basicFileAttributes = attributeView.readAttributes();

                        long lastModified = Math.max(basicFileAttributes.creationTime().toMillis(), basicFileAttributes.lastModifiedTime().toMillis());

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
                            return new ReplaySummary(path, metadata, fileName, lastModified, iconBytes);
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

        return Util.sequenceFailFastAndCancel(futures).thenApply(list -> list.stream().filter(Objects::nonNull).sorted().toList());
    }

    private void fillReplays(String string, List<ReplaySummary> list) {
        this.clearEntries();
        this.addEntry(this.loadFromDeviceHeader);
        string = string.toLowerCase(Locale.ROOT);
        for (ReplaySummary ReplaySummary : list) {
            if (!this.filterAccepts(string, ReplaySummary)) continue;
            this.addEntry(new ReplayListEntry(this, ReplaySummary));
        }
        this.notifyListUpdated();
    }

    private boolean filterAccepts(String string, ReplaySummary ReplaySummary) {
        return ReplaySummary.getReplayName().toLowerCase(Locale.ROOT).contains(string) || ReplaySummary.getReplayId().toLowerCase(Locale.ROOT).contains(string);
    }

    private void fillLoadingReplays() {
        this.clearEntries();
        this.addEntry(this.loadFromDeviceHeader);
        this.addEntry(this.loadingHeader);
        this.notifyListUpdated();
    }

    private void notifyListUpdated() {
        this.setScrollAmount(this.getScrollAmount());
        this.screen.triggerImmediateNarration(true);
        this.screen.updateButtonStatus(null);
    }

    @Override
    public int getRowWidth() {
        return 270;
    }

    @Override
    public void setSelected(@Nullable Entry entry) {
        if (entry instanceof LoadFromDeviceHeader) {
            Flashback.openReplayFromFileBrowser();
            return;
        }

        super.setSelected(entry);

        if (entry instanceof ReplayListEntry replayListEntry) {
            this.screen.updateButtonStatus(replayListEntry.summary);
        } else {
            this.screen.updateButtonStatus(null);
        }
    }

    @Nullable
    public ReplayListEntry getReplayListEntry() {
        if (this.getSelected() instanceof ReplayListEntry replayListEntry) {
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

    public static class LoadingHeader extends Entry {
        private static final Component LOADING_LABEL = Component.translatable("flashback.select_replay.loading_replays");
        private final Minecraft minecraft;

        public LoadingHeader(Minecraft minecraft) {
            this.minecraft = minecraft;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int i, int j, int k, int l, int m, int n, int o, boolean bl, float f) {
            int p = (this.minecraft.screen.width - this.minecraft.font.width(LOADING_LABEL)) / 2;
            int q = j + (m - this.minecraft.font.lineHeight) / 2;
            guiGraphics.drawString(this.minecraft.font, LOADING_LABEL, p, q, 0xFFFFFF, false);
            String string = LoadingDotsText.get(Util.getMillis());
            int r = (this.minecraft.screen.width - this.minecraft.font.width(string)) / 2;
            int s = q + this.minecraft.font.lineHeight;
            guiGraphics.drawString(this.minecraft.font, string, r, s, -8355712, false);
        }

        @Override
        public Component getNarration() {
            return LOADING_LABEL;
        }
    }

    public static class LoadFromDeviceHeader extends Entry {
        private static final Component LOAD_REPLAY_LABEL = Component.translatable("flashback.select_replay.load_replay_from_file");
        private static final WidgetSprites SPRITES = new WidgetSprites(ResourceLocation.withDefaultNamespace("widget/button"), ResourceLocation.withDefaultNamespace("widget/button_disabled"),
            ResourceLocation.withDefaultNamespace("widget/button_highlighted"));
        private final Minecraft minecraft;

        public LoadFromDeviceHeader(Minecraft minecraft) {
            this.minecraft = minecraft;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            guiGraphics.blitSprite(SPRITES.get(true, hovered), x + 4, y + 2, width - 8, height - 4);

            int p = (this.minecraft.screen.width - this.minecraft.font.width(LOAD_REPLAY_LABEL)) / 2;
            int q = y + (height - this.minecraft.font.lineHeight) / 2 + 1;
            guiGraphics.drawString(this.minecraft.font, LOAD_REPLAY_LABEL, p, q, 0xFFFFFF, true);
        }

        @Override
        public Component getNarration() {
            return LOAD_REPLAY_LABEL;
        }
    }

    public final class ReplayListEntry extends Entry implements AutoCloseable {
        private static final int ICON_WIDTH = 32;
        private static final int ICON_HEIGHT = 32;
        private final Minecraft minecraft;
        private final SelectReplayScreen screen;
        final ReplaySummary summary;
        private final FaviconTexture icon;
        private long lastClickTime;

        public ReplayListEntry(ReplaySelectionList replaySelectionList2, ReplaySummary ReplaySummary) {
            this.minecraft = replaySelectionList2.minecraft;
            this.screen = replaySelectionList2.getScreen();
            this.summary = ReplaySummary;
            this.icon = FaviconTexture.forWorld(this.minecraft.getTextureManager(), ReplaySummary.getReplayId());
            this.loadIcon();
        }

        @Override
        public Component getNarration() {
            MutableComponent component = Component.translatable("narrator.select.world_info", this.summary.getReplayName(),
                    Component.translationArg(new Date(this.summary.getLastModified())),
                    this.summary.getInfo());
            return Component.translatable("narrator.select", component);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            String title = this.summary.getReplayName();
            String fileAndTime = this.summary.getReplayId();
            long p = this.summary.getLastModified();
            if (p != -1L) {
                fileAndTime = fileAndTime + " (" + DATE_FORMAT.format(Instant.ofEpochMilli(p)) + ")";
            }
            if (StringUtils.isEmpty(title)) {
                title = I18n.get("flashback.select_replay.replay") + " " + (index + 1);
            }
            Component info = this.summary.getInfo();

            int titleColour = 0xFFFFFF;
            if (!this.summary.canOpen()) {
                titleColour = 0xFF5555;
            } else if (this.summary.hasWarning()) {
                titleColour = 0xFFAA55;
            }

            guiGraphics.drawString(this.minecraft.font, title, x + ICON_WIDTH + 3, y + 1, titleColour, false);
            guiGraphics.drawString(this.minecraft.font, fileAndTime, x + ICON_WIDTH + 3, y + this.minecraft.font.lineHeight + 3, 0xFF808080, false);
            guiGraphics.drawString(this.minecraft.font, info, x + ICON_WIDTH + 3, y + this.minecraft.font.lineHeight + this.minecraft.font.lineHeight + 3, 0xFF808080, false);

            RenderSystem.enableBlend();
            guiGraphics.blit(this.icon.textureLocation(), x, y, 0.0f, 0.0f, 32, 32, 32, 32);
            RenderSystem.disableBlend();

            if (this.minecraft.options.touchscreen().get() || hovered) {
                guiGraphics.fill(x, y, x + ICON_WIDTH, y + ICON_HEIGHT, -1601138544);
                int q = mouseX - x;
                boolean hoveredIcon = q < 32;

                if (hovered && this.summary.getHoverInfo() != null) {
                    guiGraphics.renderTooltip(this.minecraft.font, this.minecraft.font.split(this.summary.getHoverInfo(), 240), mouseX, mouseY);
                }

                ResourceLocation iconOverlay;
                if (!this.summary.canOpen()) {
                    iconOverlay = hoveredIcon ? ERROR_HIGHLIGHTED_SPRITE : ERROR_SPRITE;
                } else if (this.summary.hasWarning()) {
                    iconOverlay = hoveredIcon ? WARNING_HIGHLIGHTED_SPRITE : WARNING_SPRITE;
                } else {
                    iconOverlay = hoveredIcon ? JOIN_HIGHLIGHTED_SPRITE : JOIN_SPRITE;
                }
                guiGraphics.blitSprite(iconOverlay, x, y, ICON_WIDTH, ICON_HEIGHT);
            }
        }

        @Override
        public boolean mouseClicked(double d, double e, int i) {
            if (!this.summary.canOpen()) {
                return true;
            }
            ReplaySelectionList.this.setSelected(this);
            if (d - (double) ReplaySelectionList.this.getRowLeft() <= 32.0 || Util.getMillis() - this.lastClickTime < 250L) {
                if (this.canOpen()) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    this.openReplay();
                }
                return true;
            }
            this.lastClickTime = Util.getMillis();
            return super.mouseClicked(d, e, i);
        }

        public boolean canOpen() {
            return this.summary.canOpen();
        }

        public void openReplay() {
            if (this.summary.canOpen()) {
                this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("flashback.select_replay.data_read")));
                Flashback.openReplayWorld(this.summary.getPath());

//                setOpenTime(this.summary.getReplayMetadata().replayIdentifier, System.currentTimeMillis());
//                saveOpenTimeCache();
            }
        }

        public void deleteReplay() {
            this.minecraft.setScreen(new ConfirmScreen(bl -> {
                if (bl) {
                    this.minecraft.setScreen(new ProgressScreen(true));
                    this.doDeleteReplay();
                }
                this.minecraft.setScreen(this.screen);
            }, Component.translatable("flashback.select_replay.delete_question"),
                Component.translatable("selectWorld.deleteWarning", this.summary.getReplayName()),
                Component.translatable("selectWorld.deleteButton"),
                CommonComponents.GUI_CANCEL)
            );
        }

        private void doDeleteReplay() {
            String id = this.summary.getReplayId();
            try {
                Files.delete(this.summary.getPath());
            } catch (IOException e) {
                SystemToast.onWorldDeleteFailure(this.minecraft, id);
                LOGGER.error("Failed to delete replay {}", id, e);
            }

            ReplaySelectionList.this.reloadWorldList();
        }

        public void editReplay() {
            Minecraft.getInstance().setScreen(new EditReplayScreen(Minecraft.getInstance().screen, this.summary));
        }

        private void loadIcon() {
            byte[] iconBytes = this.summary.getIconBytes();

            if (iconBytes != null) {
                try {
                    this.icon.upload(NativeImage.read(iconBytes));
                    return;
                } catch (Throwable t) {
                    LOGGER.error("Invalid icon for replay {}", this.summary.getReplayId(), t);
                }
            }

            this.icon.clear();
        }

        @Override
        public void close() {
            this.icon.close();
        }
    }

    public static abstract class Entry extends ObjectSelectionList.Entry<Entry> implements AutoCloseable {
        @Override
        public void close() {}
    }
}

