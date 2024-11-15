package com.moulberry.flashback.screen.select_replay;

import com.moulberry.flashback.screen.ReplaySummary;
import net.minecraft.network.chat.Component;

import java.util.Comparator;

public enum ReplaySorting {

    CREATED_DATE(
        Component.literal("Created Date"),
        Comparator.comparingLong(ReplaySummary::getLastModified).reversed(),
        Comparator.comparingLong(ReplaySummary::getLastModified)
    ),
    REPLAY_NAME(
        Component.literal("Replay Name"),
        Comparator.comparing(ReplaySummary::getReplayName, NaturalOrderComparator.INSTANCE),
        Comparator.comparing(ReplaySummary::getReplayName, NaturalOrderComparator.INSTANCE.reversed())
    ),
    WORLD_NAME(
        Component.literal("World Name"),
        Comparator.comparing(ReplaySummary::getWorldName, Comparator.nullsLast(NaturalOrderComparator.INSTANCE)),
        Comparator.comparing(ReplaySummary::getWorldName, Comparator.nullsLast(NaturalOrderComparator.INSTANCE.reversed()))
    ),
    DURATION(
        Component.literal("Duration"),
        Comparator.<ReplaySummary>comparingLong(replay -> replay.getReplayMetadata().totalTicks).reversed(),
        Comparator.comparingLong(replay -> replay.getReplayMetadata().totalTicks)
    ),
    FILESIZE(
        Component.literal("Filesize"),
        Comparator.comparingLong(ReplaySummary::getFilesize).reversed(),
        Comparator.comparingLong(ReplaySummary::getFilesize)
    );

    private final Component component;
    private final Comparator<ReplaySummary> descendingComparator;
    private final Comparator<ReplaySummary> ascendingComparator;

    ReplaySorting(Component component, Comparator<ReplaySummary> descendingComparator, Comparator<ReplaySummary> ascendingComparator) {
        this.component = component;
        this.descendingComparator = descendingComparator.thenComparing(ReplaySummary::getReplayId, NaturalOrderComparator.INSTANCE);
        this.ascendingComparator = ascendingComparator.thenComparing(ReplaySummary::getReplayId, NaturalOrderComparator.INSTANCE);
    }

    public Component component() {
        return this.component;
    }

    public Comparator<PendingSelectionEntry> comparator(boolean descending) {
        Comparator<ReplaySummary> replayComparator;
        if (descending) {
            replayComparator = this.descendingComparator;
        } else {
            replayComparator = this.ascendingComparator;
        }
        return (entry1, entry2) -> {
            if (entry1 instanceof PendingSelectionEntry.Replay replay1) {
                if (entry2 instanceof PendingSelectionEntry.Replay replay2) {
                    return replayComparator.compare(replay1.summary(), replay2.summary());
                } else {
                    return 1;
                }
            } else if (entry1 instanceof PendingSelectionEntry.Folder folder1) {
                if (entry2 instanceof PendingSelectionEntry.Folder folder2) {
                    return NaturalOrderComparator.INSTANCE.compare(folder1.filename(), folder2.filename());
                } else {
                    return -1;
                }
            } else {
                return 0;
            }
        };
    }

}
