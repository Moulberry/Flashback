package com.moulberry.flashback.screen;

import com.google.common.collect.Iterators;
import com.google.common.collect.Table;
import com.moulberry.flashback.record.FlashbackMeta;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

class ResourcePackVersionScreen extends Screen {
    private final Screen parent;
    private final Table<String, String, String> overrides;
    private String url = "";
    private String recorded = "";
    private String replacement = "";

    ResourcePackVersionScreen(Screen parent, Table<String, String, String> overrides) {
        super(Component.translatable("flashback.resource_pack_versions"));
        this.parent = parent;
        this.overrides = overrides;
    }

    @Override
    protected void init() {
        GridLayout grid = new GridLayout();
        grid.defaultCellSetting().padding(3);
        GridLayout.RowHelper rows = grid.createRowHelper(2);
        rows.addChild(new StringWidget(244, 16, this.title, this.font), 2);

        EditBox address = this.field(rows, "flashback.resource_pack_versions.url", this.url, 32767);
        EditBox from = this.field(rows, "flashback.resource_pack_versions.recorded", this.recorded, 128);
        EditBox to = this.field(rows, "flashback.resource_pack_versions.replacement", this.replacement, 128);
        to.setHint(Component.translatable("flashback.resource_pack_versions.use_recorded"));

        if (!this.overrides.isEmpty()) {
            var versions = Iterators.cycle(this.overrides.cellSet());
            rows.addChild(Button.builder(Component.translatable("flashback.resource_pack_versions.load"), button -> {
                var version = versions.next();
                address.setValue(version.getRowKey());
                from.setValue(version.getColumnKey());
            }).width(244).build(), 2);
        }

        Button done = rows.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            if (this.replacement.isEmpty() || this.replacement.equals(this.recorded)) {
                this.overrides.remove(this.url, this.recorded);
            } else {
                this.overrides.put(this.url, this.recorded, this.replacement);
            }
            this.onClose();
        }).width(119).build());
        rows.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(119).build());
        Runnable validate = () -> done.active = !this.url.isEmpty() && FlashbackMeta.isSha1(this.recorded)
            && (this.replacement.isEmpty() ? this.overrides.contains(this.url, this.recorded) : FlashbackMeta.isSha1(this.replacement));
        Runnable selectVersion = () -> {
            this.url = address.getValue().strip();
            this.recorded = from.getValue().strip().toLowerCase(Locale.ROOT);
            to.setValue(this.overrides.row(this.url).getOrDefault(this.recorded, ""));
            validate.run();
        };
        address.setResponder(value -> selectVersion.run());
        from.setResponder(value -> selectVersion.run());
        to.setResponder(value -> {
            this.replacement = value.strip().toLowerCase(Locale.ROOT);
            validate.run();
        });
        validate.run();

        grid.arrangeElements();
        FrameLayout.alignInRectangle(grid, 0, 0, this.width, this.height, 0.5f, 0.5f);
        grid.visitWidgets(this::addRenderableWidget);
        this.setInitialFocus(address);
    }

    private EditBox field(GridLayout.RowHelper rows, String key, String value, int maxLength) {
        Component label = Component.translatable(key);
        rows.addChild(new BottomTextWidget(244, 10, label, this.font).alignLeft(), 2);
        EditBox box = rows.addChild(new EditBox(this.font, 0, 0, 244, 20, label), 2);
        box.setMaxLength(maxLength);
        box.setValue(value);
        return box;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
