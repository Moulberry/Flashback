package com.moulberry.flashback.utils;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class AsyncFileDialogs {

    private static CompletableFuture<String> currentSaveOrOpenFileDialog = null;

    public static boolean hasDialog() {
        return currentSaveOrOpenFileDialog != null;
    }

    private record FileFilter(SDL_DialogFileFilter.Buffer buffer, ByteBuffer encodedFilterDescription, ByteBuffer encodedFilter) {
        private void free() {
            this.buffer.free();
            MemoryUtil.memFree(this.encodedFilterDescription);
            MemoryUtil.memFree(this.encodedFilter);
        }
    }

    private static FileFilter createFilterBuffer(String filterDescription, String... filters) {
        StringBuilder filterBuilder = new StringBuilder();

        for (String filter : filters) {
            if (!filterBuilder.isEmpty()) filterBuilder.append(";");
            filterBuilder.append(filter(filter));
        }

        SDL_DialogFileFilter.Buffer fileFilter = SDL_DialogFileFilter.calloc(1);
        ByteBuffer encodedFilterDescription = MemoryUtil.memUTF8(filter(filterDescription), true);
        ByteBuffer encodedFilter = MemoryUtil.memUTF8(filter(filterBuilder.toString()), true);
        fileFilter.get(0)
                  .name(encodedFilterDescription)
                  .pattern(encodedFilter);

        return new FileFilter(fileFilter, encodedFilterDescription, encodedFilter);
    }

    public static CompletableFuture<String> saveFileDialog(String defaultPath, String defaultName, String filterDescription, String... filters) {
        if (hasDialog()) return CompletableFuture.completedFuture(null);

        currentSaveOrOpenFileDialog = new CompletableFuture<>();
        CompletableFuture<String> future = currentSaveOrOpenFileDialog;

        String defaultLocation = filter(defaultPath + "/" + defaultName);

        var fileFilter = createFilterBuffer(filterDescription, filters);

        long window = Minecraft.getInstance().getWindow().handle();
        SDLDialog.SDL_ShowSaveFileDialog((userdata, filelist, selectedFilter) -> {
            fileFilter.free();

            if (future == currentSaveOrOpenFileDialog) {
                currentSaveOrOpenFileDialog = null;
            }

            if (filelist == MemoryUtil.NULL) {
                Flashback.LOGGER.error("Error occurred during save file dialog: {}", SDLError.SDL_GetError());
                future.complete(null);
                return;
            }

            long filePtr = MemoryUtil.memGetAddress(filelist);
            future.complete(MemoryUtil.memUTF8Safe(filePtr));
        }, 0, window, fileFilter.buffer(), defaultLocation);

        return future;
    }

    public static CompletableFuture<String> openFileDialog(String defaultPath, String filterDescription, String... filters) {
        if (hasDialog()) return CompletableFuture.completedFuture(null);

        currentSaveOrOpenFileDialog = new CompletableFuture<>();
        CompletableFuture<String> future = currentSaveOrOpenFileDialog;

        var fileFilter = createFilterBuffer(filterDescription, filters);

        long window = Minecraft.getInstance().getWindow().handle();
        SDLDialog.SDL_ShowOpenFileDialog((userdata, filelist, selectedFilter) -> {
            fileFilter.free();

            if (future == currentSaveOrOpenFileDialog) {
                currentSaveOrOpenFileDialog = null;
            }

            if (filelist == MemoryUtil.NULL) {
                Flashback.LOGGER.error("Error occurred during open file dialog: {}", SDLError.SDL_GetError());
                future.complete(null);
                return;
            }

            long filePtr = MemoryUtil.memGetAddress(filelist);
            future.complete(MemoryUtil.memUTF8Safe(filePtr));
        }, 0, window, fileFilter.buffer(), filter(defaultPath), false);

        return future;
    }


    public static CompletableFuture<String> openFolderDialog(String defaultPath) {
        if (hasDialog()) return CompletableFuture.completedFuture(null);

        currentSaveOrOpenFileDialog = new CompletableFuture<>();
        CompletableFuture<String> future = currentSaveOrOpenFileDialog;

        long window = Minecraft.getInstance().getWindow().handle();
        SDLDialog.SDL_ShowOpenFolderDialog((userdata, filelist, selectedFilter) -> {
            if (future == currentSaveOrOpenFileDialog) {
                currentSaveOrOpenFileDialog = null;
            }

            if (filelist == MemoryUtil.NULL) {
                Flashback.LOGGER.error("Error occurred during open folder dialog: {}", SDLError.SDL_GetError());
                future.complete(null);
                return;
            }

            long filePtr = MemoryUtil.memGetAddress(filelist);
            future.complete(MemoryUtil.memUTF8Safe(filePtr));
        }, 0, window, filter(defaultPath), false);

        return future;
    }

    public static String filter(CharSequence in) {
        return filterLT20(in.toString()
                .replace("'", "")
                .replace("\"", "")
                .replace("$", "")
                .replace("`", ""));
    }

    public static String filterLT20(CharSequence in) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c >= 32 || c == '\n') builder.append(c);
        }
        return builder.toString();
    }

}
