package com.moulberry.flashback.exporting;

import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;

import java.nio.file.Path;

public record ExportSettings(int resolutionX, int resolutionY, int startTick, int endTick, double framerate, VideoContainer container,
                             VideoCodec codec, String encoder, int bitrate, Path output) {

}
