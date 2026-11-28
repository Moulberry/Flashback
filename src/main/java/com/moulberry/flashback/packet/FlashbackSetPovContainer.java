package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.povstate.PovContainerMenuType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.SkipPacketDecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FlashbackSetPovContainer(PovContainerMenuType menuType) implements CustomPacketPayload {
    public static final Type<FlashbackSetPovContainer> TYPE = new Type<>(Flashback.createIdentifier("set_pov_container"));

    public static final StreamCodec<FriendlyByteBuf, FlashbackSetPovContainer> STREAM_CODEC = new SetPovContainerStreamCodec();

    private static final Identifier INVENTORY = Flashback.createIdentifier("inventory");
    private static final Identifier MENU = Flashback.createIdentifier("menu");
    private static final Identifier MOUNT = Flashback.createIdentifier("mount");

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class SetPovContainerStreamCodec implements StreamCodec<FriendlyByteBuf, FlashbackSetPovContainer> {
        @Override
        public FlashbackSetPovContainer decode(FriendlyByteBuf friendlyByteBuf) {
            Identifier identifier = friendlyByteBuf.readIdentifier();

            PovContainerMenuType menuType;
            if (INVENTORY.equals(identifier)) {
                menuType = new PovContainerMenuType.Inventory();
            } else if (MENU.equals(identifier)) {
                menuType = new PovContainerMenuType.Menu(friendlyByteBuf.readVarInt(), friendlyByteBuf.readIdentifier());
            } else if (MOUNT.equals(identifier)) {
                menuType = new PovContainerMenuType.Mount(friendlyByteBuf.readVarInt(), friendlyByteBuf.readVarInt(), friendlyByteBuf.readVarInt());
            } else {
                throw new SkipPacketDecoderException("Unexpected menu type: " + identifier);
            }

            return new FlashbackSetPovContainer(menuType);
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackSetPovContainer setPovContainer) {
            switch (setPovContainer.menuType()) {
                case PovContainerMenuType.Inventory inventory -> {
                    friendlyByteBuf.writeIdentifier(INVENTORY);
                }
                case PovContainerMenuType.Menu menu -> {
                    friendlyByteBuf.writeIdentifier(MENU);
                    friendlyByteBuf.writeVarInt(menu.containerId());
                    friendlyByteBuf.writeIdentifier(menu.menuType());
                }
                case PovContainerMenuType.Mount mount -> {
                    friendlyByteBuf.writeIdentifier(MOUNT);
                    friendlyByteBuf.writeVarInt(mount.containerId());
                    friendlyByteBuf.writeVarInt(mount.inventoryColumns());
                    friendlyByteBuf.writeVarInt(mount.entityId());
                }
            }
        }
    }

}
