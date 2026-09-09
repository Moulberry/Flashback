package com.moulberry.flashback.mixin.compat.voice_chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import de.maxhenkel.voicechat.voice.client.camera.CameraState;
import de.maxhenkel.voicechat.voice.client.speaker.ALSpeakerBase;
import net.minecraft.client.Camera;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@IfModLoaded("voicechat")
@Pseudo
@Mixin(ALSpeakerBase.class)
public class MixinVoiceChatALSpeakerBase {

    @WrapOperation(method = "setPositionSync", at = @At(value = "INVOKE", target = "Lde/maxhenkel/voicechat/voice/client/ClientManager;getCameraState()Lde/maxhenkel/voicechat/voice/client/camera/CameraState;"))
    public CameraState setPositionSync_getCameraState(Operation<CameraState> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            Camera audioCamera = editorState.getAudioCamera();
            if (audioCamera != null) {
                return new CameraState(
                        audioCamera.position(),
                        new Vector3f(audioCamera.forwardVector()),
                        new Vector3f(audioCamera.upVector()),
                        audioCamera.yRot(),
                        null,
                        audioCamera.position(),
                        null,
                        false
                );
            }
        }
        return original.call();
    }

}
