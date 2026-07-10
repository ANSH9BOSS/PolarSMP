package com.polarsmp.gui;

import com.polarsmp.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Utility class for building inventory layouts, borders, fillers, and buttons.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class GuiBuilder {

    private GuiBuilder() {}

    /**
     * Creates a new Bukkit inventory with the specified title and size.
     *
     * @param title the inventory title Component
     * @param size  the size (must be a multiple of 9)
     * @return the empty inventory
     */
    public static Inventory createInventory(final Component title, final int size) {
        return Bukkit.createInventory(null, size, title);
    }

    /**
     * Fills the border of a 54-slot inventory with the given item.
     * Border slots: 0–8, 45–53, and the sides (9, 17, 18, 26, 27, 35, 36, 44).
     *
     * @param inv  the inventory to border-fill
     * @param item the item to use for the border
     */
    public static void fillBorder(final Inventory inv, final ItemStack item) {
        if (inv == null || inv.getSize() < 54 || item == null) return;
        int size = inv.getSize();

        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, item);
            inv.setItem(size - 9 + i, item);
        }

        // Side columns
        for (int i = 1; i < (size / 9) - 1; i++) {
            inv.setItem(i * 9, item);
            inv.setItem(i * 9 + 8, item);
        }
    }

    /**
     * Fills a specific row of an inventory with an item (0-indexed).
     *
     * @param inv  the inventory
     * @param row  the row number (0–5)
     * @param item the item
     */
    public static void fillRow(final Inventory inv, final int row, final ItemStack item) {
        if (inv == null || item == null || row < 0 || row * 9 >= inv.getSize()) return;
        for (int col = 0; col < 9; col++) {
            inv.setItem(row * 9 + col, item);
        }
    }

    /**
     * Creates a border item from a material and name.
     *
     * @param mat  the material (e.g. stained glass pane)
     * @param name the MiniMessage display name Component
     * @return the bordered item
     */
    public static ItemStack createBorderItem(final Material mat, final Component name) {
        return new ItemBuilder(mat).name(name).hideFlags().build();
    }

    /**
     * Creates an empty item for filling gaps in menus.
     * Uses a gray stained glass pane with no name.
     *
     * @return the empty filler item
     */
    public static ItemStack createFillerItem() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .hideFlags()
                .build();
    }

    /**
     * Creates the default red barrier close button.
     *
     * @return the close button item
     */
    public static ItemStack createCloseButton() {
        return new ItemBuilder(Material.BARRIER)
                .name(Component.text("✖ Close").color(net.kyori.adventure.text.format.NamedTextColor.RED))
                .hideFlags()
                .build();
    }
}
