package com.moulberry.flashback.io;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.action.ActionRegistry;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ReplayReader {

    private final FriendlyByteBuf friendlyByteBuf;
    private final int replaySnapshotOffset;
    private final int replayActionsOffset;
    private RegistryAccess registryAccess;
    private ResourceLocation lastActionName = null;
    private final Int2ObjectMap<Action> actions = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<ResourceLocation> ignoredActions = new Int2ObjectOpenHashMap<>();

    public ReplayReader(ByteBuf byteBuf, RegistryAccess registryAccess) {
        this.friendlyByteBuf = new FriendlyByteBuf(byteBuf);
        this.registryAccess = registryAccess;

        int magic = this.friendlyByteBuf.readInt();
        if (magic != Flashback.MAGIC) {
            throw new RuntimeException("Invalid magic");
        }

        int actions = this.friendlyByteBuf.readVarInt();
        for (int i = 0; i < actions; i++) {
            ResourceLocation actionName = this.friendlyByteBuf.readResourceLocation();
            Action action = ActionRegistry.getAction(actionName);

            if (action == null) {
                if (actionName.getPath().endsWith("optional")) {
                    this.ignoredActions.put(i, actionName);
                } else {
                    throw new RuntimeException("Missing action: " + actionName);
                }
            } else {
                this.actions.put(i, action);
            }
        }

        int snapshotSize = this.friendlyByteBuf.readInt();
        if (snapshotSize < 0) {
            throw new RuntimeException("Invalid snapshot size: " + snapshotSize + " (0x" + Integer.toHexString(snapshotSize) + ")");
        }
        this.replaySnapshotOffset = this.friendlyByteBuf.readerIndex();
        this.friendlyByteBuf.skipBytes(snapshotSize);
        this.replayActionsOffset = this.friendlyByteBuf.readerIndex();
    }

    public void changeRegistryAccess(RegistryAccess registryAccess) {
        this.registryAccess = registryAccess;
    }

    public void resetToStart() {
        this.friendlyByteBuf.readerIndex(this.replayActionsOffset);
    }

    public void handleSnapshot(ReplayServer replayServer) {
        this.friendlyByteBuf.readerIndex(this.replaySnapshotOffset);

        replayServer.isProcessingSnapshot = true;

        while (this.friendlyByteBuf.readerIndex() < this.replayActionsOffset) {
            int id = this.friendlyByteBuf.readVarInt();
            Action action = this.actions.get(id);
            if (action == null) {
                if (this.ignoredActions.containsKey(id)) {
                    this.lastActionName = this.ignoredActions.get(id);
                    int size = this.friendlyByteBuf.readInt();
                    this.friendlyByteBuf.skipBytes(size);
                    continue;
                }
                throw new RuntimeException("Unknown action id: " + id + ". Last action was " + this.lastActionName);
            }
            this.lastActionName = action.name();

            int size = this.friendlyByteBuf.readInt();
            ByteBuf slice = this.friendlyByteBuf.readSlice(size);
            RegistryFriendlyByteBuf registryFriendlyByteBuf = new RegistryFriendlyByteBuf(slice, this.registryAccess);
            action.handle(replayServer, registryFriendlyByteBuf);

            if (slice.readerIndex() < slice.writerIndex()) {
                throw new RuntimeException("Action " + this.lastActionName + " failed to fully read. Had " + slice.writerIndex() + " bytes available, only read " + slice.readerIndex());
            }
        }

        replayServer.isProcessingSnapshot = false;
    }

    public boolean handleNextAction(ReplayServer replayServer) {
        if (this.friendlyByteBuf.readerIndex() >= this.friendlyByteBuf.writerIndex()) {
            return false;
        }
        if (this.friendlyByteBuf.readerIndex() < this.replayActionsOffset) {
            this.friendlyByteBuf.readerIndex(this.replayActionsOffset);
        }

        int id = this.friendlyByteBuf.readVarInt();
        Action action = this.actions.get(id);
        if (action == null) {
            if (this.ignoredActions.containsKey(id)) {
                this.lastActionName = this.ignoredActions.get(id);
                int size = this.friendlyByteBuf.readInt();
                this.friendlyByteBuf.skipBytes(size);
                return true;
            }
            throw new RuntimeException("Unknown action id: " + id + ". Last action was " + this.lastActionName);
        }
        this.lastActionName = action.name();

        int size = this.friendlyByteBuf.readInt();
        ByteBuf slice = this.friendlyByteBuf.readSlice(size);
        RegistryFriendlyByteBuf registryFriendlyByteBuf = new RegistryFriendlyByteBuf(slice, this.registryAccess);
        action.handle(replayServer, registryFriendlyByteBuf);

        if (slice.readerIndex() < slice.writerIndex()) {
            throw new RuntimeException("Action " + this.lastActionName + " failed to fully read. Had " + slice.writerIndex() + " bytes available, only read " + slice.readerIndex());
        }

        return true;
    }

}
