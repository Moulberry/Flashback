package com.moulberry.flashback.sound;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FlashbackAudioManager {

    private static final LoadingCache<Path, FlashbackAudioBuffer> audioBufferCache = CacheBuilder.newBuilder()
            .weakValues()
            .build(new CacheLoader<>() {
                @Override
                public FlashbackAudioBuffer load(@NotNull Path path) {
                    FlashbackAudioBuffer loaded = FlashbackAudioBuffer.load(path);
                    if (loaded != null) {
                        return loaded;
                    } else {
                        return FlashbackAudioBuffer.EMPTY;
                    }
                }
            });

    public static FlashbackAudioBuffer getBuffer(Path path) {
        return audioBufferCache.getUnchecked(path);
    }

    public static void invalidateLoadedBuffers() {
        for (FlashbackAudioBuffer audioBuffer : audioBufferCache.asMap().values()) {
            audioBuffer.invalidate(true);
        }
    }

    private static class PlayingAudioInstance {
        private final FlashbackAudioBuffer audioBuffer;
        private ChannelAccess.ChannelHandle channelHandle;
        private int startTick;
        private float seconds;
        private float speed = 1.0f;
        private boolean claimed = false;

        public PlayingAudioInstance(FlashbackAudioBuffer audioBuffer) {
            this.audioBuffer = audioBuffer;
        }
    }

    private static final List<PlayingAudioInstance> instances = new ArrayList<>();
    private static boolean handling = false;

    public static void startHandling() {
        if (handling) {
            throw new IllegalStateException();
        }
        handling = true;
    }

    public static void finishHandling() {
        if (!handling) {
            throw new IllegalStateException();
        }
        handling = false;

        for (PlayingAudioInstance instance : instances) {
            if (!instance.claimed) {
                instance.channelHandle.execute(Channel::stop);
            }
            instance.claimed = false;
        }
    }

    public static void playAt(SoundEngine soundEngine, FlashbackAudioBuffer audioBuffer, int startTick, float seconds, float speed) {
        if (!handling) {
            return;
        }

        PlayingAudioInstance instance = findMatchingInstance(audioBuffer, startTick, seconds);
        if (instance == null) {
            instance = new PlayingAudioInstance(audioBuffer);
            instances.add(instance);
        }

        float clampedSpeed = Math.max(0.1f, Math.min(10f, speed));
        boolean updateSpeed = instance.speed != clampedSpeed;
        instance.startTick = startTick;
        instance.seconds = seconds;
        instance.speed = clampedSpeed;
        instance.claimed = true;

        if (instance.channelHandle == null || instance.channelHandle.isStopped()) {
            var channelAccess = soundEngine.channelAccess;
            var handleFuture = channelAccess.createHandle(Library.Pool.STATIC);
            instance.channelHandle = handleFuture.join();
            if (instance.channelHandle == null) {
                ReplayUI.setInfoOverlay("Unable to play audio: out of channels");
                return;
            }
            instance.channelHandle.execute(channel -> {
                channel.setPitch(clampedSpeed);
                channel.setVolume(1.0f);
                channel.disableAttenuation();

                channel.setLooping(false);
                channel.setSelfPosition(Vec3.ZERO);
                channel.setRelative(false);

                AL10.alGetError(); // Discard error
                channel.attachStaticBuffer(audioBuffer.soundBuffer());
                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    Flashback.LOGGER.error("Error attaching sound buffer, invalidating and trying again");
                    audioBuffer.invalidate(false);

                    channel.attachStaticBuffer(audioBuffer.soundBuffer());
                    if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                        Flashback.LOGGER.error("Error attaching sound buffer a second time... bailing");
                        return;
                    }
                }

                AL10.alSourcef(channel.source, AL11.AL_SEC_OFFSET, seconds);

                channel.play();
            });
        } else {
            instance.channelHandle.execute(channel -> {
                channel.unpause();

                if (updateSpeed) {
                    channel.setPitch(clampedSpeed);
                }

                float secOffset = AL10.alGetSourcef(channel.source, AL11.AL_SEC_OFFSET);
                if (Math.abs(secOffset - seconds) > 0.05f) {
                    AL10.alSourcef(channel.source, AL11.AL_SEC_OFFSET, seconds);
                }
            });
        }
    }

    private static @Nullable PlayingAudioInstance findMatchingInstance(FlashbackAudioBuffer audioBuffer, int startTick, float seconds) {
        if (instances.isEmpty()) {
            return null;
        }

        for (PlayingAudioInstance instance : instances) {
            if (instance.audioBuffer != audioBuffer || instance.claimed) {
                continue;
            }
            if (instance.startTick == startTick) {
                return instance;
            }
        }

        float bestSecondsDelta = Float.MAX_VALUE;
        PlayingAudioInstance bestInstance = null;

        for (PlayingAudioInstance instance : instances) {
            if (instance.audioBuffer != audioBuffer || instance.claimed) {
                continue;
            }

            float delta = Math.abs(instance.seconds - seconds);
            if (delta < bestSecondsDelta) {
                bestSecondsDelta = delta;
                bestInstance = instance;
            }
        }

        return bestInstance;
    }

    public static void stopAll() {
        if (handling) {
            throw new IllegalStateException();
        }

        for (PlayingAudioInstance instance : instances) {
            instance.channelHandle.execute(Channel::stop);
        }
        instances.clear();
    }

    public static void pauseAll() {
        if (handling) {
            throw new IllegalStateException();
        }

        for (PlayingAudioInstance instance : instances) {
            instance.channelHandle.execute(Channel::pause);
        }
    }

}
