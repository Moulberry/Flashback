package com.moulberry.flashback;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class FilePlayerSkin {

    private static class CleanState implements Runnable {
        private ResourceLocation skinResourceLocation;

        public CleanState(ResourceLocation skinResourceLocation) {
            this.skinResourceLocation = skinResourceLocation;
        }

        @Override
        public void run() {
            if (this.skinResourceLocation != null) {
                System.out.println("Cleaning " + this.skinResourceLocation + " because it's no longer in use!");
                Minecraft.getInstance().getTextureManager().release(this.skinResourceLocation);
                this.skinResourceLocation = null;
            }
        }
    }

    private transient PlayerSkin playerSkin = null;
    private final String pathToSkin;

    public FilePlayerSkin(String pathToSkin) {
        this.pathToSkin = pathToSkin;
    }

    public PlayerSkin getSkin() {
        if (this.playerSkin != null) {
            return this.playerSkin;
        }

        Path path = Path.of(this.pathToSkin);
        try (InputStream inputStream = Files.newInputStream(path)) {
            NativeImage nativeImage = NativeImage.read(inputStream);

            int w = nativeImage.getWidth();
            int h = nativeImage.getHeight();

            // We determine the type using the alpha of the pixel at 54, 20
            int argb = nativeImage.getPixel(54 * w / 64, 20 * h / 64);
            PlayerSkin.Model model = PlayerSkin.Model.WIDE;
            if (((argb >> 24) & 0xFF) < 20) {
                model = PlayerSkin.Model.SLIM;
            }

            DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);

            ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath("flashback", "skin_from_file/" + UUID.randomUUID());
            Minecraft.getInstance().getTextureManager().register(resourceLocation, dynamicTexture);
            GlobalCleaner.INSTANCE.register(this, new CleanState(resourceLocation));

            this.playerSkin = new PlayerSkin(resourceLocation, null, null, null, model, false);
        } catch (Exception e) {
            Flashback.LOGGER.error("Unable to load skin from file", e);
            this.playerSkin = DefaultPlayerSkin.getDefaultSkin();
        }

        return this.playerSkin;
    }

}
