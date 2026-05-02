package com.moulberry.flashback.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.ExportProjection;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@IfModLoaded(value = "sodium", aliases = "embeddium")
@Pseudo
@Mixin(targets = {"net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager"}, remap = false)
public abstract class MixinSodiumRenderSectionManager {

    @WrapOperation(method = "getSearchDistance", require = 0, remap = false, at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumOptions$PerformanceSettings;useFogOcclusion:Z", opcode = Opcodes.GETFIELD, remap = false))
    private boolean getSearchDistance_useFogOcclusion(SodiumOptions.PerformanceSettings instance, Operation<Boolean> original) {
        ExportJob exportJob = Flashback.EXPORT_JOB;
        if (exportJob != null && exportJob.getSettings().projection() == ExportProjection.ORTHOGRAPHIC) {
            return false;
        }
        return original.call(instance);
    }

}
