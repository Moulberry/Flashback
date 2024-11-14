package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WeatherEffectRenderer.class)
public class MixinWeatherEffectRenderer {

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    public void getPrecipitationAt(Level level, BlockPos blockPos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            switch (editorState.replayVisuals.overrideWeatherMode) {
                case CLEAR, OVERCAST -> cir.setReturnValue(Biome.Precipitation.NONE);
                case RAINING, THUNDERING -> cir.setReturnValue(Biome.Precipitation.RAIN);
                case SNOWING -> cir.setReturnValue(Biome.Precipitation.SNOW);
            }
        }
    }

}
