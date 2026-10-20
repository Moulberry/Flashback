package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class MixinLevel {

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    public void getRainLevel(float f, CallbackInfoReturnable<Float> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && (Object)this instanceof ClientLevel) {
            switch (editorState.replayVisuals.overrideWeatherMode) {
                case CLEAR -> cir.setReturnValue(0.0f);
                case OVERCAST, RAINING, SNOWING, THUNDERING -> cir.setReturnValue(1.0f);
            }
        }
    }


    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    public void getThunderLevel(float f, CallbackInfoReturnable<Float> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && (Object)this instanceof ClientLevel) {
            switch (editorState.replayVisuals.overrideWeatherMode) {
                case CLEAR, OVERCAST, RAINING, SNOWING -> cir.setReturnValue(0.0f);
                case THUNDERING -> cir.setReturnValue(1.0f);
            }
        }
    }

}
