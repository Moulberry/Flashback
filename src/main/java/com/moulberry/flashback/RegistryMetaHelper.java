package com.moulberry.flashback;

import com.google.common.collect.Sets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class RegistryMetaHelper {

    public static LinkedHashMap<String, LinkedHashSet<String>> calculateNamespacesForRegistries() {
        LinkedHashMap<String, LinkedHashSet<String>> namespacesForRegistries = new LinkedHashMap<>();

        for (ResourceLocation registryName : BuiltInRegistries.REGISTRY.keySet()) {
            Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(registryName);

            if (registry == null) {
                continue;
            }

            LinkedHashSet<String> namespaces = new LinkedHashSet<>();

            for (Map.Entry<? extends ResourceKey<?>, ?> entry : registry.entrySet()) {
                ResourceLocation location = entry.getKey().location();
                String namespace = location.getNamespace();
                if (!namespace.equals("minecraft") && !namespace.equals("brigadier")) {
                    namespaces.add(namespace);
                }
            }

            if (!namespaces.isEmpty()) {
                namespacesForRegistries.put(registryName.toString(), namespaces);
            }
        }

        return namespacesForRegistries;
    }

    private static final List<String> REGISTRY_ORDER = List.of(
        "minecraft:block",
        "minecraft:item",
        "minecraft:entity_type",
        "minecraft:fluid",
        "minecraft:particle_type",
        "minecraft:block_entity_type",
        "minecraft:sound_event"
    );

    public static Component createMismatchWarning(LinkedHashMap<String, LinkedHashSet<String>> current, LinkedHashMap<String, LinkedHashSet<String>> replay) {
        MutableComponent mutableComponent = Component.empty();

        mutableComponent.append(Component.translatable("flashback.registry.mismatch1").withStyle(ChatFormatting.RED)).append(FlashbackTextComponents.NEWLINE);
        mutableComponent.append(Component.translatable("flashback.registry.mismatch2").withStyle(ChatFormatting.RED)).append(FlashbackTextComponents.NEWLINE);
        mutableComponent.append(Component.translatable("flashback.registry.mismatch3", Component.translatable("selectWorld.backupJoinSkipButton")).withStyle(ChatFormatting.RED)).append(FlashbackTextComponents.NEWLINE);

        mutableComponent.append(FlashbackTextComponents.NEWLINE);

        record MismatchEntry(String registryName, LinkedHashSet<String> mismatchedElements) {}

        List<MismatchEntry> mismatches = new ArrayList<>();

        for (String registryName : current.keySet()) {
            if (!replay.containsKey(registryName)) {
                mismatches.add(new MismatchEntry(registryName, current.get(registryName)));
            } else {
                LinkedHashSet<String> currentEntries = current.get(registryName);
                LinkedHashSet<String> replayEntries = replay.get(registryName);

                LinkedHashSet<String> mismatchesForRegistry = new LinkedHashSet<>(Sets.symmetricDifference(currentEntries, replayEntries));
                if (!mismatchesForRegistry.isEmpty()) {
                    mismatches.add(new MismatchEntry(registryName, mismatchesForRegistry));
                }
            }

        }
        for (String registryName : replay.keySet()) {
            if (!current.containsKey(registryName)) {
                mismatches.add(new MismatchEntry(registryName, replay.get(registryName)));
            }
        }

        mismatches.sort(Comparator.comparingInt(entry -> {
            int index = REGISTRY_ORDER.indexOf(entry.registryName);
            if (index < 0) {
                index = REGISTRY_ORDER.size();
            }
            return index;
        }));

        final int maxRows = 15;
        int usedRows = 4;

        var font = Minecraft.getInstance().font;
        var splitter = font.getSplitter();

        Iterator<MismatchEntry> iterator = mismatches.iterator();
        while (maxRows - usedRows >= 3 && iterator.hasNext()) {
            MismatchEntry entry = iterator.next();
            mutableComponent.append(Component.literal("Registry: " + entry.registryName + "\n").withStyle(ChatFormatting.UNDERLINE));
            usedRows += 1;

            StringBuilder missingElements = new StringBuilder();

            boolean first = true;
            for (String element : entry.mismatchedElements) {
                if (first) {
                    first = false;
                } else {
                    missingElements.append(", ");
                }
                missingElements.append(element);
            }

            List<String> lines = new ArrayList<>();
            String missingElementsStr = missingElements.toString();
            splitter.splitLines(missingElementsStr, 300, Style.EMPTY, false, new StringSplitter.LinePosConsumer() {
                @Override
                public void accept(Style style, int from, int to) {
                    lines.add(missingElementsStr.substring(from, to));
                }
            });
            for (String line : lines) {
                if (maxRows - usedRows >= 2) {
                    mutableComponent.append(Component.literal(line + "\n").withStyle(ChatFormatting.YELLOW));
                    usedRows += 1;
                } else {
                    mutableComponent.append(Component.literal("...and more elements\n").withStyle(ChatFormatting.YELLOW));
                    usedRows += 1;
                }
            }
        }

        if (iterator.hasNext()) {
            int remaining = 0;
            while (iterator.hasNext()) {
                remaining += 1;
                iterator.next();
            }

            mutableComponent.append("...and " + remaining + " more registries");
            usedRows += 1;
        }

        return mutableComponent;
    }
}
