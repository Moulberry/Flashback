package com.moulberry.flashback.mixin.povscreen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.ScreenExt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InventoryScreen.class)
public class MixinInventoryScreen {

    @WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"))
    public void extractBackground_extractEntityInInventoryFollowsMouse(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int size, float offsetY, float mouseX, float mouseY, LivingEntity entity, Operation<Void> original) {
        if (((ScreenExt)this).flashback$isFake()) {
            AbstractClientPlayer spectating = Flashback.getSpectatingPlayer();
            if (spectating != null) {
                entity = spectating;
            }
        }
        original.call(graphics, x0, y0, x1, y1, size, offsetY, mouseX, mouseY, entity);
    }

}
