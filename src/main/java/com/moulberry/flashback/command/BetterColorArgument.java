package com.moulberry.flashback.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BetterColorArgument implements ArgumentType<Integer> {

    private static final Collection<String> EXAMPLES = Arrays.asList("red", "green");
    public static final DynamicCommandExceptionType ERROR_INVALID_VALUE = new DynamicCommandExceptionType(object -> Component.translatableEscape("argument.color.invalid", object));

    private static final Map<ChatFormatting, Integer> COLOR_MAP = Map.ofEntries(
        Map.entry(ChatFormatting.BLACK, 0x000000),
        Map.entry(ChatFormatting.DARK_BLUE, 0x0000AA),
        Map.entry(ChatFormatting.DARK_GREEN, 0x00AA00),
        Map.entry(ChatFormatting.DARK_AQUA, 0x00AAAA),
        Map.entry(ChatFormatting.DARK_RED, 0xAA0000),
        Map.entry(ChatFormatting.DARK_PURPLE, 0xAA00AA),
        Map.entry(ChatFormatting.GOLD, 0xFFAA00),
        Map.entry(ChatFormatting.GRAY, 0xAAAAAA),
        Map.entry(ChatFormatting.DARK_GRAY, 0x555555),
        Map.entry(ChatFormatting.BLUE, 0x5555FF),
        Map.entry(ChatFormatting.GREEN, 0x55FF55),
        Map.entry(ChatFormatting.AQUA, 0x55FFFF),
        Map.entry(ChatFormatting.RED, 0xFF5555),
        Map.entry(ChatFormatting.LIGHT_PURPLE, 0xFF55FF),
        Map.entry(ChatFormatting.YELLOW, 0xFFFF55),
        Map.entry(ChatFormatting.WHITE, 0xFFFFFF)
    );

    private static Integer getFormattingColor(ChatFormatting cf) {
        return COLOR_MAP.get(cf);
    }

    private BetterColorArgument() {
    }

    public static BetterColorArgument color() {
        return new BetterColorArgument();
    }

    @Override
    public Integer parse(StringReader stringReader) {
        String string = stringReader.readUnquotedString();

        // ChatFormatting.getName/getColor removed in 1.26.2 - use name() from Enum and static color map
        for (ChatFormatting chatFormatting : ChatFormatting.values()) {
            if (chatFormatting.name().equalsIgnoreCase(string)) {
                Integer color = getFormattingColor(chatFormatting);
                if (color != null) {
                    return color;
                }
            }
        }

        if (string.startsWith("#")) {
            string = string.substring(1);
        }

        return Integer.parseInt(string.trim(), 16);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        // ChatFormatting.getNames removed in 1.26.2 - collect manually from values
        return SharedSuggestionProvider.suggest(
            Arrays.stream(ChatFormatting.values())
                .filter(cf -> getFormattingColor(cf) != null)
                .map(cf -> cf.name().toLowerCase())
                .toList(),
            suggestionsBuilder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

}
