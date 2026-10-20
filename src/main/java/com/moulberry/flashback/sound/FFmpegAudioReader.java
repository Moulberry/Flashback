package com.moulberry.flashback.sound;

import com.moulberry.flashback.Flashback;
import io.netty.buffer.Unpooled;
import net.minecraft.util.Mth;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.swresample.*;
import static org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN;

public class FFmpegAudioReader {

    public record RawAudioData(ByteBuffer byteBuffer, AudioFormat audioFormat) {}

    public static RawAudioData read(InputStream inputStream) {
        var readCallback = new Read_packet_Pointer_BytePointer_int() {
            @Override
            public int call(Pointer pointer, BytePointer bytePointer, int size) {
                try {
                    byte[] b = new byte[size];
                    int read = inputStream.read(b, 0, size);
                    if (read < 0) {
                        return AVERROR_EOF();
                    } else {
                        bytePointer.put(b, 0, read);
                        return read;
                    }
                } catch (Throwable t) {
                    Flashback.LOGGER.error("Error while reading audio data", t);
                    return -1;
                }
            }
        };

        AVFormatContext formatContext = null;
        AVIOContext avioContext = null;
        AVCodecContext codecContext = null;
        AVFrame avFrame = null;
        AVPacket avPacket = null;
        SwrContext swrContext = null;
        PointerPointer<BytePointer> planeOutPtr = new PointerPointer<>(AVFrame.AV_NUM_DATA_POINTERS).retainReference();
        PointerPointer<BytePointer> planeInPtr = new PointerPointer<>(AVFrame.AV_NUM_DATA_POINTERS).retainReference();
        BytePointer convertOutPtr = null;

        try {
            formatContext = avformat_alloc_context();
            avioContext = avio_alloc_context(new BytePointer(av_malloc(4096)), 4096, 0, formatContext, readCallback, null, null);
            formatContext.pb(avioContext);

            if (avformat_open_input(formatContext, inputStream.toString(), null, null) < 0) {
                Flashback.LOGGER.error("avformat_open_input error");
                return null;
            }

            if (avformat_find_stream_info(formatContext, (AVDictionary) null) < 0) {
                Flashback.LOGGER.error("avformat_find_stream_info error");
                return null;
            }

            AVStream audioStream = null;
            AVCodecParameters audioCodecParameters = null;
            AVCodec audioCodec = null;

            int numStreams = formatContext.nb_streams();
            for (int streamIndex = 0; streamIndex < numStreams; streamIndex++) {
                AVStream stream = formatContext.streams(streamIndex);
                AVCodecParameters codecParameters = stream.codecpar();

                if (codecParameters.codec_type() != AVMEDIA_TYPE_AUDIO) {
                    continue;
                }

                AVCodec decoder = avcodec_find_decoder(codecParameters.codec_id());
                if (decoder == null) {
                    continue;
                }

                audioStream = stream;
                audioCodecParameters = codecParameters;
                audioCodec = decoder;
            }

            if (audioStream == null) {
                Flashback.LOGGER.error("Unable to find audio stream");
                return null;
            }

            // Allocate context for codec
            codecContext = avcodec_alloc_context3(audioCodec);

            // Copy parameters from audio stream to codec context
            if (avcodec_parameters_to_context(codecContext, audioCodecParameters) < 0) {
                Flashback.LOGGER.error("Unable to copy parameters from stream to codec");
                return null;
            }

            if (avcodec_open2(codecContext, audioCodec, (AVDictionary) null) < 0) {
                Flashback.LOGGER.error("Error opening codec");
                return null;
            }

            avFrame = av_frame_alloc();
            if (avFrame == null) {
                Flashback.LOGGER.error("Unable to allocate av_frame");
                return null;
            }

            avPacket = av_packet_alloc();
            if (avPacket == null) {
                Flashback.LOGGER.error("Unable to allocate av_packet");
                return null;
            }

            int audioStreamIndex = audioStream.index();

            int originalChannels = codecContext.channels();
            int originalSampleFormat = codecContext.sample_fmt();
            int channels = Math.min(2, originalChannels);
            int sampleFormat = originalSampleFormat;

            if (sampleFormat != AV_SAMPLE_FMT_U8 && sampleFormat != AV_SAMPLE_FMT_S16) {
                sampleFormat = AV_SAMPLE_FMT_S16;
            }

            int sampleRate = codecContext.sample_rate();

            // Minecraft only supports non-planar signed 8bit/16bit mono/stereo. Need to convert to that.
            if (channels != originalChannels || sampleFormat != originalSampleFormat) {
                swrContext = swr_alloc_set_opts(null, av_get_default_channel_layout(channels), sampleFormat, sampleRate,
                        av_get_default_channel_layout(originalChannels), originalSampleFormat, sampleRate, 0, null);
                if (swrContext == null) {
                    Flashback.LOGGER.error("Unable to allocate swr convert context");
                    return null;
                } else if (swr_init(swrContext) < 0) {
                    Flashback.LOGGER.error("Unable to initialize swr convert context");
                    return null;
                }
            }

            int lastBufferCapacity = 4096;
            List<ByteBuffer> rawDataBuffers = new ArrayList<>();
            ByteBuffer currentByteBuffer = null;

            while (true) {
                boolean endOfStream = false;

                // Read frame
                int ret = av_read_frame(formatContext, avPacket);
                if (ret == AVERROR_EAGAIN()) {
                    continue;
                } else if (ret < 0) {
                    // Pass through null data in case the codec has buffering
                    avPacket.stream_index(audioStreamIndex);
                    avPacket.flags(AV_PKT_FLAG_KEY);
                    avPacket.data(null);
                    avPacket.size(0);
                    endOfStream = true;
                }

                // Skip any packets that aren't from the audio stream we want
                if (avPacket.stream_index() != audioStreamIndex) {
                    continue;
                }

                // send packet to decoder
                ret = avcodec_send_packet(codecContext, avPacket);
                if (ret < 0) {
                    Flashback.LOGGER.error("Error sending packet to decoder: {}", ret);
                    return null;
                }

                while (true) {
                    ret = avcodec_receive_frame(codecContext, avFrame);
                    if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
                        break;
                    } else if (ret < 0) {
                        Flashback.LOGGER.error("Error receiving frame from decoder: {}", ret);
                        return null;
                    }

                    ByteBuffer outBuffer;

                    if (swrContext != null) {
                        int bufferInPlanes = av_sample_fmt_is_planar(originalSampleFormat) != 0 ? avFrame.channels() : 1;
                        int samplesIn = avFrame.nb_samples();
                        int bufferInSize = av_samples_get_buffer_size((IntPointer) null, originalChannels,
                                samplesIn, originalSampleFormat, 1) / bufferInPlanes;
                        BytePointer[] pointersIn = new BytePointer[bufferInPlanes];

                        for (int plane = 0; plane < bufferInPlanes; plane++) {
                            pointersIn[plane] = avFrame.data(plane).capacity(bufferInSize);
                        }

                        int samplesOut = swr_get_out_samples(swrContext, samplesIn);


                        int sampleOutBytes = av_get_bytes_per_sample(sampleFormat);
                        int bufferOutSize = samplesOut * sampleOutBytes * channels;
                        BytePointer[] pointersOut = new BytePointer[1];

                        if (convertOutPtr == null || convertOutPtr.capacity() < bufferOutSize) {
                            if (convertOutPtr != null) {
                                av_free(convertOutPtr.position(0));
                            }
                            convertOutPtr = new BytePointer(av_malloc(bufferOutSize)).capacity(bufferOutSize);
                        }
                        pointersOut[0] = convertOutPtr;

                        ret = swr_convert(swrContext, planeOutPtr.put(pointersOut), samplesOut, planeInPtr.put(pointersIn), samplesIn);
                        if (ret < 0) {
                            Flashback.LOGGER.error("Error running swr_convert: {}", ret);
                            return null;
                        }

                        int outSize = ret * sampleOutBytes * channels;
                        pointersOut[0].position(0).limit(outSize);
                        outBuffer = pointersOut[0].asBuffer().position(0).limit(outSize);
                    } else {
                        int bufferSize = av_samples_get_buffer_size((IntPointer) null, channels,
                                avFrame.nb_samples(), sampleFormat, 1);

                        BytePointer outPointer = avFrame.data(0).position(0).limit(bufferSize);
                        outBuffer = outPointer.asBuffer().position(0).limit(bufferSize);
                    }

                    if (currentByteBuffer == null || outBuffer.remaining() > currentByteBuffer.remaining()) {
                        if (currentByteBuffer != null) {
                            currentByteBuffer.flip();
                            rawDataBuffers.add(currentByteBuffer);
                        }

                        lastBufferCapacity = Math.max(lastBufferCapacity * 2, Mth.smallestEncompassingPowerOfTwo(outBuffer.remaining()));
                        currentByteBuffer = BufferUtils.createByteBuffer(lastBufferCapacity);
                    }

                    currentByteBuffer.put(outBuffer);
                }

                av_packet_unref(avPacket);

                if (endOfStream) {
                    break;
                }
            }

            if (currentByteBuffer == null) {
                Flashback.LOGGER.error("Unable to read audio file: no data");
                return null;
            }

            currentByteBuffer.flip();
            rawDataBuffers.add(currentByteBuffer);

            int finalSize = 0;
            for (ByteBuffer rawDataBuffer : rawDataBuffers) {
                finalSize += rawDataBuffer.remaining();
            }
            ByteBuffer finalBuffer = BufferUtils.createByteBuffer(finalSize);
            for (ByteBuffer rawDataBuffer : rawDataBuffers) {
                finalBuffer.put(rawDataBuffer);
            }
            finalBuffer.flip();

            return new RawAudioData(finalBuffer, new AudioFormat(sampleRate, sampleFormat == AV_SAMPLE_FMT_U8 ? 8 : 16, channels,
                    true, ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN));
        } catch (Exception e) {
            Flashback.LOGGER.error("Exception thrown while reading audio", e);
            return null;
        } finally {
            if (planeOutPtr != null) {
                planeOutPtr.releaseReference();
            }
            if (planeInPtr != null) {
                planeInPtr.releaseReference();
            }
            if (convertOutPtr != null) {
                av_free(convertOutPtr.position(0));
            }
            if (formatContext != null) {
                avformat_free_context(formatContext);
            }
            if (avioContext != null) {
                if (avioContext.buffer() != null) {
                    av_free(avioContext.buffer());
                    avioContext.buffer(null);
                }
                av_free(avioContext);
            }
            if (codecContext != null) {
                avcodec_free_context(codecContext);
            }
            if (avFrame != null) {
                av_frame_free(avFrame);
            }
            if (avPacket != null) {
                av_packet_free(avPacket);
            }
            if (swrContext != null) {
                swr_free(swrContext);
            }
        }
    }

}
