package com.moulberry.flashback.mixin.client.playback.hashing;

import com.moulberry.flashback.ext.PaletteExt;
import net.minecraft.core.IdMap;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.chunk.HashMapPalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(HashMapPalette.class)
public abstract class MixinHashMapPalette<T> implements PaletteExt {
    @Shadow
    @Final
    private IdMap<T> registry;

    @Shadow
    @Final
    private CrudeIncrementalIntIdentityHashBiMap<T> values;

    @Shadow
    public abstract int getSize();

    @Override
    public long flashback$hash() {
        long hash = 0;
        int size = this.getSize();
        for(int j = 0; j < size; ++j) {
            hash = this.registry.getId(this.values.byId(j)) + hash*17;
        }
        return hash;
    }
}
