package com.moulberry.flashback.compat;

// Headers
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
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

// Support
public class DistantHorizonsSupport {

    private static boolean boundReplaySaveStructures = false;

    // Replay Save Structure
    private static class ReplayDhSaveStructure implements IDhApiSaveStructure {
        public final Map<String, File> pathOverrides = new HashMap<>();

        @Override
        public File overrideFilePath(File file, IDhApiLevelWrapper iDhApiLevelWrapper) {
            return this.pathOverrides.get(iDhApiLevelWrapper.getDimensionName());
        }
    }
    private static final ReplayDhSaveStructure REPLAY_SAVE_STRUCTURE = new ReplayDhSaveStructure();

    // Event Registration
    public static void register() {
        try {
            DhApiEventRegister.on(DhApiWorldLoadEvent.class, new DhApiWorldLoadEvent() {
                @Override
                public void onWorldLoad(DhApiEventParam<DhApiWorldLoadEvent.EventParam> param) {
                    if (Flashback.isInReplay()) {
                        try {
                            Flashback.LOGGER.info("Forcing Distant Horizons to read-only because we're inside a replay");
                            DhApi.Delayed.worldProxy.setReadOnly(true);

                            Flashback.LOGGER.info("Binding IDhApiSaveStructure to REPLAY_SAVE_STRUCTURE");

                            ReplayServer replayServer = Flashback.getReplayServer();
                            REPLAY_SAVE_STRUCTURE.pathOverrides.clear();
                            if (replayServer != null && replayServer.getMetadata() != null && replayServer.getMetadata().distantHorizonPaths != null) {
                                REPLAY_SAVE_STRUCTURE.pathOverrides.putAll(replayServer.getMetadata().distantHorizonPaths);
                            }

                            DhApi.overrides.bind(IDhApiSaveStructure.class, REPLAY_SAVE_STRUCTURE);
                            boundReplaySaveStructures = true;
                        } catch (Throwable t) {
                            Flashback.LOGGER.warn("Failed to bind DH save structure in replay", t);
                        }
                    }
                }
            });

            DhApiEventRegister.on(DhApiWorldUnloadEvent.class, new DhApiWorldUnloadEvent() {
                @Override
                public void onWorldUnload(DhApiEventParam<DhApiWorldUnloadEvent.EventParam> param) {
                    if (boundReplaySaveStructures) {
                        try {
                            Flashback.LOGGER.info("Unbinding IDhApiSaveStructure from REPLAY_SAVE_STRUCTURE");
                            DhApi.overrides.unbind(IDhApiSaveStructure.class, REPLAY_SAVE_STRUCTURE);
                            boundReplaySaveStructures = false;
                        } catch (Throwable t) {
                            Flashback.LOGGER.warn("Failed to unbind DH save structure", t);
                        }
                    }
                }
            });

            DhApiEventRegister.on(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent() {
                @Override
                public void onLevelLoad(DhApiEventParam<DhApiLevelLoadEvent.EventParam> param) {
                    try {
                        if (Flashback.RECORDER != null) {
                            Flashback.RECORDER.putDistantHorizonsPaths(getDimensionPaths());
                        }
                    } catch (Throwable t) {
                        Flashback.LOGGER.warn("Failed to update DH dimension paths on level load", t);
                    }
                }
            });
        } catch (Throwable t) {
            Flashback.LOGGER.warn("Failed to register DH event listeners", t);
        }
    }

    // Dimension Paths Retrieval
    public static Map<String, File> getDimensionPaths() {
        Map<String, File> paths = new HashMap<>();
        try {
            getDimensionPathsInternal(paths);
        } catch (Throwable t) {
            Flashback.LOGGER.warn("Failed to retrieve Distant Horizons dimension paths", t);
        }
        return paths;
    }

    // Internal Path Resolver
    private static void getDimensionPathsInternal(Map<String, File> paths) {
        Object clientWorld = null;
        try {
            Method m = SharedApi.class.getMethod("getIDhClientWorld");
            clientWorld = m.invoke(null);
        } catch (Throwable ignored) {}

        if (clientWorld == null) {
            try {
                Method m = SharedApi.class.getMethod("getAbstractDhWorld");
                clientWorld = m.invoke(null);
            } catch (Throwable ignored) {}
        }

        if (clientWorld == null) {
            try {
                Method m = SharedApi.class.getMethod("getDhClientServerWorld");
                clientWorld = m.invoke(null);
            } catch (Throwable ignored) {}
        }

        if (clientWorld != null) {
            try {
                Method getAllLevels = clientWorld.getClass().getMethod("getAllLoadedLevels");
                Iterable<?> levels = (Iterable<?>) getAllLevels.invoke(clientWorld);
                if (levels != null) {
                    for (Object levelObj : levels) {
                        try {
                            if (levelObj instanceof IDhLevel level) {
                                File file = level.getSaveStructure().getSaveFolder(level.getLevelWrapper());
                                String dimensionName = level.getLevelWrapper().getDimensionName();
                                paths.put(dimensionName, file);
                            } else {
                                Method getSaveStructure = levelObj.getClass().getMethod("getSaveStructure");
                                Object saveStructure = getSaveStructure.invoke(levelObj);
                                Method getLevelWrapper = levelObj.getClass().getMethod("getLevelWrapper");
                                Object levelWrapper = getLevelWrapper.invoke(levelObj);
                                Method getSaveFolder = saveStructure.getClass().getMethod("getSaveFolder", levelWrapper.getClass());
                                File file = (File) getSaveFolder.invoke(saveStructure, levelWrapper);
                                Method getDimensionName = levelWrapper.getClass().getMethod("getDimensionName");
                                String dimensionName = (String) getDimensionName.invoke(levelWrapper);
                                paths.put(dimensionName, file);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

}
