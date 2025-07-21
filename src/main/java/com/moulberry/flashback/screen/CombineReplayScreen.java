package com.moulberry.flashback.screen;

import com.mojang.serialization.Lifecycle;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import com.moulberry.flashback.io.ReplayCombiner;
import com.moulberry.flashback.playback.EmptyLevelSource;
import com.moulberry.flashback.record.FlashbackMeta;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class CombineReplayScreen extends Screen {

    @Nullable
    private final Screen lastScreen;

    private String newReplayName = "Combined Replay";
    private Path firstReplay;
    private Path secondReplay;
    private Path output;

    private Button firstReplayButton;
    private Button secondReplayButton;
    private Button outputButton;

    protected CombineReplayScreen(@Nullable Screen lastScreen, @Nullable Path firstReplay, @Nullable Path secondReplay, @Nullable Path output) {
        super(Component.translatable("flashback.combine_replay"));
        this.lastScreen = lastScreen;
        this.firstReplay = firstReplay;
        this.secondReplay = secondReplay;
        this.output = output;
    }

    @Override
    protected void setInitialFocus() {
    }

    @Override
    protected void init() {
        super.init();

        GridLayout gridLayout = new GridLayout();
        gridLayout.defaultCellSetting().padding(4, 4, 4, 0);
        GridLayout.RowHelper rowHelper = gridLayout.createRowHelper(2);

        rowHelper.addChild(new StringWidget(204, 20, Component.translatable("flashback.combine_replay"), this.font), 2);

        rowHelper.addChild(new BottomTextWidget(204, 10, Component.translatable("flashback.combine_replay.new_replay_name"), this.font).alignLeft(), 2);

        EditBox replayNameEditBox = new EditBox(this.font, 0, 0, 204, 20, Component.literal(this.newReplayName));
        replayNameEditBox.setMaxLength(128);
        replayNameEditBox.setValue(this.newReplayName);
        replayNameEditBox.setResponder(string -> this.newReplayName = string);
        rowHelper.addChild(replayNameEditBox, 2);

        Path replayFolder = Flashback.getReplayFolder();

        rowHelper.addChild(new BottomTextWidget(204, 10, Component.translatable("flashback.combine_replay.source_n", Component.literal("1")), this.font).alignLeft(), 2);

        String firstReplayPath = this.firstReplay == null ? "" : this.firstReplay.toString();
        this.firstReplayButton = Button.builder(Component.literal(firstReplayPath), button -> {
            CompletableFuture<String> future = AsyncFileDialogs.openFileDialog(replayFolder.toString(),
                "Replay Archive", "zip");
            future.thenAccept(pathStr -> {
                if (pathStr != null) {
                    this.firstReplay = Path.of(pathStr);
                    this.firstReplayButton.setMessage(Component.literal(this.firstReplay.toString()));
                }
            });
        }).width(204).build();
        rowHelper.addChild(this.firstReplayButton, 2);

        rowHelper.addChild(new BottomTextWidget(204, 10, Component.translatable("flashback.combine_replay.source_n", Component.literal("2")), this.font).alignLeft(), 2);

        String secondReplayPath = this.secondReplay == null ? "" : this.secondReplay.toString();
        this.secondReplayButton = Button.builder(Component.literal(secondReplayPath), button -> {
            CompletableFuture<String> future = AsyncFileDialogs.openFileDialog(replayFolder.toString(),
                "Replay Archive", "zip");
            future.thenAccept(pathStr -> {
                if (pathStr != null) {
                    this.secondReplay = Path.of(pathStr);
                    this.secondReplayButton.setMessage(Component.literal(this.secondReplay.toString()));
                }
            });
        }).width(204).build();
        rowHelper.addChild(this.secondReplayButton, 2);

        rowHelper.addChild(new BottomTextWidget(204, 10, Component.translatable("flashback.combine_replay.output"), this.font).alignLeft(), 2);

        String outputPath = this.output == null ? "" : this.output.toString();
        this.outputButton = Button.builder(Component.literal(outputPath), button -> {
            CompletableFuture<String> future = AsyncFileDialogs.saveFileDialog(replayFolder.toString(), "combined.zip",
                "Replay Archive", "zip");
            future.thenAccept(pathStr -> {
                if (pathStr != null) {
                    this.output = Path.of(pathStr);
                    this.outputButton.setMessage(Component.literal(this.output.toString()));
                }
            });
        }).width(204).build();
        rowHelper.addChild(this.outputButton, 2);

        rowHelper.addChild(new BottomTextWidget(204, 10, Component.literal(""), this.font), 2);

        rowHelper.addChild(Button.builder(Component.translatable("flashback.combine_replay.do_combine"), button -> {
            try {
                PackRepository packRepository = ServerPacksSource.createVanillaTrustedRepository();
                packRepository.reload();

                WorldDataConfiguration worldDataConfiguration = new WorldDataConfiguration(new DataPackConfig(List.of(), List.of()), FeatureFlags.DEFAULT_FLAGS);
                LevelSettings levelSettings = new LevelSettings("Replay", GameType.SPECTATOR, false, Difficulty.NORMAL, true, new GameRules(), worldDataConfiguration);
                WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, worldDataConfiguration, false, true);
                WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.DEDICATED, 4);

                WorldStem worldStem = Util.blockUntilDone(executor -> WorldLoader.load(initConfig, dataLoadContext -> {
                    Registry<LevelStem> registry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();

                    Holder.Reference<Biome> plains = dataLoadContext.datapackWorldgen().registryOrThrow(Registries.BIOME).getHolder(Biomes.PLAINS).get();
                    Holder.Reference<DimensionType> overworld = dataLoadContext.datapackWorldgen().registryOrThrow(Registries.DIMENSION_TYPE).getHolder(BuiltinDimensionTypes.OVERWORLD).get();

                    WorldDimensions worldDimensions = new WorldDimensions(Map.of(LevelStem.OVERWORLD, new LevelStem(overworld, new EmptyLevelSource(plains))));
                    WorldDimensions.Complete complete = worldDimensions.bake(registry);

                    return new WorldLoader.DataLoadOutput<>(new PrimaryLevelData(levelSettings, new WorldOptions(0L, false, false),
                        complete.specialWorldProperty(), complete.lifecycle()), complete.dimensionsRegistryAccess());
                }, WorldStem::new, Util.backgroundExecutor(), executor)).get();

                ReplayCombiner.combine(worldStem.registries().compositeAccess(), this.newReplayName, this.firstReplay, this.secondReplay, this.output);
                Minecraft.getInstance().setScreen(new TitleScreen());

                worldStem.close();
            } catch (Exception e) {
                Flashback.LOGGER.error("Error combining replays", e);
                Minecraft.getInstance().setScreen(new AlertScreen(() -> Minecraft.getInstance().setScreen(this.lastScreen),
                    Component.translatable("flashback.combine_replay.error"), Component.literal(e.getMessage())));
            }

        }).width(98).build(), 1);
        rowHelper.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> {
            Minecraft.getInstance().setScreen(this.lastScreen);
        }).width(98).build(), 1);

        gridLayout.arrangeElements();
        FrameLayout.alignInRectangle(gridLayout, 0, 0, this.width, this.height, 0.5f, 0.5f);
        gridLayout.visitWidgets(this::addRenderableWidget);

        this.setInitialFocus(replayNameEditBox);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}
