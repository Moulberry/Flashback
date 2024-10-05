package com.moulberry.flashback.playback;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.ext.ServerLevelExt;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.biome.BiomeManager;

import java.util.Collection;
import java.util.UUID;

public class ReplayPlayer extends ServerPlayer {
    public boolean followLocalPlayerNextTick = false;
    public UUID spectatingUuid = null;
    public int spectatingUuidTickCount = 0;
    public int forceRespectateTickCount = 0;

    public UUID lastFirstPersonDataUUID = null;
    public int lastFirstPersonSelectedSlot = -1;
    public ItemStack[] lastFirstPersonHotbarItems = new ItemStack[9];
    public float lastFirstPersonExperienceProgress = 0.0f;
    public int lastFirstPersonTotalExperience = 0;
    public int lastFirstPersonExperienceLevel = 0;
    public int lastFirstPersonFoodLevel = 0;
    public float lastFirstPersonSaturationLevel = 0;

    public ReplayPlayer(MinecraftServer minecraftServer, ServerLevel serverLevel, GameProfile gameProfile, ClientInformation clientInformation) {
        super(minecraftServer, serverLevel, gameProfile, clientInformation);
    }

    @Override
    public CommonPlayerSpawnInfo createCommonSpawnInfo(ServerLevel serverLevel) {
        return new CommonPlayerSpawnInfo(serverLevel.dimensionTypeRegistration(), serverLevel.dimension(),
            ((ServerLevelExt)serverLevel).flashback$getSeedHash(), this.gameMode.getGameModeForPlayer(),
            this.gameMode.getPreviousGameModeForPlayer(),
            serverLevel.isDebug(), serverLevel.isFlat(), this.getLastDeathLocation(), this.getPortalCooldown());
    }

    @Override
    public int awardRecipes(Collection<RecipeHolder<?>> collection) {
        return 0;
    }

    @Override
    public void awardStat(Stat<?> stat, int i) {
    }

    @Override
    public void indicateDamage(double d, double e) {
    }

    @Override
    public void handleDamageEvent(DamageSource damageSource) {
    }

    @Override
    public boolean hurt(DamageSource damageSource, float f) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return true;
    }
}
