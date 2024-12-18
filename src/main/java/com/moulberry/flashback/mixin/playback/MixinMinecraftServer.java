package com.moulberry.flashback.mixin.playback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

    /*
     * Force the server to use the pending tags we gathered instead of trying
     * to load them from the resource pack repository where it doesn't exist
     */

    @WrapOperation(method = "method_29437", at = @At(value = "INVOKE", target = "Lnet/minecraft/tags/TagLoader;loadTagsForExistingRegistries(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/RegistryAccess;)Ljava/util/List;"))
    public List<Registry.PendingTags<?>> loadTagsForExistingRegistries(ResourceManager resourceManager, RegistryAccess registryAccess, Operation<List<Registry.PendingTags<?>>> original) {
        if ((Object) this instanceof ReplayServer replayServer && replayServer.overridePendingTags != null) {
            return replayServer.overridePendingTags;
        }
        return original.call(resourceManager, registryAccess);
    }

}
