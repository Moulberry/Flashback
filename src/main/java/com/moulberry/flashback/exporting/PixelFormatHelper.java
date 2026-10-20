package com.moulberry.flashback.exporting;

import com.moulberry.flashback.Flashback;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;

import java.util.HashMap;
import java.util.Map;

public class PixelFormatHelper {

    private static final Map<String, Integer> bestPixelFormats = new HashMap<>();
    private static final Map<String, Integer> bestPixelFormatsTransparent = new HashMap<>();

    public static int getBestPixelFormat(String codecName, boolean transparent) {
        Map<String, Integer> best = transparent ? bestPixelFormatsTransparent : bestPixelFormats;

        if (best.containsKey(codecName)) {
            return best.get(codecName);
        }

        int bestPixelFormat = calculateBestPixelFormat(codecName, transparent);

        if (bestPixelFormat == avutil.AV_PIX_FMT_NONE) {
            throw new RuntimeException("Unable to determine best alternate pixel format for " + codecName);
        }
        if (bestPixelFormat != avutil.AV_PIX_FMT_YUV420P) {
            Flashback.LOGGER.info("Chose to use alternate pixel format {} for codec {} with transparent={}", pixelFormatToString(bestPixelFormat), codecName, transparent);
        }

        best.put(codecName, bestPixelFormat);
        return bestPixelFormat;
    }

    private static int calculateBestPixelFormat(String codecName, boolean transparent) {
        try (AVCodec codec = avcodec.avcodec_find_encoder_by_name(codecName)) {
            IntList supportedFormats = new IntArrayList();

            IntPointer pixFmts = codec.pix_fmts();

            if (pixFmts == null) {
                return avutil.AV_PIX_FMT_YUV420P;
            }

            int index = 0;
            while (true) {
                int pixFmt = pixFmts.get(index);
                if (pixFmt == -1) {
                    break;
                }

                if (!transparent && pixFmt == avutil.AV_PIX_FMT_YUV420P) {
                    return avutil.AV_PIX_FMT_YUV420P;
                }

                supportedFormats.add(pixFmt);
                index += 1;
            }

            supportedFormats.add(avutil.AV_PIX_FMT_NONE);

            if (transparent) {
                int format = avcodec.avcodec_find_best_pix_fmt_of_list(supportedFormats.toIntArray(), ExportJob.SRC_PIXEL_FORMAT, 1, new int[1]);
                if (format != avutil.AV_PIX_FMT_NONE) {
                    return format;
                }
            }

            return avcodec.avcodec_find_best_pix_fmt_of_list(supportedFormats.toIntArray(), ExportJob.SRC_PIXEL_FORMAT, 0, new int[1]);
        }
    }

    // Pixel format supports transparency
    private static final Int2BooleanMap pixelFormatSupportsTransparency = new Int2BooleanOpenHashMap();

    public static boolean doesPixelFormatSupportTransparency(int pixelFormat) {
        if (pixelFormatSupportsTransparency.containsKey(pixelFormat)) {
            return pixelFormatSupportsTransparency.get(pixelFormat);
        }

        boolean supports = calculateDoesPixelFormatSupportTransparency(pixelFormat);
        pixelFormatSupportsTransparency.put(pixelFormat, supports);
        return supports;
    }

    private static boolean calculateDoesPixelFormatSupportTransparency(int pixelFormat) {
        try (var descriptor = avutil.av_pix_fmt_desc_get(pixelFormat)) {
            if (descriptor == null || descriptor.isNull()) {
                return false;
            }

            return (descriptor.flags() & avutil.AV_PIX_FMT_FLAG_ALPHA) != 0;
        }
    }

    // Pixel format names
    private static final Int2ObjectMap<String> pixelFormatNames = new Int2ObjectOpenHashMap<>();

    public static String pixelFormatToString(int pixelFormat) {
        if (pixelFormatNames.containsKey(pixelFormat)) {
            return pixelFormatNames.get(pixelFormat);
        }

        String name = pixelFormatToStringInner(pixelFormat);
        pixelFormatNames.put(pixelFormat, name);
        return name;
    }

    private static String pixelFormatToStringInner(int pixelFormat) {
        try (BytePointer name = avutil.av_get_pix_fmt_name(pixelFormat)) {
            return name.getString();
        } catch (Throwable ignored) {}
        return "UNKNOWN(" + pixelFormat + ")";
    }

    public static boolean isYuvFormat(int pixelFormat) {
        try (var descriptor = avutil.av_pix_fmt_desc_get(pixelFormat)) {
            if (descriptor == null || descriptor.isNull()) {
                throw new RuntimeException();
            }

            return (descriptor.flags() & avutil.AV_PIX_FMT_FLAG_RGB) == 0 && descriptor.nb_components() >= 2;
        }
    }

}
