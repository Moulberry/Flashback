package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class SaveableFramebufferQueue implements AutoCloseable {

    private final int width;
    private final int height;

    private static final int CAPACITY = 3;

    private final List<SaveableFramebuffer> available = new ArrayList<>();
    private final List<SaveableFramebuffer> waiting = new ArrayList<>();

    public SaveableFramebufferQueue(int width, int height) {
        this.width = width;
        this.height = height;

        for (int i = 0; i < CAPACITY; i++) {
            this.available.add(new SaveableFramebuffer());
        }
    }

    public SaveableFramebuffer take() {
        if (this.available.isEmpty()) {
            throw new IllegalStateException("No textures available!");
        }
        return this.available.removeFirst();
    }

    public void startDownload(SaveableFramebuffer texture) {
        texture.startDownload(this.width, this.height);
        this.waiting.add(texture);
    }

    record DownloadedFrame(NativeImage image, @Nullable FloatBuffer audioBuffer) {}

    public @Nullable DownloadedFrame finishDownload(boolean drain) {
        if (this.waiting.isEmpty()) {
            return null;
        }

        if (!drain && !this.available.isEmpty()) {
            return null;
        }

        SaveableFramebuffer texture = this.waiting.removeFirst();

        NativeImage nativeImage = texture.finishDownload(this.width, this.height);
        FloatBuffer audioBuffer = texture.audioBuffer;
        texture.audioBuffer = null;

        this.available.add(texture);
        return new DownloadedFrame(nativeImage, audioBuffer);
    }

    @Override
    public void close() {
        for (SaveableFramebuffer texture : this.waiting) {
            texture.close();
        }
        for (SaveableFramebuffer texture : this.available) {
            texture.close();
        }
        this.waiting.clear();
        this.available.clear();
    }


}
