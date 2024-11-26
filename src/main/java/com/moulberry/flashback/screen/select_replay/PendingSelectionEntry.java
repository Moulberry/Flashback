package com.moulberry.flashback.screen.select_replay;

import com.moulberry.flashback.screen.ReplaySummary;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.Locale;

public interface PendingSelectionEntry {

    ReplaySelectionEntry createEntry(ReplaySelectionList replaySelectionList, Minecraft minecraft);
    boolean matchesFilter(String filter);

    record Folder(Path path, String filename, long modifiedTime, int replayCount) implements PendingSelectionEntry {
        public Folder(Path path, long modifiedTime, int replayCount) {
            this(path, path.getFileName().toString().toLowerCase(Locale.ROOT), modifiedTime, replayCount);
        }

        @Override
        public ReplaySelectionEntry createEntry(ReplaySelectionList replaySelectionList, Minecraft minecraft) {
            return new ReplaySelectionEntry.ReplayFolder(replaySelectionList, minecraft, this.path, this.modifiedTime, this.replayCount);
        }

        @Override
        public boolean matchesFilter(String filter) {
            return this.filename.contains(filter);
        }
    }

    record Replay(ReplaySummary summary) implements PendingSelectionEntry {
        @Override
        public ReplaySelectionEntry createEntry(ReplaySelectionList replaySelectionList, Minecraft minecraft) {
            return new ReplaySelectionEntry.ReplayListEntry(replaySelectionList, minecraft, this.summary);
        }

        @Override
        public boolean matchesFilter(String filter) {
            if (this.summary.getReplayName().toLowerCase(Locale.ROOT).contains(filter) ||
                this.summary.getReplayId().toLowerCase(Locale.ROOT).contains(filter)) {
                return true;
            }
            String worldName = this.summary.getWorldName();
            if (worldName != null) {
                return worldName.toLowerCase(Locale.ROOT).contains(filter);
            } else {
                return false;
            }
        }
    }


}
