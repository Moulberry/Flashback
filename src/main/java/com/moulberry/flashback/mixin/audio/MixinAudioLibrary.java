package com.moulberry.flashback.mixin.audio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Library;
import com.moulberry.flashback.Flashback;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.SOFTLoopback;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

@Mixin(Library.class)
public class MixinAudioLibrary {

    @Shadow
    public long currentDevice;

    @Unique
    private boolean usingLoopbackDevice = false;

    @Inject(method = "init", at = @At("HEAD"))
    public void init(String string, boolean bl, CallbackInfo ci) {
        this.usingLoopbackDevice = Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().recordAudio();
        if (this.usingLoopbackDevice) {
            Flashback.LOGGER.info("Enabling loopback device for recording audio");
        }
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Library;openDeviceOrFallback(Ljava/lang/String;)J"))
    public long init_openDevice(String string, Operation<Long> original) {
        if (this.usingLoopbackDevice) {
            return SOFTLoopback.alcLoopbackOpenDeviceSOFT((CharSequence) null);
        } else {
            return original.call(string);
        }
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J", remap = false))
    public long init_createContext(long deviceHandle, IntBuffer attrList, Operation<Long> original) {
        if (this.usingLoopbackDevice) {
            try (MemoryStack memoryStack = MemoryStack.stackPush()) {
                int channels = Flashback.EXPORT_JOB.getSettings().stereoAudio() ? SOFTLoopback.ALC_STEREO_SOFT : SOFTLoopback.ALC_MONO_SOFT;
                IntBuffer intBuffer = memoryStack.callocInt(7)
                                                 .put(SOFTLoopback.ALC_FORMAT_TYPE_SOFT).put(SOFTLoopback.ALC_FLOAT_SOFT)
                                                 .put(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT).put(channels)
                                                 .put(ALC10.ALC_FREQUENCY).put(48000)
                                                 .put(0).flip();
                return ALC10.alcCreateContext(this.currentDevice, intBuffer);
            }
        } else {
            return original.call(deviceHandle, attrList);
        }
    }

}
