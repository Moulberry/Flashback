package com.moulberry.flashback.mixin.compat.voice_chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import de.maxhenkel.voicechat.voice.client.speaker.ALSpeakerBase;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@IfModLoaded("voicechat")
@Pseudo
@Mixin(ALSpeakerBase.class)
public class MixinVoiceChatALSpeakerBase {

    @WrapOperation(method = "setPositionSync", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getMainCamera()Lnet/minecraft/client/Camera;"))
    public Camera setPositionSync_getMainCamera(GameRenderer instance, Operation<Camera> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            Camera audioCamera = editorState.getAudioCamera();
            if (audioCamera != null) {
                return audioCamera;
            }
        }
        return original.call(instance);
    }

}
