package com.moulberry.flashback.compat.simple_voice_chat;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.ActionRegistry;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatInitializationEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.NameTagIconRenderEvent;
import de.maxhenkel.voicechat.api.internal.events.AlcContextEvent;
import de.maxhenkel.voicechat.api.internal.events.ForceShowIconsEvent;
import de.maxhenkel.voicechat.api.internal.events.HudIconRenderEvent;
import de.maxhenkel.voicechat.api.internal.events.NameTagIconRenderEventExtension;
import de.maxhenkel.voicechat.api.internal.events.StartMicEvent;
import de.maxhenkel.voicechat.api.internal.events.UpdateCameraPositionEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

public class SimpleVoiceChatPlugin implements VoicechatPlugin {

    public static VoicechatClientApi CLIENT_API;

    @Override
    public String getPluginId() {
        return "flashback";
    }

    @Override
    public void initialize(VoicechatApi api) {
        ActionRegistry.register(ActionSimpleVoiceChatSound.INSTANCE);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatInitializationEvent.class, this::onClientInitialized);
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, SimpleVoiceChatRecorder::onReceiveEntitySound);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, SimpleVoiceChatRecorder::onReceiveLocationalSound);
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, SimpleVoiceChatRecorder::onReceiveStaticSound);
        registration.registerEvent(ClientSoundEvent.class, SimpleVoiceChatRecorder::onSendSound);
        registration.registerEvent(UpdateCameraPositionEvent.class, this::updateCameraPosition);
        registration.registerEvent(StartMicEvent.class, this::onStartMic);
        registration.registerEvent(NameTagIconRenderEvent.class, this::onRenderNameTagIcon);
        registration.registerEvent(HudIconRenderEvent.class, this::onRenderHudIcons);
        registration.registerEvent(AlcContextEvent.class, this::onAlcContext);
        registration.registerEvent(ForceShowIconsEvent.class, this::onForceShowIcons);
    }

    private void onClientInitialized(ClientVoicechatInitializationEvent event) {
        CLIENT_API = event.getVoicechat();
    }

    private void updateCameraPosition(UpdateCameraPositionEvent event) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            Camera audioCamera = editorState.getAudioCamera();
            if (audioCamera != null) {
                event.setCameraPosition(audioCamera.position(), audioCamera.forwardVector(), audioCamera.upVector());
            }
        }
    }

    private void onStartMic(StartMicEvent event) {
        if (Flashback.isInReplay()) {
            event.cancel();
        }
    }

    private void onRenderNameTagIcon(NameTagIconRenderEvent event) {
        if (Flashback.isInReplay() && ((NameTagIconRenderEventExtension) event).isDisconnected()) {
            event.cancel();
        }
    }

    private void onRenderHudIcons(HudIconRenderEvent event) {
        if (Flashback.isInReplay()) {
            event.cancel();
        }
    }

    private void onAlcContext(AlcContextEvent event) {
        if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().recordAudio()) {
            long context = Minecraft.getInstance().getSoundManager().soundEngine.library.context;
            event.setContext(context);
        }
    }

    private void onForceShowIcons(ForceShowIconsEvent event) {
        if (Flashback.isInReplay()) {
            event.forceShow();
        }
    }

}
