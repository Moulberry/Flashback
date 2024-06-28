package com.moulberry.flashback.io;

import com.moulberry.flashback.FlashbackClient;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.action.ActionRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.List;
import java.util.Objects;

public class ReplayWriter {

    private FriendlyByteBuf snapshotBuffer = null;
    private final FriendlyByteBuf writeBuffer;
    private final ByteBuf dataBufferInner;
    private RegistryFriendlyByteBuf dataBuffer;
    private Reference2IntMap<Action> registeredActions;
    private Action writingAction = null;

    private static final int STATE_EMPTY = 0;
    private static final int STATE_WRITING_SNAPSHOT = 1;
    private static final int STATE_WRITING_DATA = 2;
    private int state = STATE_EMPTY;

    public ReplayWriter(RegistryAccess registryAccess) {
        this.writeBuffer = new FriendlyByteBuf(Unpooled.buffer());
        this.dataBufferInner = Unpooled.buffer();
        this.dataBuffer = new RegistryFriendlyByteBuf(this.dataBufferInner, registryAccess);
        this.writeHeader();
    }

    private void writeHeader() {
        this.registeredActions = new Reference2IntOpenHashMap<>();
        this.registeredActions.defaultReturnValue(-1);

        // Reset to start
        this.writeBuffer.writerIndex(0);

        // Magic
        this.writeBuffer.writeInt(FlashbackClient.MAGIC);

        // Actions
        List<Action> actions = ActionRegistry.getActions();
        this.writeBuffer.writeVarInt(actions.size());
        for (Action action : ActionRegistry.getActions()) {
            this.writeBuffer.writeResourceLocation(action.name());
            this.registeredActions.put(action, this.registeredActions.size());
        }

        this.state = STATE_EMPTY;
    }

    public void setRegistryAccess(RegistryAccess registryAccess) {
        RegistryFriendlyByteBuf newDataBuffer = new RegistryFriendlyByteBuf(this.dataBufferInner, registryAccess);
        newDataBuffer.writerIndex(this.dataBuffer.writerIndex());
        newDataBuffer.readerIndex(this.dataBuffer.readerIndex());
        this.dataBuffer = newDataBuffer;
    }

    public void startSnapshot() {
        if (this.state == STATE_EMPTY) {
            this.state = STATE_WRITING_SNAPSHOT;
            this.snapshotBuffer = new FriendlyByteBuf(Unpooled.buffer());
        } else {
            throw new IllegalStateException("Can only start snapshot in STATE_EMPTY");
        }
    }

    public void endSnapshot() {
        if (this.state == STATE_WRITING_SNAPSHOT) {
            this.state = STATE_WRITING_DATA;

            byte[] written = new byte[this.snapshotBuffer.writerIndex()];
            this.snapshotBuffer.getBytes(0, written);
            this.snapshotBuffer.writerIndex(0);

            this.writeBuffer.writeVarInt(written.length);
            this.writeBuffer.writeBytes(written);
        } else {
            throw new IllegalStateException("Can only end snapshot in STATE_WRITING_SNAPSHOT");
        }
    }

    private FriendlyByteBuf getWriteBuffer() {
        if (this.state == STATE_EMPTY) {
            throw new IllegalStateException("No write buffer in STATE_EMPTY");
        } else if (this.state == STATE_WRITING_SNAPSHOT) {
            return this.snapshotBuffer;
        } else if (this.state == STATE_WRITING_DATA) {
            return this.writeBuffer;
        } else {
            throw new IllegalStateException("Unknown state: " + this.state);
        }
    }

    public void startAndFinishAction(Action action) {
        Objects.requireNonNull(action);
        if (this.writingAction != null) {
            throw new RuntimeException("startAndFinishAction() called while still writing " + action.name());
        }

        FriendlyByteBuf writeBuffer = this.getWriteBuffer();
        int id = this.registeredActions.getInt(action);
        if (id >= 0) {
            writeBuffer.writeVarInt(id);
        } else {
            throw new RuntimeException("Unknown action: " + action.name());
        }
        writeBuffer.writeVarInt(0);
    }

    public void startAction(Action action) {
        Objects.requireNonNull(action);
        if (this.writingAction != null) {
            throw new RuntimeException("startAction() called while still writing " + action.name());
        }
        this.writingAction = action;

        FriendlyByteBuf writeBuffer = this.getWriteBuffer();
        int id = this.registeredActions.getInt(action);
        if (id >= 0) {
            writeBuffer.writeVarInt(id);
        } else {
            throw new RuntimeException("Unknown action: " + action.name());
        }
    }

    public void finishAction(Action action) {
        Objects.requireNonNull(action);
        if (this.writingAction == null) {
            throw new RuntimeException("finishAction() called before startAction()");
        }
        if (this.writingAction != action) {
            throw new RuntimeException("finishAction() called with wrong action, expected " + this.writingAction.name() + ", got " + action.name());
        }
        this.writingAction = null;

        byte[] written = new byte[this.dataBuffer.writerIndex()];
        this.dataBuffer.getBytes(0, written);
        this.dataBuffer.writerIndex(0);

        FriendlyByteBuf writeBuffer = this.getWriteBuffer();
        writeBuffer.writeVarInt(written.length);
        writeBuffer.writeBytes(written);
    }

    public RegistryFriendlyByteBuf friendlyByteBuf() {
        return this.dataBuffer;
    }

    public byte[] popBytes() {
        byte[] replay = new byte[this.writeBuffer.writerIndex()];
        this.writeBuffer.getBytes(0, replay);

        this.writeHeader();

        return replay;
    }

}
