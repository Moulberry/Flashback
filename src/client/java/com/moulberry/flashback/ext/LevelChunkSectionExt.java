package com.moulberry.flashback.ext;

import com.moulberry.flashback.ChunkSectionHash;

public interface LevelChunkSectionExt {

    ChunkSectionHash flashback$hashState();
    boolean flashback$doesHashedStateMatch(ChunkSectionHash hashedState);

}
