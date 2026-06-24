package com.moulberry.flashback.mixin.compat.fabric;

import com.moulberry.flashback.Flashback;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.fabricmc.fabric.impl.recipe.sync.RecipeSyncImpl")
@Pseudo
public class MixinRecipeSyncImpl {

    @Inject(method = "sendRecipes", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sendRecipes(ServerPlayer player, boolean exist, CallbackInfo ci) {
        // Recipe serialization is often broken inside replays, lets not send them
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

}
