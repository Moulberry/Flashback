package com.moulberry.flashback.ext;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.jetbrains.annotations.Nullable;

public interface LivingEntityExt {

    void flashback$captureSwing(InteractionHand hand, SwingAnimation animation);

    @Nullable
    LivingEntity.SwingDescription flashback$consumeCapturedSwing();

}
