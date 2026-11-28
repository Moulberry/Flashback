package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.povstate.PovContainerMenuType;
import com.moulberry.flashback.povstate.PovScreenType;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.SkipPacketDecoderException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record FlashbackSetPovScreen(PovScreenType screen) implements CustomPacketPayload {
    public static final Type<FlashbackSetPovScreen> TYPE = new Type<>(Flashback.createIdentifier("set_pov_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackSetPovScreen> STREAM_CODEC = new SetPovScreenStreamCodec();

    private static final Identifier NONE = Flashback.createIdentifier("none");
    private static final Identifier CONTAINER = Flashback.createIdentifier("container");
    private static final Identifier CHAT = Flashback.createIdentifier("chat");
    private static final Identifier DEATH = Flashback.createIdentifier("death");

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class SetPovScreenStreamCodec implements StreamCodec<RegistryFriendlyByteBuf, FlashbackSetPovScreen> {
        @Override
        public FlashbackSetPovScreen decode(RegistryFriendlyByteBuf friendlyByteBuf) {
            Identifier identifier = friendlyByteBuf.readIdentifier();

            PovScreenType screenType;
            if (NONE.equals(identifier)) {
                screenType = null;
            } else if (CONTAINER.equals(identifier)) {
                Component title = ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.decode(friendlyByteBuf).orElse(null);
                int quickCraftSlotCount = friendlyByteBuf.readVarInt();
                IntSet quickCraftSlots = new IntArraySet(quickCraftSlotCount);
                for (int i = 0; i < quickCraftSlotCount; i++) {
                    quickCraftSlots.add(friendlyByteBuf.readVarInt());
                }
                boolean isQuickCrafting = friendlyByteBuf.readBoolean();
                int quickCraftingType = friendlyByteBuf.readVarInt();
                screenType = new PovScreenType.Container(title, quickCraftSlots, isQuickCrafting, quickCraftingType);
            } else if (CHAT.equals(identifier)) {
                screenType = new PovScreenType.Chat(friendlyByteBuf.readUtf());
            } else if (DEATH.equals(identifier)) {
                Component causeOfDeath = ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.decode(friendlyByteBuf).orElse(null);
                boolean hardcore = friendlyByteBuf.readBoolean();
                screenType = new PovScreenType.Death(causeOfDeath, hardcore);
            } else {
                throw new SkipPacketDecoderException("Unexpected screen type: " + identifier);
            }

            return new FlashbackSetPovScreen(screenType);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf friendlyByteBuf, FlashbackSetPovScreen setPovScreen) {
            if (setPovScreen.screen() == null) {
                friendlyByteBuf.writeIdentifier(NONE);
                return;
            }
            switch (setPovScreen.screen()) {
                case PovScreenType.Container container -> {
                    friendlyByteBuf.writeIdentifier(CONTAINER);
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.encode(friendlyByteBuf, Optional.ofNullable(container.title()));
                    friendlyByteBuf.writeVarInt(container.quickCraftSlots().size());
                    for (Integer slot : container.quickCraftSlots()) {
                        friendlyByteBuf.writeVarInt(slot);
                    }
                    friendlyByteBuf.writeBoolean(container.isQuickCrafting());
                    friendlyByteBuf.writeVarInt(container.quickCraftingType());
                }
                case PovScreenType.Chat chat -> {
                    friendlyByteBuf.writeIdentifier(CHAT);
                    friendlyByteBuf.writeUtf(chat.text());
                }
                case PovScreenType.Death death -> {
                    friendlyByteBuf.writeIdentifier(DEATH);
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.encode(friendlyByteBuf, Optional.ofNullable(death.causeOfDeath()));
                    friendlyByteBuf.writeBoolean(death.hardcore());
                }
            }
        }
    }

}
