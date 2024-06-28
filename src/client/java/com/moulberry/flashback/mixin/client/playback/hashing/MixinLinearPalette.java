package com.moulberry.flashback.mixin.client.playback.hashing;

import com.moulberry.flashback.ext.PaletteExt;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.chunk.LinearPalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LinearPalette.class)
public class MixinLinearPalette<T> implements PaletteExt {
    @Shadow
    private int size;

    @Shadow
    @Final
    private IdMap<T> registry;

    @Shadow
    @Final
    public T[] values;

    @Override
    public long flashback$hash() {
        long hash = 0;
        int size = this.size;
        for(int i = 0; i < size; ++i) {
            hash = this.registry.getId(this.values[i]) + hash*17;
        }
        return hash;
    }
}
