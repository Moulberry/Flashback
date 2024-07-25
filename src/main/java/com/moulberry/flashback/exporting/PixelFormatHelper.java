package com.moulberry.flashback.exporting;

import com.moulberry.flashback.Flashback;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;

import java.util.HashMap;
import java.util.Map;

public class PixelFormatHelper {

    private static final Map<String, Integer> bestPixelFormats = new HashMap<>();

    public static int getBestPixelFormat(String codecName) {
        if (bestPixelFormats.containsKey(codecName)) {
            return bestPixelFormats.get(codecName);
        }

        int bestPixelFormat = calculateBestPixelFormat(codecName);

        if (bestPixelFormat == avutil.AV_PIX_FMT_NONE) {
            throw new RuntimeException("Unable to determine best alternate pixel format for " + codecName);
        }
        if (bestPixelFormat != avutil.AV_PIX_FMT_YUV420P) {
            Flashback.LOGGER.info("Chose to use alternate pixel format {} for codec {}", pixelFormatToString(bestPixelFormat), codecName);
        }

        bestPixelFormats.put(codecName, bestPixelFormat);
        return bestPixelFormat;
    }

    private static int calculateBestPixelFormat(String codecName) {
        try (AVCodec codec = avcodec.avcodec_find_encoder_by_name(codecName)) {
            IntList supportedFormats = new IntArrayList();

            IntPointer pixFmts = codec.pix_fmts();

            int index = 0;
            while (true) {
                int pixFmt = pixFmts.get(index);
                if (pixFmt == -1) {
                    break;
                }

                if (pixFmt == avutil.AV_PIX_FMT_YUV420P) {
                    return avutil.AV_PIX_FMT_YUV420P;
                }

                supportedFormats.add(pixFmt);
                index += 1;
            }

            supportedFormats.add(avutil.AV_PIX_FMT_NONE);

            return avcodec.avcodec_find_best_pix_fmt_of_list(supportedFormats.toIntArray(), avutil.AV_PIX_FMT_BGRA, 0, new int[1]);
        }
    }

    private static String pixelFormatToString(int pixelFormat) {
        try (BytePointer name = avutil.av_get_pix_fmt_name(pixelFormat)) {
            return name.getString();
        } catch (Throwable ignored) {}
        return "UNKNOWN(" + pixelFormat + ")";
    }

}
