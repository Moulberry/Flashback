package com.moulberry.flashback.screen;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends OptionsSubScreen {

    public ConfigScreen(Screen previous) {
        super(previous, Minecraft.getInstance().options, Component.literal("Flashback Options"));
    }

    protected void addOptions() {
        if (this.list != null) {
            this.list.addSmall(Flashback.getConfig().createOptionInstances());
        }
    }

    public void removed() {
        Flashback.getConfig().saveToDefaultFolder();
    }

}
