package com.moulberry.flashback.mixin.record;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FFmpegFrameRecorder.class, remap = false)
public class MixinFFmpegFrameRecorder {

    // Don't change linesize, let it be calculated automatically

    @WrapOperation(method = "recordImage", remap = false, at = @At(value = "INVOKE", remap = false, target = "Lorg/bytedeco/ffmpeg/avutil/AVFrame;linesize(II)Lorg/bytedeco/ffmpeg/avutil/AVFrame;"))
    private AVFrame changeLinesize(AVFrame instance, int i, int setter, Operation<AVFrame> original) {
        return instance;
    }

}
