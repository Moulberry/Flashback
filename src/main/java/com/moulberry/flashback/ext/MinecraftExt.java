package com.moulberry.flashback.ext;

import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public interface MinecraftExt {

    record StartReplayServerInfo(UUID playbackUUID, Path path) {}

    void flashback$applyKeyframes();
    void flashback$startReplayServer(LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem stem, Optional<GameRules> gameRules, StartReplayServerInfo info);
    float flashback$getLocalPlayerPartialTick(float originalPartialTick);
    boolean flashback$overridingLocalPlayerTimer();

}
