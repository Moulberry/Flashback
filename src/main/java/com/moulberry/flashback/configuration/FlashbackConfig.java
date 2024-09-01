package com.moulberry.flashback.configuration;

import com.mojang.serialization.Codec;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.SneakyThrow;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class FlashbackConfig {

    @OptionCaption("flashback.option.automatically_start")
    @OptionDescription("flashback.option.automatically_start.description")
    public boolean automaticallyStart = false;

    @OptionCaption("flashback.option.automatically_finish")
    @OptionDescription("flashback.option.automatically_finish.description")
    public boolean automaticallyFinish = true;

    @OptionCaption("flashback.option.show_recording_toasts")
    @OptionDescription("flashback.option.show_recording_toasts.description")
    public boolean showRecordingToasts = true;

    @OptionCaption("flashback.option.quicksave")
    @OptionDescription("flashback.option.quicksave.description")
    public boolean quicksave = false;

    @OptionCaption("flashback.option.hide_pause_menu_controls")
    @OptionDescription("flashback.option.hide_pause_menu_controls.description")
    public boolean hidePauseMenuControls = false;

    @OptionCaption("flashback.option.mark_dimension_changes")
    @OptionDescription("flashback.option.mark_dimension_changes.description")
    public boolean markDimensionChanges = true;

    @OptionCaption("flashback.option.record_hotbar")
    @OptionDescription("flashback.option.record_hotbar.description")
    public boolean recordHotbar = false;

    @OptionCaption("flashback.option.local_player_updates_per_second")
    @OptionDescription("flashback.option.local_player_updates_per_second.description")
    @IntOptionRange(min = 20, max = 120, step = 20)
    public int localPlayerUpdatesPerSecond = 20;

    public Set<String> openedWindows = new HashSet<>();
    public long nextUnsupportedModLoaderWarning = 0;

    @SuppressWarnings("unchecked")
    public OptionInstance<?>[] createOptionInstances() {
        List<OptionInstance<?>> options = new ArrayList<>();

        for (Field field : FlashbackConfig.class.getDeclaredFields()) {
            try {
                // Ignore static & transient fields
                if ((field.getModifiers() & Modifier.STATIC) != 0 || (field.getModifiers() & Modifier.TRANSIENT) != 0) {
                    continue;
                }

                OptionCaption caption = field.getDeclaredAnnotation(OptionCaption.class);
                if (caption == null) {
                    continue;
                }

                OptionInstance.TooltipSupplier tooltipSupplier = OptionInstance.noTooltip();
                OptionDescription description = field.getDeclaredAnnotation(OptionDescription.class);
                if (description != null) {
                    MutableComponent root = Component.empty();
                    root.append(Component.translatable(caption.value()).withStyle(ChatFormatting.YELLOW));
                    root.append(Component.literal("\n"));
                    root.append(Component.translatable(description.value()));

                    tooltipSupplier = OptionInstance.cachedConstantTooltip(root);
                }

                if (field.getType() == boolean.class) {
                    options.add(OptionInstance.createBoolean(caption.value(), tooltipSupplier, field.getBoolean(this), value -> {
                        try {
                            field.set(this, value);
                        } catch (Exception e) {
                            throw SneakyThrow.sneakyThrow(e);
                        }
                    }));
                } else if (field.getType() == int.class) {
                    IntOptionRange range = field.getDeclaredAnnotation(IntOptionRange.class);
                    if (range == null) {
                        continue;
                    }

                    options.add(new OptionInstance(caption.value(), tooltipSupplier, (component, integer) -> {
                        return Component.translatable("options.generic_value", component, Component.translatable("flashback.option.per_second", integer));
                    }, new IntRangeWithStep(range.min(), range.max(), range.step()), field.getInt(this), (integer) -> {
                        try {
                            field.set(this, integer);
                        } catch (Exception e) {
                            throw SneakyThrow.sneakyThrow(e);
                        }
                    }));
                }
            } catch (Exception e) {
                Flashback.LOGGER.error("Error while trying to convert config field to OptionInstance", e);
            }
        }

        return options.toArray(new OptionInstance[0]);
    }

    public record IntRangeWithStep(int minInclusive, int maxInclusive, int step) implements OptionInstance.IntRangeBase {
        public Optional<Integer> validateValue(Integer integer) {
            int intValue = integer;

            if (intValue < minInclusive || intValue > maxInclusive) {
                return Optional.empty();
            } else if (step > 0 && intValue != maxInclusive) {
                int distance = intValue - minInclusive;
                distance = distance/step*step;
                intValue = minInclusive + distance;
            }
            return Optional.of(intValue);
        }

        @Override
        public Integer fromSliderValue(double d) {
            if (d >= 1.0) {
                return maxInclusive;
            }

            int distance = (int)((maxInclusive - minInclusive) * d);
            if (step > 0) {
                distance = distance/step*step;
            }
            return minInclusive + distance;
        }

        public Codec<Integer> codec() {
            return Codec.intRange(this.minInclusive, this.maxInclusive + 1);
        }

        @Override
        public int minInclusive() {
            return this.minInclusive;
        }

        @Override
        public int maxInclusive() {
            return this.maxInclusive;
        }

        @Override
        public boolean applyValueImmediately() {
            return true;
        }
    }

    public static FlashbackConfig tryLoadFromFolder(Path configFolder) {
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

        return new FlashbackConfig();
    }

    public void saveToDefaultFolder() {
        Path configFolder = FabricLoader.getInstance().getConfigDir().resolve("flashback");
        this.saveToFolder(configFolder);
    }

    public void saveToFolder(Path configFolder) {
        Path primary = configFolder.resolve("flashback.json");
        Path backup = configFolder.resolve(".flashback.json.backup");

        if (Files.exists(primary)) {
            try {
                Files.move(primary, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Flashback.LOGGER.error("Failed to backup config", e);
            }
        }

        this.save(primary);
    }

    private static FlashbackConfig load(Path path) throws IOException {
        String serialized = Files.readString(path);
        return FlashbackGson.PRETTY.fromJson(serialized, FlashbackConfig.class);
    }

    private void save(Path path) {
        String serialized = FlashbackGson.PRETTY.toJson(this, FlashbackConfig.class);

        try {
            Files.writeString(path, serialized, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE, StandardOpenOption.DSYNC);
        } catch (IOException e) {
            Flashback.LOGGER.error("Failed to save config", e);
        }
    }

}
