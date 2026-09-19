package com.moulberry.flashback.mixin.record;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.LivingEntityExt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntity.class)
public class MixinLivingEntity implements LivingEntityExt {

    @Unique
    @Nullable
    private LivingEntity.SwingDescription flashback$capturedSwing = null;

    @Override
    public void flashback$captureSwing(InteractionHand hand, SwingAnimation animation) {
        if (!Flashback.isInReplay()) {
            this.flashback$capturedSwing = new LivingEntity.SwingDescription(hand, animation, 0);
        }
    }

    @Override
    @Nullable
    public LivingEntity.SwingDescription flashback$consumeCapturedSwing() {
        LivingEntity.SwingDescription swing = this.flashback$capturedSwing;
        this.flashback$capturedSwing = null;
        return swing;
    }

}
