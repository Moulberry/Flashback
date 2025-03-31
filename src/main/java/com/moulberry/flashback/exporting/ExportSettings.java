package com.moulberry.flashback.exporting;

import com.moulberry.flashback.combo_options.AudioCodec;
import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.visuals.ReplayVisuals;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record ExportSettings(@Nullable String name, EditorState editorState,
                             // Initial state
                             Vec3 initialCameraPosition, float initialCameraYaw, float initialCameraPitch,
                             // Capture
                             int resolutionX, int resolutionY, int startTick, int endTick, double framerate,
                             boolean resetRng,
                             // Video
                             VideoContainer container, VideoCodec codec, String encoder, int bitrate, boolean transparent, boolean ssaa, boolean noGui,
                             // Audio
                             boolean recordAudio, boolean stereoAudio, AudioCodec audioCodec,
                             // Output
                             Path output, @Nullable String pngSequenceFormat) {

}
