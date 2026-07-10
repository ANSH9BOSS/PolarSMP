package com.polarsmp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating ItemStacks with MiniMessage names and lore.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    /**
     * Creates a new ItemBuilder for the given material.
     *
     * @param material the material to build
     */
    public ItemBuilder(final Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    /**
     * Creates a new ItemBuilder from an existing ItemStack clone.
     *
     * @param base the base ItemStack to clone
     */
    public ItemBuilder(final ItemStack base) {
        this.item = base.clone();
        this.meta = item.getItemMeta();
    }

    /**
     * Sets the display name using an Adventure Component.
     *
     * @param name the display name Component
     * @return this builder
     */
    public ItemBuilder name(final Component name) {
        if (meta != null) meta.displayName(name);
        return this;
    }

    /**
     * Sets the display name using a MiniMessage string.
     *
     * @param miniMessage the MiniMessage string
     * @return this builder
     */
    public ItemBuilder name(final String miniMessage) {
        if (meta != null) meta.displayName(MiniMessage.miniMessage().deserialize(miniMessage));
        return this;
    }

    /**
     * Sets the lore from a list of Components.
     *
     * @param lore the lore lines
     * @return this builder
     */
    public ItemBuilder lore(final List<Component> lore) {
        if (meta != null) meta.lore(lore);
        return this;
    }

    /**
     * Adds a single lore line as a Component.
     *
     * @param line the lore line to add
     * @return this builder
     */
    public ItemBuilder addLoreLine(final Component line) {
        if (meta != null) {
            List<Component> existing = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            existing.add(line);
            meta.lore(existing);
        }
        return this;
    }

    /**
     * Sets lore from a list of MiniMessage strings.
     *
     * @param lines the MiniMessage lore lines
     * @return this builder
     */
    public ItemBuilder loreFromMiniMessage(final List<String> lines) {
        MiniMessage mm = MiniMessage.miniMessage();
        List<Component> components = lines.stream().map(mm::deserialize).toList();
        return lore(components);
    }

    /**
     * Sets the stack amount.
     *
     * @param amount the stack size
     * @return this builder
     */
    public ItemBuilder amount(final int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    /**
     * Adds an unsafe enchantment to the item.
     *
     * @param enchantment the enchantment to add
     * @param level       the level
     * @return this builder
     */
    public ItemBuilder enchant(final Enchantment enchantment, final int level) {
        if (meta != null) meta.addEnchant(enchantment, level, true);
        return this;
    }

    /**
     * Adds an enchantment glow effect (hidden enchant trick).
     *
     * @param glow whether to add the glow
     * @return this builder
     */
    public ItemBuilder glowing(final boolean glow) {
        if (glow && meta != null) {
            meta.addEnchant(Enchantment.INFINITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /**
     * Sets custom model data for resource pack support.
     *
     * @param data the custom model data value
     * @return this builder
     */
    public ItemBuilder customModelData(final int data) {
        if (meta != null && data > 0) meta.setCustomModelData(data);
        return this;
    }

    /**
     * Sets the skull owner for PLAYER_HEAD items.
     *
     * @param player the OfflinePlayer whose head to display
     * @return this builder
     */
    public ItemBuilder skull(final OfflinePlayer player) {
        if (meta instanceof SkullMeta skullMeta && player != null) {
            skullMeta.setOwningPlayer(player);
        }
        return this;
    }

    /**
     * Hides all item flags (enchants, attributes, etc.).
     *
     * @return this builder
     */
    public ItemBuilder hideFlags() {
        if (meta != null) meta.addItemFlags(ItemFlag.values());
        return this;
    }

    /**
     * Builds and returns the final ItemStack.
     *
     * @return the constructed ItemStack
     */
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Static factory for quick named items.
     *
     * @param material    the material
     * @param miniMessage the MiniMessage display name
     * @return the built ItemStack
     */
    public static ItemStack of(final Material material, final String miniMessage) {
        return new ItemBuilder(material).name(miniMessage).build();
    }
}
