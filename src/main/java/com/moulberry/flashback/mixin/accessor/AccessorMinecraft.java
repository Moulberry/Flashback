package com.moulberry.flashback.mixin.accessor;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.SDLEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface AccessorMinecraft {

    @Accessor("sdlEventHandler")
    SDLEventHandler flashback$getSdlEventHandler();

}
