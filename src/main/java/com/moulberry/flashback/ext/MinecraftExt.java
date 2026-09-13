package com.moulberry.flashback.ext;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.moulberry.flashback.record.FlashbackMeta;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.UUID;

public interface MinecraftExt {

    record StartReplayServerInfo(UUID playbackUUID, FileSystem playbackFileSystem, FlashbackMeta metadata) {}

    void flashback$applyKeyframes();
    void flashback$startReplayServer(LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem stem, StartReplayServerInfo info);
    float flashback$getLocalPlayerPartialTick(float originalPartialTick);
    boolean flashback$overridingLocalPlayerTimer();

    void flashback$pushMainRenderTarget(RenderTarget renderTarget);
    void flashback$popMainRenderTarget();

}
