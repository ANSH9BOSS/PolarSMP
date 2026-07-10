package com.polarsmp.integrations;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Optional Vault economy bridge.
 * Mirrors PolarSMP coin transactions to the server's Vault economy provider.
 * Gracefully handles the absence of Vault or an economy plugin.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class VaultHook {
    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private Economy economy;
    private boolean enabled = false;

    /**
     * Constructs a new VaultHook and attempts to link to Vault.
     *
     * @param plugin        the PolarSMP plugin instance
     * @param configManager the configuration manager
     */
    public VaultHook(final PolarSMP plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            setupEconomy();
        } else {
            plugin.getLogger().info("Vault not found - economy mirroring disabled.");
        }
    }

    private void setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().info("No Vault economy provider found.");
            return;
        }
        economy = rsp.getProvider();
        enabled = configManager.getMainConfig().getBoolean("vault.enabled", true);
        plugin.getLogger().info("Vault economy linked: " + economy.getName());
    }

    /**
     * Deposits coins into the player's Vault account.
     *
     * @param player the player
     * @param amount the amount
     */
    public void deposit(final Player player, final long amount) {
        if (!enabled || economy == null || amount <= 0) return;
        try {
            economy.depositPlayer(player, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Vault deposit failed: " + e.getMessage());
        }
    }

    /**
     * Withdraws coins from the player's Vault account.
     *
     * @param player the player
     * @param amount the amount
     */
    public void withdraw(final Player player, final long amount) {
        if (!enabled || economy == null || amount <= 0) return;
        try {
            economy.withdrawPlayer(player, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Vault withdrawal failed: " + e.getMessage());
        }
    }

    /** @return true if Vault hook is enabled and active */
    public boolean isEnabled() { return enabled; }

    /** @return the Vault Economy instance, or null if unavailable */
    public Economy getEconomy() { return economy; }
}
