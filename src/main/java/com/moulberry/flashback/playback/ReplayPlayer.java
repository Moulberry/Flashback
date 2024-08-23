package com.moulberry.flashback.playback;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Collection;
import java.util.UUID;

public class ReplayPlayer extends ServerPlayer {
    public boolean followLocalPlayerNextTick = false;
    public UUID spectatingUuid = null;

    public ReplayPlayer(MinecraftServer minecraftServer, ServerLevel serverLevel, GameProfile gameProfile, ClientInformation clientInformation) {
        super(minecraftServer, serverLevel, gameProfile, clientInformation);
    }

    @Override
    public int awardRecipes(Collection<RecipeHolder<?>> collection) {
        return 0;
    }

    @Override
    public void awardStat(Stat<?> stat, int i) {
    }


}
