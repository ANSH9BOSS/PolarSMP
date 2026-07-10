package com.polarsmp.gui;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyManager;
import com.polarsmp.bounty.BountyPlayer;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.util.FormatUtil;
import com.polarsmp.util.ItemBuilder;
import com.polarsmp.util.SoundUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shop GUI for purchasing in-game equipment and consumables with coins.
 * Theme: Dark Purple and Gold.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class ShopGUI {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final BountyManager bountyManager;
    private final PlayerDataCache playerDataCache;

    /**
     * Constructs a new ShopGUI.
     */
    public ShopGUI(final PolarSMP plugin, final ConfigManager configManager,
                   final BountyManager bountyManager, final PlayerDataCache playerDataCache) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bountyManager = bountyManager;
        this.playerDataCache = playerDataCache;
    }

    /**
     * Builds and opens the Shop GUI.
     *
     * @param player the player opening the shop
     */
    public void open(final Player player) {
        String titleMM = configManager.getMessagesConfig().getString("gui.shop-title", "<dark_purple><bold>🛒 PolarSMP Shop 🛒</bold></dark_purple>");
        Inventory inv = GuiBuilder.createInventory(PolarSMP.miniMessage().deserialize(titleMM), 54);

        // Border: Purple stained glass
        ItemStack border = GuiBuilder.createBorderItem(Material.PURPLE_STAINED_GLASS_PANE, Component.empty());
        GuiBuilder.fillBorder(inv, border);

        BountyPlayer p = playerDataCache.getPlayer(player.getUniqueId());
        if (p == null) return;

        // Balance Header (Slot 4)
        int coinCMD = configManager.getMainConfig().getBoolean("resource-pack.enabled", false) ? 1001 : 0;
        ItemStack balanceItem = new ItemBuilder(Material.GOLD_NUGGET)
                .name("<gold><bold>Your Balance</bold></gold>")
                .loreFromMiniMessage(List.of(
                        "<gray>Available Coins:</gray>",
                        "<gold>🪙 " + FormatUtil.formatNumber(p.getCoins()) + " Coins</gold>"
                ))
                .customModelData(coinCMD)
                .glowing(true)
                .build();
        inv.setItem(4, balanceItem);

        // Shop items from shop.yml
        List<Map<?, ?>> itemsConfig = configManager.getShopConfig().getMapList("items");
        int index = 0;
        // Slots to place shop items (grid 9-44 excluding borders)
        int[] shopSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (Map<?, ?> itemMap : itemsConfig) {
            if (index >= shopSlots.length) break;

            String matStr = (String) itemMap.get("material");
            String nameStr = (String) itemMap.get("display-name");
            List<String> loreStr = (List<String>) itemMap.get("lore");
            long price = ((Number) itemMap.get("price")).longValue();
            Object amtVal = itemMap.get("amount");
            int amount = amtVal instanceof Number ? ((Number) amtVal).intValue() : 1;
            Object cmdVal = itemMap.get("custom-model-data");
            int cmd = cmdVal instanceof Number ? ((Number) cmdVal).intValue() : 0;

            Material mat = Material.matchMaterial(matStr);
            if (mat == null) continue;

            ItemBuilder builder = new ItemBuilder(mat).amount(amount);

            if (p.getCoins() < price) {
                // Insufficient coins styling: red strikethrough name
                builder.name("<red><st>" + PolarSMP.miniMessage().stripTags(nameStr) + "</st></red>");
                List<String> badLore = new ArrayList<>();
                for (String l : loreStr) {
                    if (l.contains("Price:")) {
                        badLore.add("<red>🪙 Price: " + price + " Coins</red>");
                    } else {
                        badLore.add(l);
                    }
                }
                badLore.add("");
                badLore.add("<red><bold>❌ INSUFFICIENT COINS</bold></red>");
                builder.loreFromMiniMessage(badLore);
            } else {
                builder.name(nameStr);
                builder.loreFromMiniMessage(loreStr);
            }

            if (cmd > 0) {
                builder.customModelData(cmd);
            }

            inv.setItem(shopSlots[index], builder.build());
            index++;
        }

        // Close Button (Slot 49)
        inv.setItem(49, GuiBuilder.createCloseButton());

        player.openInventory(inv);
    }

    /**
     * Handles slot-based shop purchasing logic.
     */
    public void handleClick(final Player player, final int slot) {
        // Map slot index back to shop item
        int[] shopSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int itemIndex = -1;
        for (int i = 0; i < shopSlots.length; i++) {
            if (shopSlots[i] == slot) {
                itemIndex = i;
                break;
            }
        }

        if (itemIndex == -1) return;

        List<Map<?, ?>> itemsConfig = configManager.getShopConfig().getMapList("items");
        if (itemIndex >= itemsConfig.size()) return;

        Map<?, ?> itemMap = itemsConfig.get(itemIndex);
        long price = ((Number) itemMap.get("price")).longValue();
        String matStr = (String) itemMap.get("material");
        Object amtVal = itemMap.get("amount");
        int amount = amtVal instanceof Number ? ((Number) amtVal).intValue() : 1;
        String nameStr = (String) itemMap.get("display-name");

        BountyPlayer p = playerDataCache.getPlayer(player.getUniqueId());
        if (p == null) return;

        if (p.getCoins() < price) {
            // Insufficient funds
            SoundUtil.playSound(player, configManager.getSound("gui-deny"));
            player.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.insufficient-coins")));
            return;
        }

        Material mat = Material.matchMaterial(matStr);
        if (mat == null) return;

        // Deduct coins and deposit items
        bountyManager.adminTakeCoins(player, price);
        ItemStack itemToGive = new ItemStack(mat, amount);
        var meta = itemToGive.getItemMeta();
        if (meta != null) {
            meta.displayName(PolarSMP.miniMessage().deserialize(nameStr));
            itemToGive.setItemMeta(meta);
        }

        // Give item or drop at feet if full
        var remaining = player.getInventory().addItem(itemToGive);
        for (ItemStack left : remaining.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }

        // Sound & ActionBar feedback
        SoundUtil.playSound(player, configManager.getSound("purchase-success"));
        player.sendActionBar(PolarSMP.miniMessage().deserialize("<green>✔ Purchased " + amount + "x " + PolarSMP.miniMessage().stripTags(nameStr) + "</green>"));

        // Update scoreboards
        plugin.getScoreboardManager().updateScoreboard(player);

        // Refresh Shop GUI state
        open(player);
    }
}
