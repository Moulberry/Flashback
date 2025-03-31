package com.moulberry.flashback.mixin.visuals;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.FilePlayerSkin;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer extends Player {

    @Shadow
    public abstract @Nullable PlayerInfo getPlayerInfo();

    @Unique
    private @Nullable PlayerInfo fallbackPlayerInfo;

    public MixinAbstractClientPlayer(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    public void postInit(ClientLevel clientLevel, GameProfile gameProfile, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            if (Minecraft.getInstance().getConnection() != null) {
                try {
                    this.getPlayerInfo();
                } catch (Exception ignored) {}
            }
            this.fallbackPlayerInfo = new PlayerInfo(gameProfile, false);
        }
    }

    @Inject(method = "getPlayerInfo", at = @At("RETURN"), cancellable = true)
    public void getPlayerInfo(CallbackInfoReturnable<PlayerInfo> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(this.fallbackPlayerInfo);
        }
    }

    @Unique
    private PlayerInfo skinOverridePlayerInfo = null;

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true, require = 0)
    public void getSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            FilePlayerSkin filePlayerSkin = editorState.skinOverrideFromFile.get(this.getUUID());
            if (filePlayerSkin != null) {
                cir.setReturnValue(filePlayerSkin.getSkin());
                return;
            }

            GameProfile skinOverride = editorState.skinOverride.get(this.getUUID());
            if (skinOverride != null) {
                if (skinOverridePlayerInfo == null || skinOverridePlayerInfo.getProfile() != skinOverride) {
                    skinOverridePlayerInfo = new PlayerInfo(skinOverride, false);
                }
                cir.setReturnValue(skinOverridePlayerInfo.getSkin());
            }
        }
    }

}
