# PolarSMP 👑

[![Build Status](https://img.shields.io/badge/Build-Success-green.svg)](https://github.com/ANSH9BOSS/PolarSMP)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/downloads/)
[![Platform](https://img.shields.io/badge/Platform-Paper%201.21%2B-blue.svg)](https://papermc.io)
[![Author](https://img.shields.io/badge/Developer-ANSH9BOSS-gold.svg)](https://github.com/ANSH9BOSS)

PolarSMP is an ultra-premium, production-ready competitive PvP rank and economy plugin for Minecraft SMP servers. Built with **Java 21**, **Maven**, and **Paper 1.21+ API**, it is designed to deliver a highly optimized, commercial-grade experience out of the box.

---

## 🌟 Key Features

*   **🏆 PolarRank System:** 10 exclusive ranked slots (Rank #1 - #10) and an `Unranked` status. Ranks transfer atomically between players upon PvP deaths.
*   **⚔ Perks & Attributes:** Dynamically applies custom potion effects and maximum health attribute modifiers (e.g. up to 15 Hearts/30 HP for Rank 1) tagged to prevent vanilla overlap.
*   **🪙 PolarBounty Economy:** Integrated economy supporting passive coin accrual, PvP kill claims, kill streak tracking, and customizable milestone bonuses.
*   **🏃 Combat Logging Punishments:** Prevents players from fleeing combat by automatically transferring ranks, bounties, and processing deaths upon sudden logout.
*   **📋 Anti-Farm Protection:** Prevents kill-farming exploitation with configurable cooldown trackers and asynchronous database queries.
*   **💻 Robust Database Support:** Utilizes HikariCP connection pooling to support both SQLite and MySQL, executing all database operations asynchronously.
*   **📦 Integrated Resource Pack Auto-Delivery:** Pre-configured automatic delivery of the custom item models (Custom model data `1001` - `1019` for coins, crowns, keys, and badges) on join.
*   **🎨 Premium Scoreboards & GUIs:** Fluid, custom 54-slot menus and scoreboard animations styled entirely with Adventure API and MiniMessage.

---

## 🛠 Command Reference

### Player Commands (`polarrank.player`, `polarbounty.player`)
*   `/polarrank top` - Open the rank leaderboard menu.
*   `/polarrank check [player]` - View current rank of a player.
*   `/polarbounty profile [player]` - Open yours or another player's profile stats menu.
*   `/polarbounty balance [player]` - View current coins and active bounty.
*   `/polarbounty rewards` - Open the milestone achievements rewards menu.
*   `/polarbounty shop` - Open the equipment and items shop.
*   `/polarbounty top` - Open the bounty leaderboards.
*   `/polarsmp` - Open the plugin info card.

### Admin Commands (`polarrank.admin`, `polarbounty.admin`)
*   `/polarrank start` - Start a new rank season.
*   `/polarrank stop` - Stop the current season.
*   `/polarrank set <player> <rank>` - Set a player's rank manually (displaces current holder).
*   `/polarrank clear <player>` - Reset a player to unranked.
*   `/polarrank info` - Show current season stats and holder mappings.
*   `/polarbounty give <player> <amount>` - Deposit coins into a player's account.
*   `/polarbounty take <player> <amount>` - Deduct coins from a player's account.
*   `/polarbounty setbounty <player> <amount>` - Set a player's active bounty.
*   `/polarbounty resetstats <player>` - Wipe all stats for a player (requires double-execution verification).
*   `/polarsmp update` - Automatically download updates from GitHub and restart the server.

---

## 📁 Resource Pack Configurations
The default `config.yml` is integrated with the official resource pack release asset:
```yaml
resource-pack:
  enabled: true
  url: "https://github.com/ANSH9BOSS/PolarSMP/releases/download/v1.0.0/resourcepack.zip"
  hash: "ca60c0d5464034d06828a33ffbcdf6fd15ba341e"
  loading-message: "<gradient:#FFD700:#FFA500>Loading PolarSMP resource pack...</gradient>"
```

---

## 🏗 Developer Credit
Created by **ANSH9BOSS** for high-tier competitive Minecraft networks.
