package com.moulberry.flashback.mixin.visuals;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity {

    protected MixinPlayer(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "isModelPartShown", at = @At("HEAD"), cancellable = true)
    public void isModelPartShown(PlayerModelPart playerModelPart, CallbackInfoReturnable<Boolean> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            GameProfile skinOverride = editorState.skinOverride.get(this.uuid);
            if (skinOverride != null) {
                cir.setReturnValue(true);
            }
        }
    }

}
