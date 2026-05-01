package com.moulberry.flashback.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.Projection;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@IfModLoaded(value = "sodium", aliases = "embeddium")
@Pseudo
@Mixin(targets = {"net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", "me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer"}, remap = false)
public abstract class MixinSodiumChunkRenderer {

    @WrapOperation(method = "render", require = 0, remap = false, at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumOptions$PerformanceSettings;useBlockFaceCulling:Z", opcode = Opcodes.GETFIELD, remap = false))
    private boolean render_useBlockFaceCulling(SodiumOptions.PerformanceSettings instance, Operation<Boolean> original) {
        ExportJob exportJob = Flashback.EXPORT_JOB;
        if (exportJob != null && exportJob.getSettings().projection() == Projection.ORTHOGRAPHIC) {
            return false;
        }
        return original.call(instance);
    }

}
