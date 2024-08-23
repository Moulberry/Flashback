package com.moulberry.flashback.state;

import com.moulberry.flashback.Flashback;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class EditorStateManager {

    private static long AUTOSAVE_INTERVAL_MILLIS = 30 * 1000; // 30 seconds

    private static final ReentrantLock lock = new ReentrantLock();
    private static UUID currentUuid = null;
    private static EditorState current = null;
    private static long lastSave = 0;

    public static void saveIfNeeded() {
        try {
            lock.lock();

            if (current == null || currentUuid == null) {
                return;
            }

            long currentTime = System.currentTimeMillis();

            if (!current.dirty) {
                lastSave = currentTime;
            } else if (currentTime < lastSave || currentTime - lastSave > AUTOSAVE_INTERVAL_MILLIS) {
                save();
            }
        } finally {
            lock.unlock();
        }

    }

    private static void save() {
        if (current == null || currentUuid == null) {
            return;
        }

        Path normalPath = getPath(currentUuid, false);
        Path oldPath = getPath(currentUuid, true);

        // Backup
        if (Files.exists(normalPath)) {
            try {
                Files.move(normalPath, oldPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        }

        // Save
        current.save(normalPath);
        lastSave = System.currentTimeMillis();
    }

    private static void load() {
        lastSave = System.currentTimeMillis();

        Path normalPath = getPath(currentUuid, false);
        Path backupPath = getPath(currentUuid, true);

        if (Files.exists(normalPath)) {
            current = EditorState.load(normalPath);
            if (current == null) {
                try {
                    Files.deleteIfExists(normalPath);
                } catch (IOException ignored) {}
            } else {
                return;
            }
        }

        if (Files.exists(backupPath)) {
            current = EditorState.load(backupPath);
            if (current == null) {
                try {
                    Files.deleteIfExists(backupPath);
                } catch (IOException ignored) {}
            } else {
                return;
            }
        }

        current = new EditorState();
    }

    public static void reset() {
        try {
            lock.lock();

            save();
            current = null;
            currentUuid = null;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public static EditorState getCurrent() {
        if (Flashback.isExporting()) {
            return Flashback.EXPORT_JOB.getSettings().editorState();
        }
        if (!Flashback.isInReplay()) {
            return null;
        }
        return current;
    }

    public static EditorState get(UUID replayUuid) {
        Objects.requireNonNull(replayUuid);
        try {
            lock.lock();

            if (current == null || !Objects.equals(currentUuid, replayUuid)) {
                save();
                currentUuid = replayUuid;
                load();
            }

            return current;
        } finally {
            lock.unlock();
        }
    }

    private static Path getPath(UUID replayUuid, boolean old) {
        Objects.requireNonNull(replayUuid);

        String filename = replayUuid + ".json";
        if (old) {
            filename += ".old";
        }
        return Flashback.getDataDirectory()
                .resolve("editor_states")
                .resolve(filename);
    }

}
