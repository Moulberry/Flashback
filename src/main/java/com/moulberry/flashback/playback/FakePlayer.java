package com.moulberry.flashback.playback;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.ext.ServerLevelExt;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Collection;
import java.util.UUID;

public class FakePlayer extends ServerPlayer {

    public FakePlayer(MinecraftServer minecraftServer, ServerLevel serverLevel, GameProfile gameProfile, ClientInformation clientInformation) {
        super(minecraftServer, serverLevel, gameProfile, clientInformation);
    }

    @Override
    public void sendChatMessage(OutgoingChatMessage outgoingChatMessage, boolean bl, ChatType.Bound bound) {
    }

    @Override
    public void sendServerStatus(ServerStatus serverStatus) {
    }

    @Override
    public int awardRecipes(Collection<RecipeHolder<?>> collection) {
        return 0;
    }

    @Override
    public void awardStat(Stat<?> stat, int i) {
    }

    @Override
    public void resetStat(Stat<?> stat) {
    }
}
