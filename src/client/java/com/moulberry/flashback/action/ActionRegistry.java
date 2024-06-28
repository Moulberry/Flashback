package com.moulberry.flashback.action;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ActionRegistry {

    private static final List<Action> actions = new ArrayList<>();
    private static final Map<ResourceLocation, Action> resourceToAction = new HashMap<>();
    private static final Set<Class<? extends Action>> registeredActions = new HashSet<>();

    public static void register(Action action) {
        if (!registeredActions.add(action.getClass())) {
            throw new IllegalArgumentException("Action already registered: " + action.getClass());
        }
        if (resourceToAction.containsKey(action.name())) {
            throw new IllegalArgumentException("Action already registered by name: " + action.name());
        }

        actions.add(action);
        resourceToAction.put(action.name(), action);
    }

    public static List<Action> getActions() {
        return Collections.unmodifiableList(actions);
    }

    public static @Nullable Action getAction(ResourceLocation actionName) {
        return resourceToAction.get(actionName);
    }

}
