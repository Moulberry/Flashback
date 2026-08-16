package com.moulberry.flashback.mixin.packet_fix;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.io.AsyncReplaySaver;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StringUtil.class)
public class MixinStringUtils {

    @Inject(method = {"isValidPlayerName", "isAllowedChatCharacter"}, at = @At("HEAD"), cancellable = true)
    private static void validate(CallbackInfoReturnable<Boolean> cir) {
        if (AsyncReplaySaver.WRITING_PACKET.get() == Boolean.TRUE || Flashback.isInReplay()) {
            cir.setReturnValue(true);
        }
    }

}
