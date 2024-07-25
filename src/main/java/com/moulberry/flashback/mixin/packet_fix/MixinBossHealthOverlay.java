package com.moulberry.flashback.mixin.packet_fix;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(BossHealthOverlay.class)
public class MixinBossHealthOverlay {

    @Shadow
    @Final
    Map<UUID, LerpingBossEvent> events;

    /**
     * Prevent disconnection if boss state is incorrect
     */
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    public void update(ClientboundBossEventPacket clientboundBossEventPacket, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            clientboundBossEventPacket.dispatch(new ClientboundBossEventPacket.Handler() {
                @Override
                public void updateProgress(UUID uuid, float f) {
                    if (!events.containsKey(uuid)) {
                        ci.cancel();
                    }
                }

                @Override
                public void updateName(UUID uuid, Component component) {
                    if (!events.containsKey(uuid)) {
                        ci.cancel();
                    }
                }

                @Override
                public void updateStyle(UUID uuid, BossEvent.BossBarColor bossBarColor, BossEvent.BossBarOverlay bossBarOverlay) {
                    if (!events.containsKey(uuid)) {
                        ci.cancel();
                    }
                }

                @Override
                public void updateProperties(UUID uuid, boolean bl, boolean bl2, boolean bl3) {
                    if (!events.containsKey(uuid)) {
                        ci.cancel();
                    }
                }
            });
        }
    }

}
