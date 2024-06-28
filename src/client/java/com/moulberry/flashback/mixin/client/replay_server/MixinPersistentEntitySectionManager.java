package com.moulberry.flashback.mixin.client.replay_server;

import com.moulberry.flashback.FlashbackClient;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentEntitySectionManager.class)
public class MixinPersistentEntitySectionManager {

    @Inject(method = "saveAll", at = @At("HEAD"), cancellable = true)
    public void saveAll(CallbackInfo ci) {
        if (FlashbackClient.isInReplay()) {
            ci.cancel();
        }
    }

}
