package com.moulberry.flashback.mixin;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(DebugScreenEntryList.class)
public class MixinDebugScreenEntryList {

    /*
     * Ensures that the default IN_F3 value of RECORDING_INFO_DEBUG_SCREEN_ID is set
     * even when the player loads a profile that didn't have Flashback originally
     */

    @Shadow private Map<Identifier, DebugScreenEntryStatus> allStatuses;

    @Inject(method = "rebuildCurrentList", at = @At("HEAD"))
    public void rebuildCurrentList(CallbackInfo ci) {
        if (!this.allStatuses.containsKey(Flashback.RECORDING_INFO_DEBUG_SCREEN_ID)) {
            if (!(this.allStatuses instanceof HashMap<Identifier, DebugScreenEntryStatus>)) {
                this.allStatuses = new HashMap<>(this.allStatuses);
            }
            this.allStatuses.put(Flashback.RECORDING_INFO_DEBUG_SCREEN_ID, DebugScreenEntryStatus.IN_OVERLAY);
        }
    }

}
