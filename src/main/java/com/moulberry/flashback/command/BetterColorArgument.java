package com.moulberry.flashback.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BetterColorArgument implements ArgumentType<Integer> {

    private static final Collection<String> EXAMPLES = Arrays.asList("red", "green");
    public static final DynamicCommandExceptionType ERROR_INVALID_VALUE = new DynamicCommandExceptionType(object -> Component.translatableEscape("argument.color.invalid", object));

    private static final List<String> names = new ArrayList<>();
    private static final Map<String, ChatFormatting> nameToFormatting = new HashMap<>();
    private static final Object2IntMap<ChatFormatting> formattingToRgb = new Object2IntOpenHashMap<>();

    static {
        for (ChatFormatting value : ChatFormatting.values()) {
            var color = TextColor.fromLegacyFormat(value);
            if (color == null) {
                continue;
            }

            String name = value.name().toLowerCase(Locale.ROOT);
            names.add(name);
            nameToFormatting.put(name, value);
            formattingToRgb.put(value, color.getValue());
        }
    }

    private BetterColorArgument() {
    }

    public static BetterColorArgument color() {
        return new BetterColorArgument();
    }

    @Override
    public Integer parse(StringReader stringReader) {
        String string = stringReader.readUnquotedString();

        ChatFormatting chatFormatting = nameToFormatting.get(string);
        if (chatFormatting != null) {
            return formattingToRgb.getOrDefault(chatFormatting, 0xFFFFFF);
        }

        if (string.startsWith("#")) {
            string = string.substring(1);
        }

        return Integer.parseInt(string.trim(), 16);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        return SharedSuggestionProvider.suggest(Collections.unmodifiableList(names), suggestionsBuilder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

}
