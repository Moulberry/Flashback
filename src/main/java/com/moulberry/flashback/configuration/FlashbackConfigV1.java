package com.moulberry.flashback.configuration;

import com.google.gson.JsonObject;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.combo_options.AudioCodec;
import com.moulberry.flashback.combo_options.MovementDirection;
import com.moulberry.flashback.combo_options.RecordingControlsLocation;
import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.screen.select_replay.ReplaySorting;
import com.moulberry.lattice.annotation.LatticeCategory;
import com.moulberry.lattice.annotation.LatticeFormatValues;
import com.moulberry.lattice.annotation.LatticeOption;
import com.moulberry.lattice.annotation.constraint.LatticeFloatRange;
import com.moulberry.lattice.annotation.constraint.LatticeIntRange;
import com.moulberry.lattice.annotation.constraint.LatticeShowIf;
import com.moulberry.lattice.annotation.widget.LatticeWidgetButton;
import com.moulberry.lattice.annotation.widget.LatticeWidgetDropdown;
import com.moulberry.lattice.annotation.widget.LatticeWidgetMessage;
import com.moulberry.lattice.annotation.widget.LatticeWidgetSlider;
import com.moulberry.lattice.annotation.widget.LatticeWidgetTextField;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlashbackConfigV1 {

    private static final int CURRENT_CONFIG_VERSION = 2;

    private FlashbackConfigV1() {
    }

    private int configVersion = -1;

    @LatticeCategory(name = "flashback.option.recording_controls")
    public SubcategoryRecordingControls recordingControls = new SubcategoryRecordingControls();

    public static class SubcategoryRecordingControls {
        @LatticeOption(title = "flashback.option.recording_controls.location", description = "!!.description")
        @LatticeWidgetDropdown
        public RecordingControlsLocation controlsLocation = RecordingControlsLocation.RIGHT;

        @LatticeOption(title = "flashback.option.recording_controls.automatically_start", description = "!!.description")
        @LatticeWidgetButton
        public boolean automaticallyStart = false;

        @LatticeOption(title = "flashback.option.recording_controls.automatically_finish", description = "!!.description")
        @LatticeWidgetButton
        public boolean automaticallyFinish = true;

        @LatticeOption(title = "flashback.option.recording_controls.show_recording_toasts", description = "!!.description")
        @LatticeWidgetButton
        public boolean showRecordingToasts = true;

        @LatticeOption(title = "flashback.option.recording_controls.quicksave", description = "!!.description")
        @LatticeWidgetButton
        public boolean quicksave = false;
    }

    @LatticeCategory(name = "flashback.option.recording")
    public SubcategoryRecording recording = new SubcategoryRecording();

    public static class SubcategoryRecording {
        @LatticeOption(title = "flashback.option.recording.mark_dimension_changes", description = "!!.description")
        @LatticeWidgetButton
        public boolean markDimensionChanges = true;

        @LatticeOption(title = "flashback.option.recording.record_hotbar", description = "!!.description")
        @LatticeWidgetButton
        public boolean recordHotbar = false;

        @LatticeOption(title = "flashback.option.recording.local_player_updates_per_second", description = "!!.description")
        @LatticeFormatValues(formattingString = "flashback.option.per_second", translate = true)
        @LatticeIntRange(min = 20, max = 120, step = 20, clampMin = 20, clampMax = 360, clampStep = 20)
        @LatticeWidgetSlider
        public int localPlayerUpdatesPerSecond = 20;

        @LatticeOption(title = "flashback.option.recording.record_voice_chat", description = "!!.description")
        @LatticeWidgetButton
        @LatticeShowIf(function = "hasSimpleVoiceChat")
        public boolean recordVoiceChat = false;

        public boolean hasSimpleVoiceChat() {
            return FabricLoader.getInstance().isModLoaded("voicechat");
        }
    }

    @LatticeCategory(name = "flashback.option.exporting")
    public SubcategoryExporting exporting = new SubcategoryExporting();

    public static class SubcategoryExporting {
        @LatticeOption(title = "flashback.export_filename", description = "flashback.export_filename_tooltip")
        @LatticeWidgetTextField
        public String defaultExportFilename = "%date%T%time%";

        @LatticeOption(title = "flashback.dummy_render_frames", description = "flashback.dummy_render_frames_description")
        @LatticeIntRange(min = 0, max = 60, clampMin = 0)
        @LatticeWidgetSlider
        public int exportRenderDummyFrames = 0;
    }

    @LatticeCategory(name = "flashback.option.keyframes")
    public SubcategoryKeyframes keyframes = new SubcategoryKeyframes();

    public static class SubcategoryKeyframes {
        @LatticeOption(title = "flashback.default_interpolation", description = "flashback.default_interpolation_description")
        @LatticeWidgetDropdown
        public InterpolationType defaultInterpolationType = InterpolationType.SMOOTH;

        @LatticeOption(title = "flashback.use_realtime_interpolation", description = "flashback.use_realtime_interpolation_description")
        @LatticeWidgetButton
        public boolean useRealtimeInterpolation = true;
    }

    @LatticeCategory(name = "flashback.option.editor_movement")
    public SubcategoryEditorMovement editorMovement = new SubcategoryEditorMovement();

    public static class SubcategoryEditorMovement {
        @LatticeOption(title = "flashback.movement_direction")
        @LatticeWidgetButton
        public MovementDirection flightDirection = MovementDirection.HORIZONTAL;

        @LatticeOption(title = "flashback.momentum")
        @LatticeFloatRange(min = 0.0f, max = 1.0f, clampMin = 0.0f, clampMax = 1.0f)
        @LatticeWidgetSlider
        public float flightMomentum = 1.0f;

        @LatticeOption(title = "flashback.lock_x")
        @LatticeWidgetButton
        public boolean flightLockX = false;

        @LatticeOption(title = "flashback.lock_y")
        @LatticeWidgetButton
        public boolean flightLockY = false;

        @LatticeOption(title = "flashback.lock_z")
        @LatticeWidgetButton
        public boolean flightLockZ = false;

        @LatticeOption(title = "flashback.lock_yaw")
        @LatticeWidgetButton
        public boolean flightLockYaw = false;

        @LatticeOption(title = "flashback.lock_pitch")
        @LatticeWidgetButton
        public boolean flightLockPitch = false;
    }

    @LatticeCategory(name = "flashback.advanced")
    public SubcategoryAdvanced advanced = new SubcategoryAdvanced();

    public static class SubcategoryAdvanced {
        @LatticeWidgetMessage(maxRows = 5)
        public transient Component warning = Component.translatable("flashback.advanced_description");

        @LatticeOption(title = "flashback.disable_first_person_updates")
        @LatticeWidgetButton
        public boolean disableIncreasedFirstPersonUpdates = false;

        @LatticeOption(title = "flashback.disable_third_person_cancel")
        @LatticeWidgetButton
        public boolean disableThirdPersonCancel = false;

        @LatticeOption(title = "flashback.advanced.synchronize_ticking", description = "!!.description")
        @LatticeWidgetButton
        public boolean synchronizeTicking = false;
    }

    public SubcategoryInternal internal = new SubcategoryInternal();

    public static class SubcategoryInternal {
        public List<String> recentReplays = new ArrayList<>();
        public Set<String> openedWindows = new HashSet<>();
        public long nextUnsupportedModLoaderWarning = 0;
        public boolean filterUnnecessaryPackets = true;
        public boolean signedRenderFilter = false;

        public int viewedTipsOfTheDay = 0;
        public boolean showTipOfTheDay = true;
        public long nextTipOfTheDay = 0;

        public ReplaySorting replaySorting = ReplaySorting.CREATED_DATE;
        public boolean sortDescending = true;

        public float defaultOverrideFov = 70.0f;
        public boolean enableOverrideFovByDefault = false;
    }

    public SubcategoryInternalExport internalExport = new SubcategoryInternalExport();

    public static class SubcategoryInternalExport {
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
    }

    public ForceDefaultExportSettings forceDefaultExportSettings = new ForceDefaultExportSettings();

    private transient int saveDelay = 0;

    public static FlashbackConfigV1 tryLoadFromFolder(Path configFolder) {
        Path primary = configFolder.resolve("flashback.json");
        Path backup = configFolder.resolve(".flashback.json.backup");

        if (Files.exists(primary)) {
            try {
                return load(primary);
            } catch (Exception e) {
                Flashback.LOGGER.error("Failed to load config from {}", primary, e);
            }
        }

        if (Files.exists(backup)) {
            try {
                return load(backup);
            } catch (Exception e) {
                Flashback.LOGGER.error("Failed to load config from {}", backup, e);
            }
        }

        return new FlashbackConfigV1();
    }

    public void delayedSaveToDefaultFolder() {
        if (this.saveDelay <= 0) {
            this.saveDelay = 200;
        }
    }

    public void tickDelayedSave() {
        if (this.saveDelay > 0) {
            this.saveDelay -= 1;
            if (this.saveDelay == 0) {
                this.saveToDefaultFolder();
            }
        }
    }

    public void saveToDefaultFolder() {
        Path configFolder = FabricLoader.getInstance().getConfigDir().resolve("flashback");
        this.saveToFolder(configFolder);
        this.saveDelay = 0;
    }

    public synchronized void saveToFolder(Path configFolder) {
        Path primary = configFolder.resolve("flashback.json");
        Path backup = configFolder.resolve(".flashback.json.backup");

        if (Files.exists(primary)) {
            try {
                // Ensure primary can be loaded before backing it up
                // Don't want to back up a bad config now, do we?
                load(primary);

                // Try to back up the config
                try {
                    Files.move(primary, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Flashback.LOGGER.error("Failed to backup config", e);
                }
            } catch (Exception ignored) {}
        }

        this.save(primary);
    }

    private static FlashbackConfigV1 load(Path path) throws IOException {
        String serialized = Files.readString(path);

        try {
            JsonObject jsonObject = FlashbackGson.PRETTY.fromJson(serialized, JsonObject.class);
            if (!jsonObject.has("configVersion")) {
                // Legacy config, convert
                FlashbackConfigV0 legacyConfig = FlashbackGson.PRETTY.fromJson(serialized, FlashbackConfigV0.class);

                FlashbackConfigV1 config = FlashbackGson.PRETTY.fromJson(serialized, FlashbackConfigV1.class);
                legacyConfig.migrateTo(config);
                config.configVersion = CURRENT_CONFIG_VERSION;
                return config;
            } else {
                int configVersion = jsonObject.get("configVersion").getAsInt();
                // Handle any other config migrations here
            }
        } catch (Exception ignored) {}

        FlashbackConfigV1 config = FlashbackGson.PRETTY.fromJson(serialized, FlashbackConfigV1.class);
        config.configVersion = CURRENT_CONFIG_VERSION;
        return config;
    }

    private void save(Path path) {
        String serialized = FlashbackGson.PRETTY.toJson(this, FlashbackConfigV1.class);

        try {
            Files.writeString(path, serialized, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE, StandardOpenOption.DSYNC);
        } catch (IOException e) {
            Flashback.LOGGER.error("Failed to save config", e);
        }
    }

}
