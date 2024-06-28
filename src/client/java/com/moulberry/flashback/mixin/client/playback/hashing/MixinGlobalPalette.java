package com.moulberry.flashback.mixin.client.playback.hashing;

import com.moulberry.flashback.ext.PaletteExt;
import net.minecraft.world.level.chunk.GlobalPalette;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GlobalPalette.class)
public class MixinGlobalPalette implements PaletteExt {
    @Override
    public long flashback$hash() {
        return 0xCF184DF659428CABL;
    }
}
