package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumSet;

@Mixin(AvatarRenderer.class)
public class MixinAvatarRenderer {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("HEAD"))
    public void extractRenderState(Avatar avatar, AvatarRenderState avatarRenderState, float f, CallbackInfo ci,
            @Share(value = "hiddenModelParts", namespace = "flashback") LocalRef<EnumSet<PlayerModelPart>> hiddenPartsRef) {
        EnumSet<PlayerModelPart> hiddenParts = null;

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            hiddenParts = editorState.hiddenModelParts.get(avatar.getUUID());
        }

        hiddenPartsRef.set(hiddenParts);
    }

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Avatar;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"))
    public boolean extractRenderState_isModelPartShown(Avatar instance, PlayerModelPart playerModelPart, Operation<Boolean> original,
            @Share(value = "hiddenModelParts", namespace = "flashback") LocalRef<EnumSet<PlayerModelPart>> hiddenPartsRef) {
        var hiddenParts = hiddenPartsRef.get();
        return hiddenParts == null ? original.call(instance, playerModelPart) : !hiddenParts.contains(playerModelPart);
    }

}
