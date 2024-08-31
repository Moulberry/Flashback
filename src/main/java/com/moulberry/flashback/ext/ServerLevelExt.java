package com.moulberry.flashback.ext;

public interface ServerLevelExt {

    void flashback$setSeedHash(long seedHash);
    long flashback$getSeedHash();

    boolean flashback$shouldSendChunk(long pos);
    void flashback$markChunkAsSendable(long pos);

    void flashback$setCanSpawnEntities(boolean canSpawnEntities);

}
