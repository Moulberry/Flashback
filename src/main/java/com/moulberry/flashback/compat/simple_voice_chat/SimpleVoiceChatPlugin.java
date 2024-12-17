package com.moulberry.flashback.compat.simple_voice_chat;

import com.moulberry.flashback.action.ActionRegistry;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatInitializationEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

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
    }

    private void onClientInitialized(ClientVoicechatInitializationEvent event) {
        CLIENT_API = event.getVoicechat();
    }
}
