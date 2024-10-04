package com.moulberry.flashback.exporting;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.SkinManager;

import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public class PerfectFrames {

    private static boolean isEnabled = false;
    static List<Consumer<Boolean>> frexFlawlessFrames = new ArrayList<>();

    private static final Set<UUID> ignoreSkinLoading = new HashSet<>();

    public static void enable() {
        isEnabled = true;
        ignoreSkinLoading.clear();
        for (Consumer<Boolean> frexFlawlessFramesConsumer : frexFlawlessFrames) {
            frexFlawlessFramesConsumer.accept(true);
        }
    }

    public static void disable() {
        isEnabled = false;
        ignoreSkinLoading.clear();
        for (Consumer<Boolean> frexFlawlessFramesConsumer : frexFlawlessFrames) {
            frexFlawlessFramesConsumer.accept(false);
        }
    }

    public static boolean isEnabled() {
        return isEnabled;
    }

    public static void waitUntilFrameReady() {
        long start = System.currentTimeMillis();
        while (!isFrameReady(start)) {
            LockSupport.parkNanos("waiting for frame to be ready", 100000L);
            while (Minecraft.getInstance().pollTask()) {}
        }
    }

    private static boolean isFrameReady(long start) {
        long current = System.currentTimeMillis();
        boolean forceFrameReady = current - start > 15000;

        ClientLevel level = Minecraft.getInstance().level;
        SkinManager skinManager = Minecraft.getInstance().getSkinManager();

        if (level != null) {
            if (forceFrameReady) {
                Flashback.LOGGER.error("Waiting for skin loading took too long, ignoring...");
                for (AbstractClientPlayer player : level.players()) {
                    if (player == Minecraft.getInstance().player) {
                        continue;
                    }
                    PlayerInfo playerInfo = player.getPlayerInfo();
                    if (playerInfo != null && !skinManager.getOrLoad(playerInfo.getProfile()).isDone()) {
                        Flashback.LOGGER.error("Took too long to load skin for {}", player.getUUID());
                        ignoreSkinLoading.add(player.getUUID());
                    }
                }
            } else {
                for (AbstractClientPlayer player : level.players()) {
                    if (player == Minecraft.getInstance().player) {
                        continue;
                    }
                    if (ignoreSkinLoading.contains(player.getUUID())) {
                        continue;
                    }
                    PlayerInfo playerInfo = player.getPlayerInfo();
                    if (playerInfo != null && !skinManager.getOrLoad(playerInfo.getProfile()).isDone()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

}
