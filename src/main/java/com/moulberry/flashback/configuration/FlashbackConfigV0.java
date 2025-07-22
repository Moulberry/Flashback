package com.moulberry.flashback.configuration;

import com.moulberry.flashback.combo_options.AudioCodec;
import com.moulberry.flashback.combo_options.MovementDirection;
import com.moulberry.flashback.combo_options.RecordingControlsLocation;
import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.screen.select_replay.ReplaySorting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlashbackConfigV0 {

    public boolean automaticallyStart = false;
    public boolean automaticallyFinish = true;
    public boolean showRecordingToasts = true;
    public boolean quicksave = false;
    public boolean hidePauseMenuControls = false;
    public boolean markDimensionChanges = true;
    public boolean recordHotbar = false;
    public int localPlayerUpdatesPerSecond = 20;
    public boolean recordVoiceChat = false;

    public Set<String> openedWindows = new HashSet<>();
    public long nextUnsupportedModLoaderWarning = 0;

    public boolean flightCameraDirection = false;
    public float flightMomentum = 1.0f;
    public boolean flightLockX = false;
    public boolean flightLockY = false;
    public boolean flightLockZ = false;
    public boolean flightLockYaw = false;
    public boolean flightLockPitch = false;

    public List<String> recentReplays = new ArrayList<>();
    public String defaultExportFilename = "%date%T%time%";
    public InterpolationType defaultInterpolationType = InterpolationType.SMOOTH;
    public boolean useRealtimeInterpolation = true;

    public boolean disableIncreasedFirstPersonUpdates = false;
    public boolean disableThirdPersonCancel = false;
    public int[] exportRenderDummyFrames = new int[]{0};

    public ReplaySorting replaySorting = ReplaySorting.CREATED_DATE;
    public boolean sortDescending = true;

    public int[] resolution = new int[]{1920, 1080};
    public float[] framerate = new float[]{60};
    public boolean resetRng = false;
    public boolean ssaa = false;
    public boolean noGui = false;

    public VideoContainer container = null;
    public VideoCodec videoCodec = null;
    public int[] selectedVideoEncoder = new int[]{0};
    public boolean useMaximumBitrate = false;

    public boolean recordAudio = false;
    public boolean transparentBackground = false;
    public AudioCodec audioCodec = AudioCodec.AAC;
    public boolean stereoAudio = false;

    public String defaultExportPath = null;

    public float defaultOverrideFov = 70.0f;
    public boolean enableOverrideFovByDefault = false;

    public ForceDefaultExportSettings forceDefaultExportSettings = new ForceDefaultExportSettings();

    public boolean filterUnnecessaryPackets = true;

    public boolean signedRenderFilter = false;
    public int viewedTipsOfTheDay = 0;
    public boolean showTipOfTheDay = true;
    public long nextTipOfTheDay = 0;

    public void migrateTo(FlashbackConfigV1 config) {
        config.recordingControls.controlsLocation = this.hidePauseMenuControls ? RecordingControlsLocation.HIDDEN : RecordingControlsLocation.RIGHT;
        config.recordingControls.automaticallyStart = this.automaticallyStart;
        config.recordingControls.automaticallyFinish = this.automaticallyFinish;
        config.recordingControls.showRecordingToasts = this.showRecordingToasts;
        config.recordingControls.quicksave = this.quicksave;

        config.recording.markDimensionChanges = this.markDimensionChanges;
        config.recording.recordHotbar = this.recordHotbar;
        config.recording.localPlayerUpdatesPerSecond = this.localPlayerUpdatesPerSecond;
        config.recording.recordVoiceChat = this.recordVoiceChat;

        config.exporting.defaultExportFilename = this.defaultExportFilename;
        config.exporting.exportRenderDummyFrames = this.exportRenderDummyFrames[0];

        config.keyframes.defaultInterpolationType = this.defaultInterpolationType;
        config.keyframes.useRealtimeInterpolation = this.useRealtimeInterpolation;

        config.editorMovement.flightDirection = this.flightCameraDirection ? MovementDirection.CAMERA : MovementDirection.HORIZONTAL;
        config.editorMovement.flightMomentum = this.flightMomentum;
        config.editorMovement.flightLockX = this.flightLockX;
        config.editorMovement.flightLockY = this.flightLockY;
        config.editorMovement.flightLockZ = this.flightLockZ;
        config.editorMovement.flightLockYaw = this.flightLockYaw;
        config.editorMovement.flightLockPitch = this.flightLockPitch;

        config.advanced.disableIncreasedFirstPersonUpdates = this.disableIncreasedFirstPersonUpdates;
        config.advanced.disableThirdPersonCancel = this.disableThirdPersonCancel;

        config.internal.recentReplays = this.recentReplays;
        config.internal.openedWindows = this.openedWindows;
        config.internal.nextUnsupportedModLoaderWarning = this.nextUnsupportedModLoaderWarning;
        config.internal.filterUnnecessaryPackets = this.filterUnnecessaryPackets;
        config.internal.signedRenderFilter = this.signedRenderFilter;
        config.internal.viewedTipsOfTheDay = this.viewedTipsOfTheDay;
        config.internal.showTipOfTheDay = this.showTipOfTheDay;
        config.internal.nextTipOfTheDay = this.nextTipOfTheDay;
        config.internal.replaySorting = this.replaySorting;
        config.internal.sortDescending = this.sortDescending;
        config.internal.defaultOverrideFov = this.defaultOverrideFov;
        config.internal.enableOverrideFovByDefault = this.enableOverrideFovByDefault;

        config.internalExport.resolution = this.resolution;
        config.internalExport.framerate = this.framerate;
        config.internalExport.resetRng = this.resetRng;
        config.internalExport.ssaa = this.ssaa;
        config.internalExport.noGui = this.noGui;
        config.internalExport.container = this.container;
        config.internalExport.videoCodec = this.videoCodec;
        config.internalExport.selectedVideoEncoder = this.selectedVideoEncoder;
        config.internalExport.useMaximumBitrate = this.useMaximumBitrate;
        config.internalExport.recordAudio = this.recordAudio;
        config.internalExport.transparentBackground = this.transparentBackground;
        config.internalExport.audioCodec = this.audioCodec;
        config.internalExport.stereoAudio = this.stereoAudio;
        config.internalExport.defaultExportPath = this.defaultExportPath;



    }
}
