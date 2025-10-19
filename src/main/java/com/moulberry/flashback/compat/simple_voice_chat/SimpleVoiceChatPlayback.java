package com.moulberry.flashback.compat.simple_voice_chat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.packet.FlashbackVoiceChatSound;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.audiochannel.ClientEntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.ClientLocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.ClientStaticAudioChannel;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import de.maxhenkel.voicechat.voice.client.ClientVoicechat;
import net.minecraft.world.phys.Vec3;

import java.time.Duration;
import java.util.UUID;

public class SimpleVoiceChatPlayback {

    private static final Cache<UUID, ClientStaticAudioChannel> staticAudioChannelCache = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofMinutes(1)).build();
    private static final Cache<UUID, ClientLocationalAudioChannel> locationAudioChannelCache = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofMinutes(1)).build();
    private static final Cache<UUID, ClientEntityAudioChannel> entityAudioChannelCache = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofMinutes(1)).build();

    private static final int ERROR_LOG_MAX = 8;
    private static int errorLogCount = 0;

    public static void play(FlashbackVoiceChatSound sound) {
        try {
            UUID source = sound.source();

            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState == null || editorState.hideDuringExport.contains(source)) {
                return;
            }

            boolean whispering = false;
            switch (sound) {
                case FlashbackVoiceChatSound.SoundStatic soundStatic -> {
                    ClientStaticAudioChannel channel = staticAudioChannelCache.get(source,
                        () -> SimpleVoiceChatPlugin.CLIENT_API.createStaticAudioChannel(source));
                    channel.play(sound.samples());
                }
                case FlashbackVoiceChatSound.SoundLocational soundLocational -> {
                    Vec3 pos = soundLocational.position();
                    Position position = SimpleVoiceChatPlugin.CLIENT_API.createPosition(pos.x, pos.y, pos.z);
                    ClientLocationalAudioChannel channel = locationAudioChannelCache.get(source,
                        () -> SimpleVoiceChatPlugin.CLIENT_API.createLocationalAudioChannel(source, position));
                    channel.setLocation(position);
                    channel.setDistance(soundLocational.distance());
                    channel.play(sound.samples());
                }
                case FlashbackVoiceChatSound.SoundEntity soundEntity -> {
                    ClientEntityAudioChannel channel = entityAudioChannelCache.get(source,
                        () -> SimpleVoiceChatPlugin.CLIENT_API.createEntityAudioChannel(source));
                    channel.setWhispering(soundEntity.whispering());
                    channel.setDistance(soundEntity.distance());
                    channel.play(sound.samples());
                    whispering = soundEntity.whispering();
                }
                default -> {
                }
            }

            try {
                ClientVoicechat client = ClientManager.getClient();
                if (client != null) {
                    client.getTalkCache().updateLevel(sound.source(), null, whispering, sound.samples());
                }
            } catch (NoSuchMethodError ignored) {
            }
        } catch (Exception | NoSuchMethodError e) {
            if (errorLogCount < ERROR_LOG_MAX) {
                Flashback.LOGGER.error("Error while trying to play voice chat sound", e);

                errorLogCount += 1;
                if (errorLogCount == ERROR_LOG_MAX) {
                    Flashback.LOGGER.error("Stopping logging voice chat playback errors since there are too many");
                }
            }
        }
    }

    public static void cleanUp() {
        staticAudioChannelCache.cleanUp();
        locationAudioChannelCache.cleanUp();
        entityAudioChannelCache.cleanUp();
    }

}
