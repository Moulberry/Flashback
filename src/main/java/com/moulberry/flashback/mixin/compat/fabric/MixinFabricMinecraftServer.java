package com.moulberry.flashback.mixin.compat.fabric;

import com.bawnorton.mixinsquared.TargetHandler;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * Prevent Fabric from modifying the world gen
 * The registry that our ReplayServer uses typically does not contain server-only registries
 * like minecraft:worldgen/placed_feature and so trying to modify the world gen context using
 * these server-only registries is bound to error
 */

@Mixin(value = MinecraftServer.class, priority = 1500)
public class MixinFabricMinecraftServer {
    @TargetHandler(
        mixin = "net.fabricmc.fabric.mixin.biome.modification.MinecraftServerMixin",
        name = "finalizeWorldGen"
    )
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0)
    private void preventWorldGenChanges(CallbackInfo ci) {
        if ((Object) this instanceof ReplayServer) {
            ci.cancel();
        }
    }

}
