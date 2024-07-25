package com.moulberry.flashback.combo_options;

import com.moulberry.flashback.Flashback;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecHWConfig;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;

import java.util.*;

public enum VideoCodec implements ComboOption {

    H264("H264 (AVC)", avcodec.AV_CODEC_ID_H264),
    H265("H265 (HEVC)", avcodec.AV_CODEC_ID_H265),
    AV1("AV1", avcodec.AV_CODEC_ID_AV1),
    VP9("VP9", avcodec.AV_CODEC_ID_VP9),
    PRO_RES("Apple ProRes", avcodec.AV_CODEC_ID_PRORES),
    QUICK_TIME("QuickTime", avcodec.AV_CODEC_ID_QTRLE);

    private final String text;
    private final int codecId;
    private String[] encoders;

    VideoCodec(String text, int codecId) {
        this.text = text;
        this.codecId = codecId;
    }

    @Override
    public String text() {
        return this.text;
    }

    public int codecId() {
        return this.codecId;
    }

    public String[] getEncoders() {
        if (this.encoders == null) {
            List<String> encodersHardware = new ArrayList<>();
            List<String> encodersHybrid = new ArrayList<>();
            List<String> encodersSoftware = new ArrayList<>();
            List<String> encodersAvoid = new ArrayList<>();

            try (Pointer pointer = new Pointer()) {
                while (true) {
                    try (AVCodec codec = avcodec.av_codec_iterate(pointer)) {
                        if (codec == null) {
                            break;
                        } else if (codec.id() == this.codecId && avcodec.av_codec_is_encoder(codec) != 0) {
                            int capabilities = codec.capabilities();
                            String name = codec.name().getString();

                            if ((capabilities & avcodec.AV_CODEC_CAP_HARDWARE) != 0) {
                                encodersHardware.add(name);
                            } else if ((capabilities & avcodec.AV_CODEC_CAP_HYBRID) != 0 || codecHasHwConfig(codec)) {
                                encodersHybrid.add(name);
                            } else if ((capabilities & avcodec.AV_CODEC_CAP_EXPERIMENTAL) != 0 || name.equals("libaom-av1")) {
                                encodersAvoid.add(name);
                            } else {
                                encodersSoftware.add(name);
                            }
                        }
                    }
                }
            }

            List<String> encoders = new ArrayList<>();
            encoders.addAll(encodersHardware);
            encoders.addAll(encodersHybrid);
            encoders.addAll(encodersSoftware);
            encoders.addAll(encodersAvoid);

            this.encoders = encoders.toArray(new String[0]);
        }

        return this.encoders;
    }

    private static boolean codecHasHwConfig(AVCodec codec) {
        try (AVCodecHWConfig config = avcodec.avcodec_get_hw_config(codec, 0)) {
            return config != null;
        } catch (Throwable t) {
            return false;
        }
    }

}
