package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class MixinClientLevel {

    @Mixin(ClientLevel.ClientLevelData.class)
    public static class MixinClientLevelData {
        @Inject(method = "getGameTime", at = @At("HEAD"), cancellable = true, require = 0)
        public void getGameTime(CallbackInfoReturnable<Long> cir) {
            if (Flashback.isInReplay()) {
                cir.setReturnValue(Flashback.getReplayGameTime());
            }
        }
    }

//    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true, require = 0)
//    public void getSkyColor(Vec3 vec3, float f, CallbackInfoReturnable<Integer> cir) {
//        EditorState editorState = EditorStateManager.getCurrent();
//        if (editorState != null) {
//            ReplayVisuals visuals = editorState.replayVisuals;
//
//            if (!visuals.renderSky) {
//                if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent()) {
//                    cir.setReturnValue(0);
//                } else {
//                    float[] skyColour = visuals.skyColour;
//                    int r = (int)(skyColour[0] * 255);
//                    int g = (int)(skyColour[1] * 255);
//                    int b = (int)(skyColour[2] * 255);
//                    cir.setReturnValue(0xFF000000 | (r << 16) | (g << 8) | b);
//                }
//            }
//        }
//    }

    /*
     * Some entities have weird behaviour when rotating in a replay
     * This code here ensures that the interpolation of yRotO -> yRot will always be the shortest path
     */



    @Inject(method = "tickNonPassenger", at = @At("RETURN"), require = 0)
    public void tickNonPassengerEnd(Entity entity, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            entity.xRotO = Mth.wrapDegrees(entity.xRotO - entity.getXRot()) + entity.getXRot();
            entity.yRotO = Mth.wrapDegrees(entity.yRotO - entity.getYRot()) + entity.getYRot();
        }
    }

    @Inject(method = "tickPassenger", at = @At("RETURN"), require = 0)
    public void tickPassengerEnd(Entity vehicle, Entity entity, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            entity.xRotO = Mth.wrapDegrees(entity.xRotO - entity.getXRot()) + entity.getXRot();
            entity.yRotO = Mth.wrapDegrees(entity.yRotO - entity.getYRot()) + entity.getYRot();
        }
    }

}
