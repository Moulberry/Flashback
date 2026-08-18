package com.moulberry.flashback.exporting;

import java.util.Map;

public class EncoderQuirks {

    private static final int HARD_MINIMUM_FRAME_SIZE = 2;
    private static final int HARD_MAXIMUM_FRAME_SIZE = 16384;
    private static final int HARD_MAXIMUM_FRAME_AREA = HARD_MAXIMUM_FRAME_SIZE*HARD_MAXIMUM_FRAME_SIZE;

    private static final Map<String, Integer> MINIMUM_FRAME_SIZE_BY_ENCODER = Map.ofEntries(
        Map.entry("h264_amf", 128),
        Map.entry("hevc_amf", 128),
        Map.entry("h264_nvenc", 145),
        Map.entry("hevc_nvenc", 145)
    );
    private static final Map<String, Integer> MAXIMUM_FRAME_SIZE_BY_ENCODER = Map.ofEntries(
        Map.entry("h264_amf", 4096),
        Map.entry("hevc_amf", 8192),
        Map.entry("h264_nvenc", 4096),
        Map.entry("hevc_nvenc", 8192)
    );
    private static final Map<String, Integer> MAXIMUM_FRAME_AREA_BY_ENCODER = Map.ofEntries(
        Map.entry("libopenh264", 3072*3072),
        Map.entry("hevc_amf", 4352*4352)
    );

    public static int minimumFrameSize(String encoder) {
        return MINIMUM_FRAME_SIZE_BY_ENCODER.getOrDefault(encoder, HARD_MINIMUM_FRAME_SIZE);
    }

    public static int maximumFrameSize(String encoder) {
        return MAXIMUM_FRAME_SIZE_BY_ENCODER.getOrDefault(encoder, HARD_MAXIMUM_FRAME_SIZE);
    }

    public static int maximumFrameArea(String encoder) {
        return MAXIMUM_FRAME_AREA_BY_ENCODER.getOrDefault(encoder, HARD_MAXIMUM_FRAME_AREA);
    }

}
