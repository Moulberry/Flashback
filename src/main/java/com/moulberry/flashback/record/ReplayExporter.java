package com.moulberry.flashback.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.moulberry.flashback.Flashback;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ReplayExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void export(Path recordFolder, Path outputFile, @Nullable String name) {
        Flashback.LOGGER.info("Exporting {} to {}", recordFolder, outputFile);

        FlashbackMeta meta = tryReadMeta(recordFolder.resolve("metadata.json"));
        if (meta == null) {
            meta = tryReadMeta(recordFolder.resolve("metadata.json.old"));
        }
        if (meta == null) {
            Flashback.LOGGER.error("Cannot export, both metadata files are invalid");
            return;
        }

        if (name != null) {
            meta.name = name;
        }

        // Validate
        meta.chunks.keySet().removeIf(chunkName -> {
            Path chunkPath = recordFolder.resolve(chunkName);
            if (!Files.exists(chunkPath)) {
                Flashback.LOGGER.warn("Cannot find chunk path: {}, skipping", chunkPath);
                return true;
            } else {
                return false;
            }
        });

        if (meta.chunks.isEmpty()) {
            Flashback.LOGGER.error("Cannot export, no chunk files exist");
            return;
        }

        try {
            Files.createDirectories(outputFile.getParent());
        } catch (IOException e) {
            Flashback.LOGGER.error("Unable to create parent directories", e);
        }

        try {
            FileOutputStream fos = new FileOutputStream(outputFile.toFile());
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            ZipOutputStream zipOut = new ZipOutputStream(bos);
            zipOut.setLevel(Deflater.BEST_SPEED);

            // Write metadata
            ZipEntry zipEntry = new ZipEntry("metadata.json");
            zipOut.putNextEntry(zipEntry);
            zipOut.write(GSON.toJson(meta.toJson()).getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();

            // Write chunked level chunk caches
            Path levelChunkCaches = recordFolder.resolve("level_chunk_caches");
            if (Files.exists(levelChunkCaches) && Files.isDirectory(levelChunkCaches)) {
                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(levelChunkCaches)) {
                    for (Path path : directoryStream) {
                        zipEntry = new ZipEntry("level_chunk_caches/" + path.getFileName().toString());
                        zipOut.putNextEntry(zipEntry);
                        Files.copy(path, zipOut);
                        zipOut.closeEntry();
                    }
                }
            }

            // Write level chunk cache
            Path levelChunkCachePath = recordFolder.resolve("level_chunk_cache");
            if (Files.exists(levelChunkCachePath)) {
                zipEntry = new ZipEntry("level_chunk_cache");
                zipOut.putNextEntry(zipEntry);
                Files.copy(levelChunkCachePath, zipOut);
                zipOut.closeEntry();
            }

            // Write icon
            Path iconPath = recordFolder.resolve("icon.png");
            if (Files.exists(iconPath)) {
                zipEntry = new ZipEntry("icon.png");
                zipOut.putNextEntry(zipEntry);
                Files.copy(iconPath, zipOut);
                zipOut.closeEntry();
            }

            // Write chunks
            for (String chunkName : meta.chunks.keySet()) {
                Path chunkPath = recordFolder.resolve(chunkName);

                zipEntry = new ZipEntry(chunkName);
                zipOut.putNextEntry(zipEntry);
                Files.copy(chunkPath, zipOut);
                zipOut.closeEntry();
            }

            zipOut.close();
            bos.close();
            fos.close();

            // Delete record folder
            try {
                FileUtils.deleteDirectory(recordFolder.toFile());
            } catch (Exception e) {
                Flashback.LOGGER.error("Exception deleting record folder", e);
            }
        } catch (Exception e) {
            Flashback.LOGGER.error("Exception exporting replay", e);
        }
    }

    @Nullable
    private static FlashbackMeta tryReadMeta(Path file) {
        Flashback.LOGGER.info("Trying to read metadata json {}", file);

        if (!Files.exists(file)) {
            Flashback.LOGGER.error("Metadata JSON doesn't exist!");
            return null;
        }

        try {
            String metaString = Files.readString(file);
            if (metaString.isBlank()) {
                Flashback.LOGGER.error("Metadata JSON is blank");
                return null;
            }

            JsonObject metaObject = GSON.fromJson(metaString, JsonObject.class);
            if (metaObject.isEmpty()) {
                Flashback.LOGGER.error("Metadata JSON is empty");
                return null;
            }

            return FlashbackMeta.fromJson(metaObject);
        } catch (Exception e) {
            Flashback.LOGGER.error("Exception while reading metadata", e);
            return null;
        }
    }

}
