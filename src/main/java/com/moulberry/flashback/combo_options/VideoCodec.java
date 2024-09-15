package com.moulberry.flashback.combo_options;

import com.moulberry.flashback.exporting.PixelFormatHelper;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecHWConfig;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.Pointer;

import java.util.*;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.avutil.av_find_nearest_q_idx;

public enum VideoCodec implements ComboOption {

    H264("H264 (AVC)", avcodec.AV_CODEC_ID_H264),
    H265("H265 (HEVC)", avcodec.AV_CODEC_ID_H265),
    AV1("AV1", avcodec.AV_CODEC_ID_AV1),
    VP9("VP9", avcodec.AV_CODEC_ID_VP9),
    PRO_RES("Apple ProRes", avcodec.AV_CODEC_ID_PRORES),
    QUICK_TIME("QuickTime", avcodec.AV_CODEC_ID_QTRLE),
    WEBP("WebP", AV_CODEC_ID_WEBP),
    GIF("GIF", AV_CODEC_ID_GIF);

    private final String text;
    private final int codecId;
    private String[] encoders;
    private boolean supportsTransparency = false;

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

    public boolean supportsTransparency() {
        if (this == VP9 || this == H264 || this == H265) {
            // VP9 supports transparency (yuva420p), but apparently most decoders for it do not. Let's not mark it as supporting transparency
            // See also: https://trac.ffmpeg.org/ticket/8468

            // H264/H265 sometimes claim to support transparency but don't, also ignore them as well
            return false;
        }
        if (this.encoders == null) {
            this.getEncoders();
        }
        return this.supportsTransparency;
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
                        try {
                            if (codec == null) {
                                break;
                            }
                            if (codec.id() != this.codecId) {
                                continue;
                            }
                            if (avcodec.av_codec_is_encoder(codec) == 0) {
                                continue;
                            }
                            if (!doesEncoderWork(codec)) {
                                continue;
                            }

                            int capabilities = codec.capabilities();
                            String name = codec.name().getString();

                            this.supportsTransparency = false;
                            for (int i = 0;; i++) {
                                int pixFmt = codec.pix_fmts().get(i);
                                if (pixFmt == -1) {
                                    break;
                                } else {
                                    if (PixelFormatHelper.doesPixelFormatSupportTransparency(pixFmt)) {
                                        // System.out.println(name + " supports transparency because of " + PixelFormatHelper.pixelFormatToString(pixFmt));
                                        this.supportsTransparency = true;
                                        break;
                                    }
                                }
                            }

                            if ((capabilities & avcodec.AV_CODEC_CAP_HARDWARE) != 0) {
                                encodersHardware.add(name);
                            } else if ((capabilities & avcodec.AV_CODEC_CAP_HYBRID) != 0 || codecHasHwConfig(codec)) {
                                encodersHybrid.add(name);
                            } else if ((capabilities & avcodec.AV_CODEC_CAP_EXPERIMENTAL) != 0 || name.equals("libaom-av1")) {
                                encodersAvoid.add(name);
                            } else {
                                encodersSoftware.add(name);
                            }
                        } finally {
                            if (codec != null) {
                                codec.close();
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

    private static boolean doesEncoderWork(AVCodec codec) {
        AVCodecContext codecContext = null;
        AVDictionary options = new AVDictionary(null);

        try {
            if ((codecContext = avcodec.avcodec_alloc_context3(codec)) == null) {
                return false;
            }

            // Setup dummy parameters
            codecContext.codec_id(codec.id());
            codecContext.codec_type(AVMEDIA_TYPE_VIDEO);
            codecContext.bit_rate(400000);
            codecContext.width(1920);
            codecContext.height(1080);

            AVRational frameRate = av_d2q(60.0, 1001000);
            AVRational supportedFramerates = codec.supported_framerates();
            if (supportedFramerates != null) {
                int idx = av_find_nearest_q_idx(frameRate, supportedFramerates);
                frameRate = supportedFramerates.position(idx);
            }

            AVRational time_base = av_inv_q(frameRate);
            codecContext.time_base(time_base);

            int pixelFormat = PixelFormatHelper.getBestPixelFormat(codec.name().getString(), false);
            codecContext.pix_fmt(pixelFormat);

            if ((codec.capabilities() & AV_CODEC_CAP_EXPERIMENTAL) != 0) {
                codecContext.strict_std_compliance(FF_COMPLIANCE_EXPERIMENTAL);
            }

            return avcodec.avcodec_open2(codecContext, codec, options) >= 0;
        } finally {
            if (codecContext != null) {
                avcodec.avcodec_close(codecContext);
                codecContext.close();
            }
            avutil.av_dict_free(options);
        }

    }

    private static boolean codecHasHwConfig(AVCodec codec) {
        try (AVCodecHWConfig config = avcodec.avcodec_get_hw_config(codec, 0)) {
            if (config != null) {
                config.close();
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

}
