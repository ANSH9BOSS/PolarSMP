package com.polarsmp.util;

import com.polarsmp.core.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Utility class for playing sounds to players.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class SoundUtil {

    private SoundUtil() {}

    /**
     * Plays a sound to a player using a SoundConfig from config.yml.
     *
     * @param player the player to play the sound to
     * @param config the SoundConfig containing sound name, volume, pitch
     */
    public static void playSound(final Player player, final ConfigManager.SoundConfig config) {
        if (config == null || player == null) return;
        try {
            Sound sound = Sound.valueOf(config.sound().toUpperCase());
            player.playSound(player.getLocation(), sound, config.volume(), config.pitch());
        } catch (IllegalArgumentException e) {
            // Unknown sound name – silently ignore
        }
    }

    /**
     * Plays a sound at a location to nearby players within a radius.
     *
     * @param location the location to play the sound at
     * @param config   the SoundConfig
     * @param radius   the range within which players will hear the sound
     */
    public static void playGlobalSound(final Location location, final ConfigManager.SoundConfig config,
                                       final double radius) {
        if (config == null || location == null || location.getWorld() == null) return;
        try {
            Sound sound = Sound.valueOf(config.sound().toUpperCase());
            location.getWorld().playSound(location, sound, (float) radius, config.pitch());
        } catch (IllegalArgumentException e) {
            // Unknown sound name – silently ignore
        }
    }

    /**
     * Plays a raw sound to a player.
     *
     * @param player the player
     * @param sound  the Sound enum value
     * @param volume the volume
     * @param pitch  the pitch
     */
    public static void play(final Player player, final Sound sound, final float volume, final float pitch) {
        if (player == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
