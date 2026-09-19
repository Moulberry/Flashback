package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.SDLEventHandler;
import com.moulberry.flashback.editor.ui.ReplayUI;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SDLEventHandler.class)
public class MixinSDLEventHandler {

    @WrapOperation(method = "pollEvents", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDL_Event;type()I"))
    public int pollEvents_type(SDL_Event instance, Operation<Integer> original) {
        if (((com.moulberry.flashback.editor.ui.CustomImGuiWindowerSdl) ReplayUI.imguiWindower).processEvent(instance)) {
            return -1;
        }
        return original.call(instance);
    }

}
