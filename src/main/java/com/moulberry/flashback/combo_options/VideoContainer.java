package com.moulberry.flashback.combo_options;

import org.bytedeco.ffmpeg.avformat.AVOutputFormat;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public enum VideoContainer implements ComboOption {

    MP4("MP4", "mp4"),
    MKV("MKV", "mkv"),
    AVI("AVI", "avi"),
    MOV("MOV", "mov"),
    PNG_SEQUENCE("PNG Sequence", "png"),
    EXR_SEQUENCE("EXR Sequence", "exr"),
    WEBP("WebP", "webp"),
    WEBM("WebM", "webm"),
    GIF("GIF", "gif");

    private final String text;
    private final String extension;
    private VideoCodec[] supportedVideoCodecs = null;
    private VideoCodec[] supportedVideoCodecsWithTransparency = null;
    private AudioCodec[] supportedAudioCodecs = null;
    private boolean isImageSequence = false;

    VideoContainer(String text, String extension) {
        this.text = text;
        this.extension = extension;
    }

    @Override
    public String text() {
        return this.text;
    }

    public String extension() {
        return extension;
    }

    public static VideoContainer[] findSupportedContainers(boolean transparency) {
        List<VideoContainer> containers = new ArrayList<>();
        for (VideoContainer videoContainer : VideoContainer.values()) {
            if (videoContainer.getSupportedVideoCodecs(transparency).length != 0) {
                containers.add(videoContainer);
            }
        }
        return containers.toArray(new VideoContainer[0]);
    }

    public boolean isImageSequence() {
        this.getSupportedVideoCodecs(false);
        return this.isImageSequence;
    }

    public VideoCodec[] getSupportedVideoCodecs(boolean transparency) {
        VideoCodec[] codecs = transparency ? this.supportedVideoCodecsWithTransparency : this.supportedVideoCodecs;

        if (codecs == null) {
            List<VideoCodec> supportedCodecs = new ArrayList<>();

            try (AVOutputFormat outputFormat = avformat.av_guess_format(this.extension, "test."+this.extension, null)) {
                for (VideoCodec codec : VideoCodec.values()) {
                    Set<VideoContainer> validContainers = codec.validContainers();
                    if (validContainers != null && !validContainers.contains(this)) {
                        continue;
                    }
                    if (codec.getEncoders().length == 0) {
                        continue;
                    }
                    if (transparency && !codec.supportsTransparency()) {
                        continue;
                    }

                    if (outputFormat.name().getString().equals("image2")) {
                        this.isImageSequence = true;
                    }

                    int ret = avformat.avformat_query_codec(outputFormat, codec.codecId(), avcodec.FF_COMPLIANCE_NORMAL);
                    if (ret == 1) {
                        supportedCodecs.add(codec);
                    }
                }
            }

            codecs = supportedCodecs.toArray(new VideoCodec[0]);
            if (transparency) {
                this.supportedVideoCodecsWithTransparency = codecs;
            } else {
                this.supportedVideoCodecs = codecs;
            }
        }
        return codecs;
    }

    public AudioCodec[] getSupportedAudioCodecs() {
        if (this.supportedAudioCodecs == null) {
            List<AudioCodec> supportedCodecs = new ArrayList<>();
            if (!this.isImageSequence()) {
                try (AVOutputFormat outputFormat = avformat.av_guess_format(this.extension, "test."+this.extension, null)) {
                    for (AudioCodec codec : AudioCodec.values()) {
                        if (codec.getEncoders().length == 0) {
                            continue;
                        }

                        int ret = avformat.avformat_query_codec(outputFormat, codec.codecId(), avcodec.FF_COMPLIANCE_NORMAL);
                        if (ret == 1) {
                            supportedCodecs.add(codec);
                        }
                    }
                }
            }
            this.supportedAudioCodecs = supportedCodecs.toArray(new AudioCodec[0]);
        }
        return this.supportedAudioCodecs;
    }

    public String mimeType() {
        return switch (this) {
            case MP4 -> "video/mp4";
            case MKV -> "video/mkv";
            case AVI -> "video/x-msvideo";
            case MOV -> "video/quicktime";
            case PNG_SEQUENCE -> "image/png";
            case EXR_SEQUENCE -> "image/x-exr";
            case WEBP -> "image/webp";
            case WEBM -> "video/webm";
            case GIF -> "image/gif";
        };
    }
}
