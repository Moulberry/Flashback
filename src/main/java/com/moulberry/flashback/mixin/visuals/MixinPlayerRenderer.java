package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.EnumSet;

@Mixin(PlayerRenderer.class)
public class MixinPlayerRenderer {

    @WrapOperation(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Scoreboard;getDisplayObjective(Lnet/minecraft/world/scores/DisplaySlot;)Lnet/minecraft/world/scores/Objective;"))
    public Objective renderNameTag(Scoreboard instance, DisplaySlot displaySlot, Operation<Objective> original, @Local(argsOnly = true) AbstractClientPlayer abstractClientPlayer) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.hideBelowName.contains(abstractClientPlayer.getUUID())) {
            return null;
        }
        return original.call(instance, displaySlot);
    }


    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("HEAD"))
    public void extractRenderStateHead(AbstractClientPlayer player, PlayerRenderState playerRenderState, float f, CallbackInfo ci,
        @Share(value = "hiddenModelParts", namespace = "flashback") LocalRef<EnumSet<PlayerModelPart>> hiddenPartsRef) {
        EnumSet<PlayerModelPart> hiddenParts = null;

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            hiddenParts = editorState.hiddenModelParts.get(player.getUUID());
        }

        hiddenPartsRef.set(hiddenParts);
    }

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"))
    public boolean extractRenderState_isModelPartShown(AbstractClientPlayer instance, PlayerModelPart playerModelPart, Operation<Boolean> original,
        @Share(value = "hiddenModelParts", namespace = "flashback") LocalRef<EnumSet<PlayerModelPart>> hiddenPartsRef) {
        var hiddenParts = hiddenPartsRef.get();
        return hiddenParts == null ? original.call(instance, playerModelPart) : !hiddenParts.contains(playerModelPart);
    }

}
