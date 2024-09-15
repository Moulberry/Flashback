package com.moulberry.flashback;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TempFolderProvider {

    public enum TempFolderType {
        SERVER("server"),
        RECORDING("recording");

        private final String id;

        TempFolderType(String id) {
            this.id = id;
        }
    }

    private static Path getSharedTempFolder() {
        return Flashback.getDataDirectory().resolve("temp");
    }

    public static Path getTypedTempFolder(TempFolderType type) {
        return getSharedTempFolder().resolve(type.id);
    }

    public static void tryDeleteStaleFolders(TempFolderType type) {
        Path tempFolder = getSharedTempFolder();

        if (!Files.exists(tempFolder)) {
            return;
        }

        Path typedTempFolder = tempFolder.resolve(type.id);
        if (!Files.exists(typedTempFolder)) {
            return;
        }

        Set<Path> toDelete = new HashSet<>();

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(typedTempFolder)) {
            for (Path path : directoryStream) {
                if (!Files.isDirectory(path)) {
                    continue;
                }

                Path lockFile = path.resolve("flashback_pid");

                String pidStr = "?";
                boolean isStillInUse = false;
                try {
                    pidStr = Files.readString(lockFile);
                    long pid = Long.parseLong(pidStr);
                    isStillInUse = ProcessHandle.of(pid).isPresent();
                } catch (Exception ignored) {}

                if (!isStillInUse) {
                    toDelete.add(path);
                } else {
                    Flashback.LOGGER.error("Cannot delete stale temp folder {}, pid {} is still in use", tempFolder.relativize(path), pidStr);
                }
            }
        } catch (IOException e) {
            Flashback.LOGGER.error("Failed to find stale temp folders to delete", e);
        }

        for (Path path : toDelete) {
            Flashback.LOGGER.info("Deleting stale temp folder {}", tempFolder.relativize(path));
            try {
                FileUtils.deleteDirectory(path.toFile());
            } catch (Exception e) {
                Flashback.LOGGER.error("Failed to delete stale temp folder", e);
            }
        }

        deleteDirectoryIfEmpty(typedTempFolder);
        deleteDirectoryIfEmpty(tempFolder);
    }

    public static Path createTemp(TempFolderType type, UUID uuid) {
        Path path = getTempPath(type, uuid);

        if (!Files.exists(path)) {
            // Create directories
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Write pid file
            long pid = ProcessHandle.current().pid();
            try {
                Files.writeString(path.resolve("flashback_pid"), String.valueOf(pid));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return path;
    }

    private static Path getTempPath(TempFolderType type, UUID uuid) {
        return getSharedTempFolder()
                .resolve(type.id)
                .resolve(uuid.toString());
    }

    public static void deleteTemp(TempFolderType type, UUID uuid) {
        Path path = getTempPath(type, uuid);

        // Delete directory
        try {
            FileUtils.deleteDirectory(path.toFile());
        } catch (Exception e) {
            Flashback.LOGGER.error("Failed to delete temp directory", e);
        }

        deleteDirectoryIfEmpty(path.getParent());
        deleteDirectoryIfEmpty(path.getParent().getParent());
    }

    public static void deleteDirectoryIfEmpty(Path path) {
        if (!Files.exists(path)) {
            return;
        }

        try {
            boolean empty;
            try (var stream = Files.newDirectoryStream(path)) {
                empty = !stream.iterator().hasNext();
            }
            if (empty) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {}
    }

}
