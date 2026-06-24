package com.moulberry.flashback;

import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class IgnoredCustomPayloads {

    private static final Set<String> DEFAULT_IGNORED_NAMESPACES = Set.of(
        "fabric-screen-handler-api"
    );
    private static final Set<Identifier> DEFAULT_IGNORED_IDENTIFIERS = Set.of(
        Identifier.fromNamespaceAndPath("fabric", "recipe_sync")
    );

    private final HashSet<String> ignoredNamespaces = new HashSet<>(DEFAULT_IGNORED_NAMESPACES);
    private final HashSet<Identifier> ignoredIdentifiers = new HashSet<>(DEFAULT_IGNORED_IDENTIFIERS);
    private String lastConfigString = null;

    public void setFromConfigString(String configString) {
        if (Objects.equals(configString, this.lastConfigString)) {
            return;
        }
        this.lastConfigString = configString;

        this.ignoredNamespaces.clear();
        this.ignoredNamespaces.addAll(DEFAULT_IGNORED_NAMESPACES);
        this.ignoredIdentifiers.clear();
        this.ignoredIdentifiers.addAll(DEFAULT_IGNORED_IDENTIFIERS);

        configString = configString.trim();
        for (String line : configString.split("\n")) {
            line = line.trim();

            boolean reallow = line.startsWith("+");
            if (reallow) {
                line = line.substring(1);
            }

            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.endsWith(":*")) {
                String namespace = line.substring(0, line.length()-2);
                if (namespace.isBlank()) {
                    continue;
                }
                if (reallow) {
                    this.ignoredNamespaces.remove(namespace);
                } else {
                    this.ignoredNamespaces.add(namespace);
                }
            } else {
                try {
                    Identifier identifier = Identifier.parse(line);
                    if (reallow) {
                        this.ignoredIdentifiers.remove(identifier);
                    } else {
                        this.ignoredIdentifiers.add(identifier);
                    }
                } catch (Exception e) {
                    Flashback.LOGGER.error("Invalid identifier for ignored custom payload: {}", line);
                }
            }
        }
    }

    public boolean isIgnored(Identifier identifier) {
        return ignoredNamespaces.contains(identifier.getNamespace()) || ignoredIdentifiers.contains(identifier);
    }

}
