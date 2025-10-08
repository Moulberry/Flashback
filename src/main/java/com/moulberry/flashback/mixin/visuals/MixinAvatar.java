package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Avatar.class)
public abstract class MixinAvatar extends LivingEntity {

    protected MixinAvatar(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "isModelPartShown", at = @At("HEAD"), cancellable = true)
    public void isModelPartShown(PlayerModelPart playerModelPart, CallbackInfoReturnable<Boolean> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            var skinOverride = editorState.skinOverride.get(this.uuid);
            var skinOverrideFile = editorState.skinOverrideFromFile.get(this.uuid);
            if (skinOverride != null || skinOverrideFile != null) {
                cir.setReturnValue(true);
            }
        }
    }

}
