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
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class BetterColorArgument implements ArgumentType<Integer> {

    private static final Collection<String> EXAMPLES = Arrays.asList("red", "green");
    public static final DynamicCommandExceptionType ERROR_INVALID_VALUE = new DynamicCommandExceptionType(object -> Component.translatableEscape("argument.color.invalid", object));

    private BetterColorArgument() {
    }

    public static BetterColorArgument color() {
        return new BetterColorArgument();
    }

    @Override
    public Integer parse(StringReader stringReader) {
        String string = stringReader.readUnquotedString();

        ChatFormatting chatFormatting = ChatFormatting.getByName(string);
        if (chatFormatting != null && chatFormatting.getColor() != null) {
            return chatFormatting.getColor();
        }

        if (string.startsWith("#")) {
            string = string.substring(1);
        }

        return Integer.parseInt(string.trim(), 16);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        return SharedSuggestionProvider.suggest(ChatFormatting.getNames(true, false), suggestionsBuilder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

}
