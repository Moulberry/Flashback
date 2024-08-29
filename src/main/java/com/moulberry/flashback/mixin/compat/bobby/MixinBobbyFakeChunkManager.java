package com.moulberry.flashback.mixin.compat.bobby;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import de.johni0702.minecraft.bobby.FakeChunkManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@IfModLoaded("bobby")
@Mixin(FakeChunkManager.class)
public class MixinBobbyFakeChunkManager {

    @Inject(method = "getCurrentWorldOrServerName", at = @At("HEAD"), cancellable = true)
    private static void getCurrentWorldOrServerName(ClientPacketListener networkHandler, CallbackInfoReturnable<String> cir) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            FlashbackMeta meta = replayServer.getMetadata();
            if (meta != null && meta.bobbyWorldName != null) {
                Flashback.LOGGER.info("Overriding Bobby World Name: {}", meta.bobbyWorldName);
                cir.setReturnValue(meta.bobbyWorldName);
            } else {
                cir.setReturnValue("unknown_flashback");
            }
        }
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    public void save(LevelChunk chunk, CallbackInfoReturnable<Supplier<LevelChunk>> cir) {
        if (Flashback.isInReplay()) {
            cir.setReturnValue(() -> chunk);
        }
    }

}
