package com.moulberry.flashback.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RegistryHelper {

    // Checks if all the given registries in one match two
    public static boolean equals(RegistryAccess one, RegistryAccess two, List<RegistryDataLoader.RegistryData<?>> registries) {
        List<Registry<?>> listOne = new ArrayList<>();
        List<Registry<?>> listTwo = new ArrayList<>();

        // Check counts first, as a fast path
        for (RegistryDataLoader.RegistryData<?> registryData : registries) {
            Optional<? extends Registry<?>> registryOne = one.lookup(registryData.key());
            Optional<? extends Registry<?>> registryTwo = two.lookup(registryData.key());

            if (registryOne.isEmpty() || registryTwo.isEmpty()) {
                if (registryOne.isEmpty() && registryTwo.isEmpty()) {
                    continue;
                } else {
                    return false;
                }
            }

            if (registryOne.get().size() != registryTwo.get().size()) {
                return false;
            }

            listOne.add(registryOne.get());
            listTwo.add(registryTwo.get());
        }

        class FrozenAccess extends RegistryAccess.ImmutableRegistryAccess implements RegistryAccess.Frozen {
            public FrozenAccess(List<? extends Registry<?>> list) {
                super(list);
            }
        }

        LayeredRegistryAccess<?> layeredOne = ClientRegistryLayer.createRegistryAccess().replaceFrom(ClientRegistryLayer.REMOTE, new FrozenAccess(listOne));
        LayeredRegistryAccess<?> layeredTwo = ClientRegistryLayer.createRegistryAccess().replaceFrom(ClientRegistryLayer.REMOTE, new FrozenAccess(listTwo));

        RegistryOps<?> dynamicOpsOne = RegistryOps.create(JsonOps.INSTANCE, layeredOne.compositeAccess());
        RegistryOps<?> dynamicOpsTwo = RegistryOps.create(JsonOps.INSTANCE, layeredTwo.compositeAccess());

        // Check if all objects match
        for (RegistryDataLoader.RegistryData<?> registryData : registries) {
            if (!equalsAssumeSameSize(layeredOne.compositeAccess(), layeredTwo.compositeAccess(), dynamicOpsOne, dynamicOpsTwo, registryData)) {
                return false;
            }
        }

        return true;
    }

    private static <T> boolean equalsAssumeSameSize(RegistryAccess one, RegistryAccess two, RegistryOps<?> dynamicOpsOne, RegistryOps<?> dynamicOpsTwo,
        RegistryDataLoader.RegistryData<T> registryData) {
        Optional<? extends Registry<T>> registryOne = one.lookup(registryData.key());
        Optional<? extends Registry<T>> registryTwo = two.lookup(registryData.key());

        if (registryOne.isEmpty() || registryTwo.isEmpty()) {
            return registryOne.isEmpty() && registryTwo.isEmpty();
        }

        Iterator<T> iteratorOne = registryOne.get().iterator();
        Iterator<T> iteratorTwo = registryTwo.get().iterator();

        while (iteratorOne.hasNext()) {
            T valueOne = iteratorOne.next();
            T valueTwo = iteratorTwo.next();

            if (!equalsUsingCodec(valueOne, valueTwo, dynamicOpsOne, dynamicOpsTwo, registryData.elementCodec())) {
                return false;
            }
        }

        return true;
    }

    private static <T> boolean equalsUsingCodec(T one, T two, RegistryOps<?> dynamicOpsOne, RegistryOps<?> dynamicOpsTwo, Codec<T> codec) {
        if (one.equals(two)) {
            return true;
        }

        var resultOne = codec.encodeStart(dynamicOpsOne, one);
        if (resultOne.isError()) {
            return false;
        }

        var resultTwo = codec.encodeStart(dynamicOpsTwo, two);
        if (resultTwo.isError()) {
            return false;
        }

        return resultOne.getOrThrow().equals(resultTwo.getOrThrow());
    }

}
