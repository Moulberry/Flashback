package com.moulberry.flashback.mixin.language;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

@Mixin(ClientLanguage.class)
public class MixinClientLanguage {

    @Unique
    private static final Gson FLASHBACK_GSON = new Gson();

    @WrapOperation(method = "appendFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"))
    private static void appendFrom_loadFromFile(InputStream inputStream, BiConsumer<String, String> biConsumer, Operation<Void> original, @Local Resource resource) {
        if (Objects.equals(resource.sourcePackId(), "flashback")) {
            JsonObject jsonObject = FLASHBACK_GSON.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);

            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                if (entry.getValue().isJsonNull()) {
                    continue;
                }
                biConsumer.accept(entry.getKey(), GsonHelper.convertToString(entry.getValue(), entry.getKey()));
            }
        } else {
            original.call(inputStream, biConsumer);
        }
    }

}
