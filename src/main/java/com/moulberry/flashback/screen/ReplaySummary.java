/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package com.moulberry.flashback.screen;

import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.record.FlashbackMeta;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class ReplaySummary implements Comparable<ReplaySummary> {
    private final Path path;
    private final FlashbackMeta metadata;
    private final String replayId;
    private final long lastOpened;
    private final byte[] iconBytes;
    private Component info = null;

    public ReplaySummary(Path path, FlashbackMeta metadata, String replayId, long lastOpened, @Nullable byte[] iconBytes) {
        this.path = path;
        this.metadata = metadata;
        this.replayId = replayId;
        this.lastOpened = lastOpened;
        this.iconBytes = iconBytes;
    }

    public Path getPath() {
        return this.path;
    }

    public String getReplayId() {
        return this.replayId;
    }

    public String getReplayName() {
        return this.metadata.name.isEmpty() ? this.replayId : this.metadata.name;
    }

    public long getLastPlayed() {
        return this.lastOpened;
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

                mutable.append(Component.translatable("flashback.select_replay.duration", builder));
            }
            this.info = mutable;
        }

        return this.info;
    }

    public byte[] getIconBytes() {
        return this.iconBytes;
    }

    public boolean canOpen() {
        return true;
    }

    @Override
    public int compareTo(ReplaySummary levelSummary) {
        if (this.getLastPlayed() < levelSummary.getLastPlayed()) {
            return 1;
        }
        if (this.getLastPlayed() > levelSummary.getLastPlayed()) {
            return -1;
        }
        return this.replayId.compareTo(levelSummary.replayId);
    }

}

