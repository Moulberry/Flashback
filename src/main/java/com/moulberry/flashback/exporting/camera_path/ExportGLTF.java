package com.moulberry.flashback.exporting.camera_path;

import com.moulberry.flashback.state.EditorState;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record ExportGLTF(@Nullable String name, EditorState editorState,
                         // Properties
                         double scale, int startTick, int endTick,
                         // Output
                         Path output) {

}
