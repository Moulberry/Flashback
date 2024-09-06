package com.moulberry.flashback.compat.simple_voice_chat;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.packet.FlashbackVoiceChatSound;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionSimpleVoiceChatSound implements Action {
    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/simple_voice_chat_sound_optional");
    public static final ActionSimpleVoiceChatSound INSTANCE = new ActionSimpleVoiceChatSound();
    private ActionSimpleVoiceChatSound() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf friendlyByteBuf) {
        var soundPacket = FlashbackVoiceChatSound.STREAM_CODEC.decode(friendlyByteBuf);

        if (replayServer.isProcessingSnapshot) {
            return;
        }

        boolean sendVoiceChat;
        if (Flashback.isExporting()) {
            sendVoiceChat = Flashback.EXPORT_JOB.getSettings().recordAudio() && Flashback.EXPORT_JOB.getCurrentTickDouble() > 0.0;
        } else {
            sendVoiceChat = !replayServer.fastForwarding && !replayServer.replayPaused;
        }

        if (sendVoiceChat) {
            for (ReplayPlayer replayViewer : replayServer.getReplayViewers()) {
                ServerPlayNetworking.send(replayViewer, soundPacket);
            }
        }

    }

}
