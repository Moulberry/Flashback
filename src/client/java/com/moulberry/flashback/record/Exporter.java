package com.moulberry.flashback.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.moulberry.flashback.FlashbackClient;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Exporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void export(Path recordFolder, Path outputFile) {
        FlashbackClient.LOGGER.info("Exporting {} to {}", recordFolder, outputFile);

        FlashbackMeta meta = tryReadMeta(recordFolder.resolve("metadata.json"));
        if (meta == null) {
            meta = tryReadMeta(recordFolder.resolve("metadata.json.old"));
        }
        if (meta == null) {
            FlashbackClient.LOGGER.error("Cannot export, both metadata files are invalid");
            return;
        }

        // Validate
        meta.chunks.keySet().removeIf(chunkName -> {
            Path chunkPath = recordFolder.resolve(chunkName);
            if (!Files.exists(chunkPath)) {
                FlashbackClient.LOGGER.warn("Cannot find chunk path: {}, skipping", chunkPath);
                return true;
            } else {
                return false;
            }
        });

        if (meta.chunks.isEmpty()) {
            FlashbackClient.LOGGER.error("Cannot export, no chunk files exist");
            return;
        }

        try {
            FileOutputStream fos = new FileOutputStream(outputFile.toFile());
            ZipOutputStream zipOut = new ZipOutputStream(fos);

            // Write metadata
            ZipEntry zipEntry = new ZipEntry("metadata.json");
            zipOut.putNextEntry(zipEntry);
            zipOut.write(GSON.toJson(meta.toJson()).getBytes(StandardCharsets.UTF_8));

            // Write chunks
            for (String chunkName : meta.chunks.keySet()) {
                Path chunkPath = recordFolder.resolve(chunkName);
                byte[] chunkData = Files.readAllBytes(chunkPath);

                if (chunkData.length == 0) {
                    FlashbackClient.LOGGER.warn("Length of chunk data {} is zero, might be corrupted...", chunkName);
                }

                zipEntry = new ZipEntry(chunkName);
                zipOut.putNextEntry(zipEntry);
                zipOut.write(chunkData);
            }

            zipOut.close();
            fos.close();

            // Delete record folder
            try {
                FileUtils.deleteDirectory(recordFolder.toFile());
            } catch (Exception e) {
                FlashbackClient.LOGGER.error("Exception deleting record folder", e);
            }
        } catch (Exception e) {
            FlashbackClient.LOGGER.error("Exception exporting replay", e);
        }
    }

    @Nullable
    private static FlashbackMeta tryReadMeta(Path file) {
        FlashbackClient.LOGGER.info("Trying to read metadata json {}", file);

        if (!Files.exists(file)) {
            FlashbackClient.LOGGER.error("Metadata JSON doesn't exist!");
            return null;
        }

        try {
            String metaString = Files.readString(file);
            if (metaString.isBlank()) {
                FlashbackClient.LOGGER.error("Metadata JSON is blank");
                return null;
            }

            JsonObject metaObject = GSON.fromJson(metaString, JsonObject.class);
            if (metaObject.isEmpty()) {
                FlashbackClient.LOGGER.error("Metadata JSON is empty");
                return null;
            }

            return FlashbackMeta.fromJson(metaObject);
        } catch (Exception e) {
            FlashbackClient.LOGGER.error("Exception while reading metadata", e);
            return null;
        }
    }

}
