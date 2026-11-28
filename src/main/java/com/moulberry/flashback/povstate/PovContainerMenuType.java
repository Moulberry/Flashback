package com.moulberry.flashback.povstate;

import net.minecraft.resources.Identifier;

public sealed interface PovContainerMenuType permits PovContainerMenuType.Menu, PovContainerMenuType.Mount, PovContainerMenuType.Inventory {

    int containerId();

    record Menu(int containerId, Identifier menuType) implements PovContainerMenuType {
    }

    record Mount(int containerId, int inventoryColumns, int entityId) implements PovContainerMenuType {
    }

    record Inventory() implements PovContainerMenuType {
        @Override
        public int containerId() {
            return 0;
        }
    }

}
