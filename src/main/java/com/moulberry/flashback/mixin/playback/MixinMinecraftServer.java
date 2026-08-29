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

    /* Keep the tags already installed on replay registries instead of replacing
     * them with tags from the replay server's empty datapack repository. */

    @WrapOperation(method = "method_29437", at = @At(value = "INVOKE", target = "Lnet/minecraft/tags/TagLoader;loadTagsForExistingRegistries(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/RegistryAccess;)Ljava/util/List;"))
    public List<Registry.PendingTags<?>> loadTagsForExistingRegistries(ResourceManager resourceManager, RegistryAccess registryAccess, Operation<List<Registry.PendingTags<?>>> original) {
        if ((Object) this instanceof ReplayServer) {
            return List.of();
        }
        return original.call(resourceManager, registryAccess);
    }

}
