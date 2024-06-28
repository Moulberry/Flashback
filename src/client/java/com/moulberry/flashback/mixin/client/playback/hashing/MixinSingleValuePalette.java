package com.moulberry.flashback.mixin.client.playback.hashing;

import com.moulberry.flashback.ext.PaletteExt;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SingleValuePalette.class)
public class MixinSingleValuePalette<T> implements PaletteExt {
    @Shadow
    @Final
    public IdMap<T> registry;

    @Shadow
    @Nullable
    public T value;

    @Override
    public long flashback$hash() {
        return this.registry.getId(this.value);
    }
}
