package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity  {

    public MixinLivingEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    // This isn't ideal. Ideally we'd only do this during rendering, but unfortunately
    // there isn't a specific method to override when rendering. I also don't want to have
    // to manually wrap every getItemBySlot call inside the model rendering code, so here we are

    @Inject(method = "getItemBySlot", at = @At("HEAD"), cancellable = true)
    public void getItemBySlot(EquipmentSlot equipmentSlot, CallbackInfoReturnable<ItemStack> cir) {
        if (this.level().isClientSide()) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null) {
                var hidden = editorState.hiddenEquipment.get(this.uuid);
                if (hidden != null && hidden.contains(equipmentSlot)) {
                    cir.setReturnValue(ItemStack.EMPTY);
                }
            }
        }
    }

}
