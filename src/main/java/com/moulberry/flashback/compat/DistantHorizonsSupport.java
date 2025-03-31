package com.moulberry.flashback.compat;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.override.levelHandling.IDhApiSaveStructure;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiWorldLoadEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiWorldUnloadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.level.IDhLevel;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DistantHorizonsSupport {

    private static boolean boundReplaySaveStructure = false;

    private static class ReplayDhSaveStructure implements IDhApiSaveStructure {
        public final Map<String, File> pathOverrides = new HashMap<>();

        @Override
        public File overrideFilePath(File file, IDhApiLevelWrapper iDhApiLevelWrapper) {
            return this.pathOverrides.get(iDhApiLevelWrapper.getDimensionName());
        }
    }
    private static final ReplayDhSaveStructure REPLAY_SAVE_STRUCTURE = new ReplayDhSaveStructure();

    public static void register() {
        DhApiEventRegister.on(DhApiWorldLoadEvent.class, new DhApiWorldLoadEvent() {
            @Override
            public void onWorldLoad(DhApiEventParam<EventParam> dhApiEventParam) {
                if (Flashback.isInReplay()) {
                    Flashback.LOGGER.info("Forcing Distant Horizons to read-only because we're inside a replay");
                    DhApi.Delayed.worldProxy.setReadOnly(true);

                    Flashback.LOGGER.info("Binding IDhApiSaveStructure to REPLAY_SAVE_STRUCTURE");

                    ReplayServer replayServer = Flashback.getReplayServer();
                    REPLAY_SAVE_STRUCTURE.pathOverrides.clear();
                    for (Map.Entry<String, File> entry : replayServer.getMetadata().distantHorizonPaths.entrySet()) {
                        if (entry.getValue().exists()) {
                            REPLAY_SAVE_STRUCTURE.pathOverrides.put(entry.getKey(), entry.getValue());
                        }
                    }

                    DhApi.overrides.bind(IDhApiSaveStructure.class, REPLAY_SAVE_STRUCTURE);
                    boundReplaySaveStructure = true;
                }
            }
        });

        DhApiEventRegister.on(DhApiWorldUnloadEvent.class, new DhApiWorldUnloadEvent() {
            @Override
            public void onWorldUnload(DhApiEventParam<EventParam> dhApiEventParam) {
                if (boundReplaySaveStructure) {
                    Flashback.LOGGER.info("Unbinding IDhApiSaveStructure from REPLAY_SAVE_STRUCTURE");
                    DhApi.overrides.unbind(IDhApiSaveStructure.class, REPLAY_SAVE_STRUCTURE);
                    boundReplaySaveStructure = false;
                }
            }
        });

        DhApiEventRegister.on(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent() {
            @Override
            public void onLevelLoad(DhApiEventParam<EventParam> dhApiEventParam) {
                if (Flashback.RECORDER != null) {
                    Flashback.RECORDER.putDistantHorizonsPaths(getDimensionPaths());
                }
            }
        });
    }

    public static Map<String, File> getDimensionPaths() {
        Map<String, File> paths = new HashMap<>();

        for (IDhLevel level : SharedApi.getIDhClientWorld().getAllLoadedLevels()) {
            File file = level.getSaveStructure().getSaveFolder(level.getLevelWrapper());
            String dimensionName = level.getLevelWrapper().getDimensionName();
            paths.put(dimensionName, file);
        }

        return paths;
    }

}
