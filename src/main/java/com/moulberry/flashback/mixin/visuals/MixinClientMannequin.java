package com.moulberry.flashback.mixin.visuals;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.FilePlayerSkin;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientMannequin.class)
public abstract class MixinClientMannequin extends LivingEntity {

    @Unique
    private PlayerInfo skinOverridePlayerInfo = null;

    public MixinClientMannequin(EntityType<Mannequin> entityType, Level level) {
        super(entityType, level);
    }

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
