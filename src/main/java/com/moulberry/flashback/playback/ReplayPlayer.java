package com.moulberry.flashback.playback;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.ext.ServerLevelExt;
import com.moulberry.flashback.packet.FlashbackClientPovChanges;
import com.moulberry.flashback.povstate.PovPlayerState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.biome.BiomeManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ReplayPlayer extends ServerPlayer {
    public boolean followLocalPlayerNextTick = false;
    public UUID spectatingUuid = null;
    public int spectatingUuidTickCount = 0;
    public int forceRespectateTickCount = 0;

    public UUID povUUID = null;
    public PovPlayerState povPlayerState = null;

    public ReplayPlayer(MinecraftServer minecraftServer, ServerLevel serverLevel, GameProfile gameProfile, ClientInformation clientInformation) {
        super(minecraftServer, serverLevel, gameProfile, clientInformation);
    }

    public void updatePovStateAndSync(UUID newUuid, PovPlayerState newState) {
        if (!Objects.equals(this.povUUID, newUuid)) {
            this.povUUID = newUuid;
            this.povPlayerState = null;
        }

        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        PovPlayerState.addChangesBetween(packets, this.povPlayerState, newState);

        if (!packets.isEmpty()) {
            this.povPlayerState = newState.copy();
            ServerPlayNetworking.send(this, new FlashbackClientPovChanges(newUuid, packets));
        }
    }

    @Override
    public CommonPlayerSpawnInfo createCommonSpawnInfo(ServerLevel serverLevel) {
        return new CommonPlayerSpawnInfo(serverLevel.dimensionTypeRegistration(), serverLevel.dimension(),
            ((ServerLevelExt)serverLevel).flashback$getSeedHash(), this.gameMode.getGameModeForPlayer(),
            Optional.ofNullable(this.gameMode.getPreviousGameModeForPlayer()),
            serverLevel.isDebug(), serverLevel.isFlat(), this.getLastDeathLocation(), this.getPortalCooldown(), serverLevel.getSeaLevel());
    }

    @Override
    public void setCamera(@Nullable Entity entity) {
        super.setCamera(entity);

        if (entity == null) {
            this.spectatingUuid = null;
        }
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

    @Override
    public void indicateDamage(double d, double e) {
    }

    @Override
    public void handleDamageEvent(DamageSource damageSource) {
    }

    @Override
    public boolean hurtClient(DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel serverLevel, DamageSource damageSource) {
        return true;
    }

}
