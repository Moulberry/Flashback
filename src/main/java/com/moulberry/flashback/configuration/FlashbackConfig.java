package com.moulberry.flashback.configuration;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.SneakyThrow;
import net.minecraft.ChatFormatting;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class FlashbackConfig {

    @OptionCaption("flashback.option.automatically_start")
    @OptionDescription("flashback.option.automatically_start.description")
    public static boolean automaticallyStart = false;

    @OptionCaption("flashback.option.automatically_finish")
    @OptionDescription("flashback.option.automatically_finish.description")
    public static boolean automaticallyFinish = true;

    @OptionCaption("flashback.option.show_recording_toasts")
    @OptionDescription("flashback.option.show_recording_toasts.description")
    public static boolean showRecordingToasts = true;

    @SuppressWarnings("unchecked")
    public static OptionInstance<?>[] createOptionInstances() {
        List<OptionInstance<?>> options = new ArrayList<>();

        for (Field field : FlashbackConfig.class.getDeclaredFields()) {
            try {
                // Ignore non-static fields
                if ((field.getModifiers() & Modifier.STATIC) == 0) {
                    continue;
                }

                OptionCaption caption = field.getDeclaredAnnotation(OptionCaption.class);
                if (caption == null) {
                    continue;
                }

                OptionInstance.TooltipSupplier tooltipSupplier = OptionInstance.noTooltip();
                OptionDescription description = field.getDeclaredAnnotation(OptionDescription.class);
                if (description != null) {
                    MutableComponent root = Component.empty();
                    root.append(Component.translatable(caption.value()).withStyle(ChatFormatting.YELLOW));
                    root.append(Component.literal("\n"));
                    root.append(Component.translatable(description.value()));

                    tooltipSupplier = OptionInstance.cachedConstantTooltip(root);
                }

                if (field.getType() == boolean.class) {
                    options.add(OptionInstance.createBoolean(caption.value(), tooltipSupplier, field.getBoolean(null), value -> {
                        try {
                            field.set(null, value);
                        } catch (Exception e) {
                            throw SneakyThrow.sneakyThrow(e);
                        }
                    }));
                }
            } catch (Exception e) {
                Flashback.LOGGER.error("Error while trying to convert config field to OptionInstance", e);
            }
        }

        return options.toArray(new OptionInstance[0]);
    }

}
