/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package com.moulberry.flashback.screen;

import com.moulberry.flashback.record.FlashbackMeta;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class ReplaySummary implements Comparable<ReplaySummary> {
    private final Path path;
    private final FlashbackMeta metadata;
    private final String replayId;
    private final long lastModified;
    private final long filesize;
    private final byte[] iconBytes;
    private Component info = null;
    private Component hoverInfo = null;
    private boolean canOpen = true;
    private boolean hasWarning = false;
    private boolean hasNamespaceMismatch = false;

    public ReplaySummary(Path path, FlashbackMeta metadata, LinkedHashMap<String, LinkedHashSet<String>> currentNamespacesForRegistries, String replayId, long lastModified, long filesize, @Nullable byte[] iconBytes) {
        this.path = path;
        this.metadata = metadata;
        this.replayId = replayId;
        this.lastModified = lastModified;
        this.filesize = filesize;
        this.iconBytes = iconBytes;

        if (metadata.namespacesForRegistries != null && !currentNamespacesForRegistries.equals(metadata.namespacesForRegistries)) {
            this.hasNamespaceMismatch = true;
        }
        if (metadata.protocolVersion != 0 && metadata.protocolVersion != SharedConstants.getProtocolVersion()) {
            this.canOpen = false;

            if (metadata.versionString != null && !metadata.versionString.equals(SharedConstants.VERSION_STRING)) {
                this.hoverInfo = Component.translatable("flashback.incompatible_replay_version",
                    Component.literal(metadata.versionString),
                    Component.literal(SharedConstants.VERSION_STRING));
            } else {
                this.hoverInfo = Component.translatable("flashback.incompatible_replay_version_protocol",
                    Component.literal(String.valueOf(metadata.protocolVersion)),
                    Component.literal(String.valueOf(SharedConstants.getProtocolVersion())));
            }
        } else if (metadata.dataVersion != 0 && metadata.dataVersion != SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            this.hasWarning = true;

            if (metadata.versionString != null && !metadata.versionString.equals(SharedConstants.VERSION_STRING)) {
                this.hoverInfo = Component.translatable("flashback.maybe_incompatible_replay_version",
                    Component.literal(metadata.versionString),
                    Component.literal(SharedConstants.VERSION_STRING));
            } else {
                this.hoverInfo = Component.translatable("flashback.incompatible_replay_version_data",
                    Component.literal(String.valueOf(metadata.dataVersion)),
                    Component.literal(String.valueOf(SharedConstants.getCurrentVersion().getDataVersion().getVersion())));
            }
        } else if (this.hasNamespaceMismatch) {
            this.hasWarning = true;
            this.hoverInfo = Component.translatable("flashback.maybe_incompatible_registry_data");
        }
    }

    public Path getPath() {
        return this.path;
    }

    public String getReplayId() {
        return this.replayId;
    }

    public FlashbackMeta getReplayMetadata() {
        return this.metadata;
    }

    public String getReplayName() {
        return this.metadata.name.isEmpty() ? this.replayId : this.metadata.name;
    }

    public @Nullable String getWorldName() {
        return this.metadata.worldName;
    }

    public long getLastModified() {
        return this.lastModified;
    }

    public long getFilesize() {
        return filesize;
    }

    public Component getInfo() {
        if (this.info == null) {
            MutableComponent mutable = Component.empty();
            if (this.metadata.totalTicks > 0) {
                int ticks = this.metadata.totalTicks;
                int seconds = (ticks / 20) % 60;
                int minutes = (ticks / 20 / 60) % 60;
                int hours = ticks / 20 / 60 / 60;

                StringBuilder builder = new StringBuilder();
                if (hours > 0) {
                    builder.append(hours).append('h');
                }
                if (minutes > 0) {
                    if (!builder.isEmpty()) {
                        builder.append(' ');
                    }
                    builder.append(minutes).append('m');
                }
                if (seconds > 0) {
                    if (!builder.isEmpty()) {
                        builder.append(' ');
                    }
                    builder.append(seconds).append('s');
                }
                if (builder.isEmpty()) {
                    builder.append("0s");
                }

                mutable.append(Component.translatable("flashback.select_replay.duration", builder));
            }
            this.info = mutable;
        }

        return this.info;
    }

    public byte[] getIconBytes() {
        return this.iconBytes;
    }

    public Component getHoverInfo() {
        return this.hoverInfo;
    }

    public boolean hasWarning() {
        return this.hasWarning;
    }

    public boolean hasNamespaceMismatch() {
        return this.hasNamespaceMismatch;
    }

    public boolean canOpen() {
        return this.canOpen;
    }

    @Override
    public int compareTo(ReplaySummary levelSummary) {
        if (this.getLastModified() < levelSummary.getLastModified()) {
            return 1;
        }
        if (this.getLastModified() > levelSummary.getLastModified()) {
            return -1;
        }
        return this.replayId.compareTo(levelSummary.replayId);
    }

}

