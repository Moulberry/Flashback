package com.moulberry.flashback.configuration;

import com.moulberry.flashback.combo_options.AudioCodec;
import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;

import java.util.Arrays;

public class ForceDefaultExportSettings {

    public int[] resolution = null;
    public float[] framerate = null;
    public Boolean resetRng = null;
    public Boolean ssaa = null;
    public Boolean noGui = null;

    public VideoContainer container = null;
    public VideoCodec videoCodec = null;
    public int[] selectedVideoEncoder = null;
    public Boolean useMaximumBitrate = null;

    public Boolean recordAudio = null;
    public Boolean transparentBackground = null;
    public AudioCodec audioCodec = null;
    public Boolean stereoAudio = null;

    public String defaultExportPath = null;

    public void apply(FlashbackConfig config) {
        if (this.resolution != null) {
            config.resolution = Arrays.copyOf(this.resolution, 2);
        }
        if (this.framerate != null) {
            config.framerate = Arrays.copyOf(this.framerate, 1);
        }
        if (this.resetRng != null) {
            config.resetRng = this.resetRng;
        }
        if (this.ssaa != null) {
            config.ssaa = this.ssaa;
        }
        if (this.noGui != null) {
            config.noGui = this.noGui;
        }
        if (this.container != null) {
            config.container = this.container;
        }
        if (this.videoCodec != null) {
            config.videoCodec = this.videoCodec;
        }
        if (this.selectedVideoEncoder != null) {
            config.selectedVideoEncoder =  Arrays.copyOf(this.selectedVideoEncoder, 1);
        }
        if (this.useMaximumBitrate != null) {
            config.useMaximumBitrate = this.useMaximumBitrate;
        }
        if (this.recordAudio != null) {
            config.recordAudio = this.recordAudio;
        }
        if (this.transparentBackground != null) {
            config.transparentBackground = this.transparentBackground;
        }
        if (this.audioCodec != null) {
            config.audioCodec = this.audioCodec;
        }
        if (this.stereoAudio != null) {
            config.stereoAudio = this.stereoAudio;
        }
        if (this.defaultExportPath != null) {
            config.defaultExportPath = this.defaultExportPath;
        }
    }

}
