package com.moulberry.flashback.povstate;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

public sealed interface PovScreenType permits PovScreenType.Container, PovScreenType.Chat, PovScreenType.Death {

    float mouseOriginX(Screen screen);
    float mouseOriginY(Screen screen);

    record Container(Component title, IntSet quickCraftSlots, boolean isQuickCrafting, int quickCraftingType) implements PovScreenType {
        public float mouseOriginX(Screen screen) {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                return containerScreen.leftPos;
            } else {
                return screen.width/2f;
            }
        }

        public float mouseOriginY(Screen screen) {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                return containerScreen.topPos;
            } else {
                return screen.height/2f;
            }
        }
    }

    record Chat(String text) implements PovScreenType {
        public float mouseOriginX(Screen screen) {
            return 0;
        }

        public float mouseOriginY(Screen screen) {
            return screen.height;
        }
    }

    record Death(Component causeOfDeath, boolean hardcore) implements PovScreenType {
        public float mouseOriginX(Screen screen) {
            return screen.width/2f;
        }

        public float mouseOriginY(Screen screen) {
            return screen.height/4f;
        }
    }

}
