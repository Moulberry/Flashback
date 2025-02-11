package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
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
            var skinOverride = editorState.skinOverride.get(this.uuid);
            var skinOverrideFile = editorState.skinOverrideFromFile.get(this.uuid);
            if (skinOverride != null || skinOverrideFile != null) {
                cir.setReturnValue(true);
            }
        }
    }

    @WrapOperation(method = "getDisplayName", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"))
    public MutableComponent getDisplayName_formatNameForTeam(Team team, Component component, Operation<MutableComponent> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && team instanceof PlayerTeam playerTeam) {
            boolean hidePrefix = editorState.hideTeamPrefix.contains(this.uuid) && !Utils.isComponentEmpty(playerTeam.getPlayerPrefix());
            boolean hideSuffix = editorState.hideTeamSuffix.contains(this.uuid) && !Utils.isComponentEmpty(playerTeam.getPlayerSuffix());

            if (hidePrefix || hideSuffix) {
                MutableComponent mutableComponent = Component.empty();

                if (!hidePrefix) {
                    mutableComponent = mutableComponent.append(playerTeam.getPlayerPrefix());
                }

                mutableComponent = mutableComponent.append(component);

                if (!hideSuffix) {
                    mutableComponent = mutableComponent.append(playerTeam.getPlayerSuffix());
                }

                ChatFormatting chatFormatting = playerTeam.getColor();
                if (chatFormatting != ChatFormatting.RESET) {
                    mutableComponent.withStyle(chatFormatting);
                }

                return mutableComponent;
            }
        }
        return original.call(team, component);
    }


}
