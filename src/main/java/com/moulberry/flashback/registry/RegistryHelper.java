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
    public static List<ResourceKey<? extends Registry<?>>> findChangedRegistries(RegistryAccess one, RegistryAccess two, List<RegistryDataLoader.RegistryData<?>> registries) {
        List<Registry<?>> listOne = new ArrayList<>();
        List<Registry<?>> listTwo = new ArrayList<>();

        // Check counts first, as a fast path
        for (RegistryDataLoader.RegistryData<?> registryData : registries) {
            Optional<? extends Registry<?>> registryOne = one.registry(registryData.key());
            Optional<? extends Registry<?>> registryTwo = two.registry(registryData.key());

            if (registryOne.isEmpty() || registryTwo.isEmpty()) {
                if (registryOne.isPresent()) {
                    listOne.add(registryOne.get());
                    listTwo.add(registryOne.get());
                    continue;
                } else if (registryTwo.isPresent()) {
                    listOne.add(registryTwo.get());
                    listTwo.add(registryTwo.get());
                    continue;
                } else {
                    continue;
                }
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
        var trackedLookupAdapter = new TrackedHolderLookupAdapter(layeredTwo.compositeAccess());
        RegistryOps<?> dynamicOpsTwo = RegistryOps.create(JsonOps.INSTANCE, trackedLookupAdapter);

        List<ResourceKey<? extends Registry<?>>> changedRegistries = new ArrayList<>();

        record RegistryWithDependency(ResourceKey<? extends Registry<?>> registry, List<ResourceKey<? extends Registry<?>>> dependsOn) {}
        List<RegistryWithDependency> registryWithDependencies = new ArrayList<>();

        for (RegistryDataLoader.RegistryData<?> registryData : registries) {
            if (!equalsCheckSize(layeredOne.compositeAccess(), layeredTwo.compositeAccess(), dynamicOpsOne, dynamicOpsTwo, registryData)) {
                changedRegistries.add(registryData.key());
            } else if (!trackedLookupAdapter.accessedRegistries.isEmpty()) {
                registryWithDependencies.add(new RegistryWithDependency(registryData.key(), new ArrayList<>(trackedLookupAdapter.accessedRegistries)));
            }

            trackedLookupAdapter.accessedRegistries.clear();
        }

        // Recursively add all registries which access a registry that was changed
        while (true) {
            boolean changed = registryWithDependencies.removeIf(registryWithDependency -> {
                for (ResourceKey<? extends Registry<?>> dependency : registryWithDependency.dependsOn) {
                    if (changedRegistries.contains(dependency)) {
                        changedRegistries.add(registryWithDependency.registry);
                        return true;
                    }
                }
                return false;
            });
            if (!changed) {
                break;
            }
        }

        return changedRegistries;
    }

    private static <T> boolean equalsCheckSize(RegistryAccess one, RegistryAccess two, RegistryOps<?> dynamicOpsOne, RegistryOps<?> dynamicOpsTwo,
            RegistryDataLoader.RegistryData<T> registryData) {
        Optional<? extends Registry<?>> registryOne = one.registry(registryData.key());
        Optional<? extends Registry<?>> registryTwo = two.registry(registryData.key());

        if (registryOne.isEmpty() || registryTwo.isEmpty()) {
            return registryOne.isEmpty() && registryTwo.isEmpty();
        }

        if (registryOne.get().size() != registryTwo.get().size()) {
            return false;
        }

        return equalsAssumeSameSize(one, two, dynamicOpsOne, dynamicOpsTwo, registryData);
    }

    private static <T> boolean equalsAssumeSameSize(RegistryAccess one, RegistryAccess two, RegistryOps<?> dynamicOpsOne, RegistryOps<?> dynamicOpsTwo,
            RegistryDataLoader.RegistryData<T> registryData) {
        Optional<? extends Registry<T>> registryOne = one.registry(registryData.key());
        Optional<? extends Registry<T>> registryTwo = two.registry(registryData.key());

        if (registryOne.isEmpty() || registryTwo.isEmpty()) {
            return false;
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

        var encodedOne = resultOne.getOrThrow();
        var encodedTwo = resultTwo.getOrThrow();

        return encodedOne.equals(encodedTwo);
    }

    private static final class TrackedHolderLookupAdapter implements RegistryOps.RegistryInfoLookup {
        private final HolderLookup.Provider lookupProvider;
        private final Map<ResourceKey<? extends Registry<?>>, Optional<? extends RegistryOps.RegistryInfo<?>>> lookups = new ConcurrentHashMap();

        public final Set<ResourceKey<? extends Registry<?>>> accessedRegistries = new HashSet<>();

        public TrackedHolderLookupAdapter(HolderLookup.Provider provider) {
            this.lookupProvider = provider;
        }

        @Override
        public <E> Optional<RegistryOps.RegistryInfo<E>> lookup(ResourceKey<? extends Registry<? extends E>> resourceKey) {
            this.accessedRegistries.add(resourceKey);
            return (Optional<RegistryOps.RegistryInfo<E>>) this.lookups.computeIfAbsent(resourceKey, this::createLookup);
        }

        private Optional<RegistryOps.RegistryInfo<Object>> createLookup(ResourceKey<? extends Registry<?>> resourceKey) {
            return this.lookupProvider.lookup(resourceKey).map(RegistryOps.RegistryInfo::fromRegistryLookup);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            TrackedHolderLookupAdapter that = (TrackedHolderLookupAdapter) o;
            return Objects.equals(lookupProvider, that.lookupProvider);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(lookupProvider);
        }
    }

}
