package com.polarsmp.listeners;

import com.polarsmp.PolarSMP;
import com.polarsmp.gui.GuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Handles all custom GUI menu clicks and close tracking.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class InventoryListener implements Listener {

    private final PolarSMP plugin;
    private final GuiManager guiManager;

    /**
     * Constructs a new InventoryListener.
     *
     * @param plugin     the plugin instance
     * @param guiManager the GUI manager to route events to
     */
    public InventoryListener(final PolarSMP plugin, final GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * Blocks item taking/modification in PolarSMP inventories and routes the clicks.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if player has a PolarSMP GUI open
        if (!guiManager.hasOpenGui(player.getUniqueId())) return;

        // Cancel the click event to prevent taking items
        event.setCancelled(true);

        // Process only valid click slots
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) return;

        guiManager.handleClick(player, rawSlot, event.getCurrentItem());
    }

    /**
     * Removes the player from the GUI tracking map upon closing the menu.
     */
    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            guiManager.onClose(player);
        }
    }
}
