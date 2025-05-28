package com.moulberry.flashback.ext;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public interface LevelChunkExt {

    BlockState flashback$setBlockStateWithoutUpdates(BlockPos blockPos, BlockState blockState);
    int flashback$getCachedChunkId();
    void flashback$setCachedChunkId(int id);

}
