package com.kasper.playerstatuses;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;

/**
 * Maps a Minecraft {@link AbstractContainerMenu} to its {@link Activity}, shared by the client screen
 * detector and (for vanilla clients) the server-side open-container detector.
 */
public final class MenuActivities {

    private MenuActivities() {
    }

    /**
     * @param menu the open container menu
     * @param includePlayerInventory true on the client (where {@link InventoryScreen} is an open screen),
     *                               false on the server (where {@link InventoryMenu} is the default menu even when closed)
     */
    public static Activity of(AbstractContainerMenu menu, boolean includePlayerInventory) {
        if (menu == null) {
            return Activity.NONE;
        }
        if (menu instanceof InventoryMenu) {
            return includePlayerInventory ? Activity.INVENTORY : Activity.NONE;
        }
        if (menu instanceof CraftingMenu
                || menu instanceof StonecutterMenu
                || menu instanceof LoomMenu
                || menu instanceof CartographyTableMenu
                || menu instanceof SmithingMenu) {
            return Activity.CRAFTING;
        }
        if (menu instanceof ChestMenu
                || menu instanceof ShulkerBoxMenu
                || menu instanceof HopperMenu
                || menu instanceof DispenserMenu
                || menu instanceof HorseInventoryMenu) {
            return Activity.CONTAINER;
        }
        if (menu instanceof AbstractFurnaceMenu
                || menu instanceof BrewingStandMenu) {
            return Activity.FURNACE;
        }
        if (menu instanceof EnchantmentMenu
                || menu instanceof BeaconMenu) {
            return Activity.ENCHANTING;
        }
        if (menu instanceof AnvilMenu
                || menu instanceof GrindstoneMenu) {
            return Activity.ANVIL;
        }
        if (menu instanceof MerchantMenu) {
            return Activity.TRADING;
        }
        if (menu instanceof LecternMenu) {
            return Activity.BOOK;
        }
        String name = menu.getClass().getName();
        if (name.equals("net.minecraft.world.inventory.CrafterMenu")) {
            return Activity.CRAFTING;
        }
        if (name.startsWith("net.minecraft.")) {
            return Activity.CONTAINER;
        }
        return Activity.BUSY;
    }
}
