package com.moulberry.flashback.povstate;

import com.moulberry.flashback.ext.PlayerPovAuxExt;
import com.moulberry.flashback.packet.FlashbackSetPovContainer;
import com.moulberry.flashback.packet.FlashbackSetPovMouseOffset;
import com.moulberry.flashback.packet.FlashbackSetPovScreen;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.NautilusInventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PovPlayerState {

    public PovScreenType screen;
    public float mouseX;
    public float mouseY;

    public PovContainerMenuType menuType;
    public List<ItemStack> items;
    public ItemStack carried;
    public IntList data;

    public int selectedSlot;
    public float experienceProgress;
    public int totalExperience;
    public int experienceLevel;
    public float health;
    public int foodLevel;
    public float saturationLevel;

    public PovPlayerState() {
        this.screen = null;
        this.menuType = new PovContainerMenuType.Inventory();
        this.items = new ArrayList<>();
        this.carried = ItemStack.EMPTY;
        this.data = new IntArrayList();
    }

    public PovPlayerState(PovScreenType screen, float mouseX, float mouseY, PovContainerMenuType menuType, List<ItemStack> items, ItemStack carried, IntList data, int selectedSlot, float experienceProgress, int totalExperience, int experienceLevel, float health, int foodLevel, float saturationLevel) {
        this.screen = screen;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.menuType = menuType;
        this.items = items;
        this.carried = carried;
        this.data = data;
        this.selectedSlot = selectedSlot;
        this.experienceProgress = experienceProgress;
        this.totalExperience = totalExperience;
        this.experienceLevel = experienceLevel;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturationLevel = saturationLevel;
    }

    public PovPlayerState copy() {
        PovScreenType screen = this.screen; // not mutated
        float mouseX = this.mouseX;
        float mouseY = this.mouseY;
        PovContainerMenuType menuType = this.menuType;  // not mutated
        List<ItemStack> items = copyItemStackList(this.items); // copied
        ItemStack carried = this.carried;  // not mutated
        IntList data = new IntArrayList(this.data); // copied
        int selectedSlot = this.selectedSlot;
        float experienceProgress = this.experienceProgress;
        int totalExperience = this.totalExperience;
        int experienceLevel = this.experienceLevel;
        float health = this.health;
        int foodLevel = this.foodLevel;
        float saturationLevel = this.saturationLevel;
        return new PovPlayerState(screen, mouseX, mouseY, menuType, items, carried, data, selectedSlot, experienceProgress, totalExperience, experienceLevel, health, foodLevel, saturationLevel);
    }

    private static List<ItemStack> copyItemStackList(List<ItemStack> original) {
        List<ItemStack> items = new ArrayList<>(original.size());
        for (ItemStack item : original) {
            items.add(item.copy());
        }
        return items;
    }

    public static PovPlayerState fromPlayer(Player player) {
        PlayerPovAuxiliaryData aux = ((PlayerPovAuxExt)player).flashback$getPlayerPovAuxiliaryData();

        PovContainerMenuType type;

        var menu = player.containerMenu;
        if (menu instanceof InventoryMenu) {
            type = new PovContainerMenuType.Inventory();
        } else if (menu instanceof AbstractMountInventoryMenu mountInventoryMenu) {
            int columns = mountInventoryMenu.mountContainer.getContainerSize() / AbstractMountInventoryMenu.INVENTORY_ROWS;
            int entityId = mountInventoryMenu.mount.getId();
            type = new PovContainerMenuType.Mount(mountInventoryMenu.containerId, columns, entityId);
        } else {
            try {
                MenuType<?> menuType = menu.getType();
                type = new PovContainerMenuType.Menu(menu.containerId, BuiltInRegistries.MENU.getKey(menuType));
            } catch (UnsupportedOperationException unsupported) {
                menu = player.inventoryMenu;
                type = new PovContainerMenuType.Inventory();
            }
        }

        List<ItemStack> items = copyItemStackList(menu.getItems());

        ItemStack carried = menu.getCarried().copy();
        IntList data = new IntArrayList(menu.dataSlots.size());
        for (DataSlot dataSlot : menu.dataSlots) {
            data.add(dataSlot.get());
        }

        int selectedSlot = player.getInventory().getSelectedSlot();
        float experienceProgress = player.experienceProgress;
        int totalExperience = player.totalExperience;
        int experienceLevel = player.experienceLevel;
        float health = player.getHealth();
        int foodLevel = player.getFoodData().getFoodLevel();
        float saturationLevel = player.getFoodData().getSaturationLevel();

        return new PovPlayerState(aux.screen, aux.mouseX, aux.mouseY, type, items, carried, data, selectedSlot, experienceProgress, totalExperience, experienceLevel, health, foodLevel, saturationLevel);
    }

    public static void addChangesBetween(List<Packet<? super ClientGamePacketListener>> packets, PovPlayerState previous, PovPlayerState next) {
        if (previous == next || next == null) {
            return;
        }

        if (previous == null) {
            next.addFullState(packets);
            return;
        }

        addMenuChangesBetween(packets, previous, next);

        boolean screenChanged = !Objects.equals(previous.screen, next.screen);
        if (screenChanged) {
            packets.add(ServerPlayNetworking.createClientboundPacket(new FlashbackSetPovScreen(next.screen)));
        }

        if (next.screen != null) {
            if (screenChanged || previous.mouseX != next.mouseX || previous.mouseY != next.mouseY) {
                packets.add(ServerPlayNetworking.createClientboundPacket(new FlashbackSetPovMouseOffset(next.mouseX, next.mouseY)));
            }
        }

        if (previous.selectedSlot != next.selectedSlot) {
            packets.add(new ClientboundSetHeldSlotPacket(next.selectedSlot));
        }

        if (previous.experienceProgress != next.experienceProgress
            || previous.totalExperience != next.totalExperience
            || previous.experienceLevel != next.experienceLevel
        ) {
            packets.add(new ClientboundSetExperiencePacket(next.experienceProgress, next.totalExperience, next.experienceLevel));
        }

        if (previous.health != next.health
            || previous.foodLevel != next.foodLevel
            || previous.saturationLevel != next.saturationLevel
        ) {
            packets.add(new ClientboundSetHealthPacket(next.health, next.foodLevel, next.saturationLevel));
        }
    }

    private static void addMenuChangesBetween(List<Packet<? super ClientGamePacketListener>> packets, PovPlayerState previous, PovPlayerState next) {
        if (!Objects.equals(previous.menuType, next.menuType)
            || previous.items.size() != next.items.size()
            || previous.data.size() != next.data.size()
        ) {
            next.addMenuFullState(packets);
            return;
        }

        int containerId = next.menuType.containerId();

        int slots = next.items.size();
        for (int index = 0; index < slots; index++) {
            ItemStack previousSlot = previous.items.get(index);
            ItemStack newSlot = next.items.get(index);

            if (!ItemStack.matches(previousSlot, newSlot)) {
                packets.add(new ClientboundContainerSetSlotPacket(containerId, 0, index, newSlot.copy()));
            }
        }

        if (!ItemStack.matches(previous.carried, next.carried)) {
            packets.add(new ClientboundSetCursorItemPacket(next.carried.copy()));
        }

        int dataSlots = next.data.size();
        for (int index = 0; index < dataSlots; index++) {
            int previousValue = previous.data.getInt(index);
            int newValue = next.data.getInt(index);

            if (previousValue != newValue) {
                packets.add(new ClientboundContainerSetDataPacket(containerId, index, newValue));
            }
        }
    }

    private void addFullState(List<Packet<? super ClientGamePacketListener>> packets) {
        this.addMenuFullState(packets);
        packets.add(ServerPlayNetworking.createClientboundPacket(new FlashbackSetPovScreen(this.screen)));
        if (this.screen != null) {
            packets.add(ServerPlayNetworking.createClientboundPacket(new FlashbackSetPovMouseOffset(this.mouseX, this.mouseY)));
        }
        packets.add(new ClientboundSetHeldSlotPacket(this.selectedSlot));
        packets.add(new ClientboundSetExperiencePacket(this.experienceProgress, this.totalExperience, this.experienceLevel));
        packets.add(new ClientboundSetHealthPacket(this.health, this.foodLevel, this.saturationLevel));
    }

    private void addMenuFullState(List<Packet<? super ClientGamePacketListener>> packets) {
        packets.add(ServerPlayNetworking.createClientboundPacket(new FlashbackSetPovContainer(this.menuType)));

        int containerId = this.menuType.containerId();

        List<ItemStack> items = new ArrayList<>(this.items.size());
        for (ItemStack item : this.items) {
            items.add(item.copy());
        }
        packets.add(new ClientboundContainerSetContentPacket(containerId, 0, items, this.carried.copy()));

        int index = 0;
        for (int value : this.data) {
            packets.add(new ClientboundContainerSetDataPacket(containerId, index++, value));
        }
    }

    public void applyChanges(List<Packet<? super ClientGamePacketListener>> packets) {
        for (Packet<? super ClientGamePacketListener> packet : packets) {
            if (packet instanceof ClientboundCustomPayloadPacket custom) {
                if (custom.payload() instanceof FlashbackSetPovScreen setPovScreen) {
                    this.screen = setPovScreen.screen();
                } else if (custom.payload() instanceof FlashbackSetPovContainer setPovContainer) {
                    this.menuType = setPovContainer.menuType();
                    this.carried = ItemStack.EMPTY;
                    this.items.clear();
                    this.data.clear();
                } else if (custom.payload() instanceof FlashbackSetPovMouseOffset setPovMouseOffset) {
                    this.mouseX = setPovMouseOffset.mouseX();
                    this.mouseY = setPovMouseOffset.mouseY();
                }
            } else if (packet instanceof ClientboundSetHeldSlotPacket heldSlot) {
                this.selectedSlot = heldSlot.slot();
            } else if (packet instanceof ClientboundSetExperiencePacket setExperience) {
                this.experienceProgress = setExperience.getExperienceProgress();
                this.totalExperience = setExperience.getTotalExperience();
                this.experienceLevel = setExperience.getExperienceLevel();
            } else if (packet instanceof ClientboundSetHealthPacket setHealth) {
                this.health = setHealth.getHealth();
                this.foodLevel = setHealth.getFood();
                this.saturationLevel = setHealth.getSaturation();
            } else if (packet instanceof ClientboundContainerSetContentPacket containerSetContent) {
                if (containerSetContent.containerId() == this.menuType.containerId()) {
                    this.items = containerSetContent.items();
                    this.carried = containerSetContent.carriedItem();
                }
            } else if (packet instanceof ClientboundContainerSetDataPacket containerSetData) {
                if (containerSetData.getContainerId() == this.menuType.containerId()) {
                    while (this.data.size() <= containerSetData.getId()) {
                        this.data.add(0);
                    }
                    this.data.set(containerSetData.getId(), containerSetData.getValue());
                }
            } else if (packet instanceof ClientboundContainerSetSlotPacket containerSetSlot) {
                if (containerSetSlot.getContainerId() == this.menuType.containerId()) {
                    while (this.items.size() <= containerSetSlot.getSlot()) {
                        this.items.add(ItemStack.EMPTY);
                    }
                    this.items.set(containerSetSlot.getSlot(), containerSetSlot.getItem());
                }
            } else if (packet instanceof ClientboundSetCursorItemPacket setCursorItem) {
                this.carried = setCursorItem.contents();
            }
        }
    }

    private static boolean doesContainerMatchMenuType(AbstractContainerMenu menu, MenuType<?> menuType) {
        try {
            if (menu instanceof InventoryMenu || menu instanceof HorseInventoryMenu || menu instanceof NautilusInventoryMenu) {
                return false;
            }

            MenuType<?> currentType = menu.getType();
            return currentType == menuType;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    public void applyToPlayer(Player player) {
        PlayerPovAuxiliaryData aux = ((PlayerPovAuxExt)player).flashback$getPlayerPovAuxiliaryData();
        aux.screen = this.screen;
        aux.mouseX = this.mouseX;
        aux.mouseY = this.mouseY;

        AbstractContainerMenu previousMenu = player.containerMenu;
        aux.failedToCreateMenu = !tryApplyMenuToPlayer(player);
        if (aux.failedToCreateMenu) {
            player.containerMenu = player.inventoryMenu;
        }
        boolean menuChanged = previousMenu != player.containerMenu;

        if (player.getInventory().getSelectedSlot() != this.selectedSlot) {
            player.getInventory().setSelectedSlot(this.selectedSlot);
        }

        // Update items
        if (player.containerMenu.containerId == this.menuType.containerId()) {
            int index = 0;
            for (Slot slot : player.containerMenu.slots) {
                ItemStack slotStack = slot.getItem();
                ItemStack newStack = ItemStack.EMPTY;
                if (index < this.items.size()) {
                    newStack = this.items.get(index);
                }
                if (!ItemStack.matches(slotStack, newStack)) {
                    ItemStack copyStack = newStack.copy();
                    if (!menuChanged
                        && player.containerMenu == player.inventoryMenu
                        && InventoryMenu.isHotbarSlot(slot.index)
                        && (slotStack.isEmpty() || slotStack.count() < newStack.count())
                    ) {
                        copyStack.setPopTime(5);
                    }
                    slot.set(copyStack);
                }
                index += 1;
            }

            // Update data
            index = 0;
            for (DataSlot slot : player.containerMenu.dataSlots) {
                int previousValue = slot.get();
                int newValue = 0;
                if (index < this.data.size()) {
                    newValue = this.data.getInt(index);
                }
                if (previousValue != newValue) {
                    slot.set(newValue);
                }
                index += 1;
            }

            // Update carried
            if (!ItemStack.matches(player.containerMenu.getCarried(), this.carried)) {
                player.containerMenu.setCarried(this.carried.copy());
            }
        }
    }

    private boolean tryApplyMenuToPlayer(Player player) {
        switch (this.menuType) {
            case PovContainerMenuType.Inventory inventory -> {
                player.containerMenu = player.inventoryMenu;
                return true;
            }
            case PovContainerMenuType.Menu menu -> {
                MenuType<?> menuType = BuiltInRegistries.MENU.getValue(menu.menuType());
                if (menuType == null) {
                    return false;
                }
                if (player.containerMenu.containerId == menu.containerId() && doesContainerMatchMenuType(player.containerMenu, menuType)) {
                    return true;
                } else {
                    try {
                        player.containerMenu = menuType.create(menu.containerId(), player.getInventory());
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }
                }
            }
            case PovContainerMenuType.Mount mount -> {
                int inventoryColumns = mount.inventoryColumns();
                int size = AbstractMountInventoryMenu.getInventorySize(inventoryColumns);

                Entity entity = player.level().getEntity(mount.entityId());

                if (player.containerMenu.containerId == mount.containerId()
                    && player.containerMenu instanceof AbstractMountInventoryMenu menu
                    && menu.mount == entity
                    && menu.mountContainer.getContainerSize() == size
                ) {
                    return true;
                }

                SimpleContainer container = new SimpleContainer(size);

                if (entity instanceof AbstractHorse horse) {
                    player.containerMenu = new HorseInventoryMenu(mount.containerId(), player.getInventory(), container, horse, inventoryColumns);
                    return true;
                } else if (entity instanceof AbstractNautilus nautilus) {
                    player.containerMenu = new NautilusInventoryMenu(mount.containerId(), player.getInventory(), container, nautilus, inventoryColumns);
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

}
