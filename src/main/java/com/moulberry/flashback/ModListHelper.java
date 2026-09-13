package com.moulberry.flashback;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModListHelper {

    public record ModVersionChange(String modId, String oldVersion, String newVersion) {}

    public record ModListChanges(List<String> removed, List<String> added, List<ModVersionChange> changes) {
        public void log() {
            for (String modId : this.removed) {
                Flashback.LOGGER.warn("Mod removed since record: {}", modId);
            }
            for (String modId : this.added) {
                Flashback.LOGGER.warn("Mod added since record: {}", modId);
            }
            for (ModVersionChange change : this.changes) {
                Flashback.LOGGER.warn("Mod version changed since record: {} ({} -> {})", change.modId, change.oldVersion, change.newVersion);
            }
        }
    }

    public static LinkedHashMap<String, String> calculateModList() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();

        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if (container.getContainingMod().isPresent()) {
                // Ignore children, tracking the parent should be sufficient
                continue;
            }

            var modMetadata = container.getMetadata();
            map.put(modMetadata.getId(), modMetadata.getVersion().getFriendlyString());
        }

        return map;
    }

    public static ModListChanges calculateChanges(LinkedHashMap<String, String> from) {
        LinkedHashMap<String, String> to = calculateModList();

        List<String> removed = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<ModVersionChange> changes = new ArrayList<>();

        for (Map.Entry<String, String> toEntry : to.entrySet()) {
            String modId = toEntry.getKey();
            String toVersion = toEntry.getValue();
            String fromVersion = from.get(modId);

            if (fromVersion == null) {
                added.add(toEntry.getKey());
            } else if (!fromVersion.equals(toVersion)) {
                changes.add(new ModVersionChange(toEntry.getKey(), fromVersion, toVersion));
            }
        }

        for (String modId : from.keySet()) {
            if (!to.containsKey(modId)) {
                removed.add(modId);
            }
        }

        return new ModListChanges(removed, added, changes);
    }

}
