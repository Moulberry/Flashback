package com.moulberry.flashback.compat.simple_voice_chat;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.packet.FlashbackVoiceChatSound;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class SimpleVoiceChatRecorder {

    public static void onReceiveEntitySound(ClientReceiveSoundEvent.EntitySound event) {
        if (Flashback.RECORDER == null) {
            return;
        }

        FlashbackVoiceChatSound soundPacket = new FlashbackVoiceChatSound.SoundEntity(event.getId(), event.getRawAudio(), event.isWhispering(), event.getDistance());
        submitSoundPacket(soundPacket);
    }

    public static void onReceiveLocationalSound(ClientReceiveSoundEvent.LocationalSound event) {
        if (Flashback.RECORDER == null) {
            return;
        }

        Vec3 position = new Vec3(event.getPosition().getX(), event.getPosition().getY(), event.getPosition().getZ());
        FlashbackVoiceChatSound soundPacket = new FlashbackVoiceChatSound.SoundLocational(event.getId(), event.getRawAudio(), position, event.getDistance());
        submitSoundPacket(soundPacket);
    }

    public static void onReceiveStaticSound(ClientReceiveSoundEvent.StaticSound event) {
        if (Flashback.RECORDER == null) {
            return;
        }

        FlashbackVoiceChatSound soundPacket = new FlashbackVoiceChatSound.SoundStatic(event.getId(), event.getRawAudio());
        submitSoundPacket(soundPacket);
    }

    public static void onSendSound(ClientSoundEvent event) {
        if (Flashback.RECORDER == null) {
            return;
        }

        UUID id = ClientManager.getPlayerStateManager().getOwnID();

        FlashbackVoiceChatSound soundPacket;
        if (SimpleVoiceChatPlugin.CLIENT_API.getGroup() != null) {
            soundPacket = new FlashbackVoiceChatSound.SoundStatic(id, event.getRawAudio());
        } else {
            soundPacket = new FlashbackVoiceChatSound.SoundEntity(id, event.getRawAudio(), event.isWhispering(),
                (float) SimpleVoiceChatPlugin.CLIENT_API.getVoiceChatDistance());
        }

        submitSoundPacket(soundPacket);
    }

    private static void submitSoundPacket(FlashbackVoiceChatSound soundPacket) {
        if (!Flashback.getConfig().recordVoiceChat) {
            return;
        }

        Flashback.RECORDER.submitCustomTask(writer -> {
            writer.startAction(ActionSimpleVoiceChatSound.INSTANCE);
            FlashbackVoiceChatSound.STREAM_CODEC.encode(writer.friendlyByteBuf(), soundPacket);
            writer.finishAction(ActionSimpleVoiceChatSound.INSTANCE);
        });
    }

}
