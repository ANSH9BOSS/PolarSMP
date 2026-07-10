package com.polarsmp.util;

import com.polarsmp.PolarSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides text animation utilities for PolarSMP including scrolling gradients,
 * rainbow cycles, pulsing text, typewriter effects, and particle animations.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class AnimationUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private AnimationUtil() {}

    /**
     * Creates a list of scrolling gradient animation frames for the given text.
     *
     * @param text       the text to animate
     * @param hexColors  list of hex color strings (e.g. "#FFD700", "#FFA500")
     * @param frameCount the number of frames to generate
     * @return list of MiniMessage Component frames
     */
    public static List<Component> createScrollingGradient(final String text,
                                                          final List<String> hexColors,
                                                          final int frameCount) {
        List<Component> frames = new ArrayList<>();
        if (hexColors.isEmpty()) return frames;

        int colorCount = hexColors.size();
        for (int frame = 0; frame < frameCount; frame++) {
            int colorIndex = frame % colorCount;
            int nextIndex = (colorIndex + 1) % colorCount;
            String miniMsg = "<gradient:" + hexColors.get(colorIndex) + ":"
                    + hexColors.get(nextIndex) + ">" + text + "</gradient>";
            frames.add(MM.deserialize(miniMsg));
        }
        return frames;
    }

    /**
     * Creates a rainbow cycle animation cycling through the full HSB spectrum.
     *
     * @param text       the text to animate
     * @param frameCount the number of frames to generate
     * @return list of rainbow-colored Component frames
     */
    public static List<Component> createRainbowCycle(final String text, final int frameCount) {
        List<Component> frames = new ArrayList<>();
        for (int frame = 0; frame < frameCount; frame++) {
            float hue = (float) frame / frameCount;
            java.awt.Color c = java.awt.Color.getHSBColor(hue, 1.0f, 1.0f);
            String hex = String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
            float hue2 = (float) (frame + frameCount / 2) / frameCount;
            java.awt.Color c2 = java.awt.Color.getHSBColor(hue2 % 1.0f, 1.0f, 1.0f);
            String hex2 = String.format("#%02X%02X%02X", c2.getRed(), c2.getGreen(), c2.getBlue());
            frames.add(MM.deserialize("<gradient:" + hex + ":" + hex2 + "><bold>" + text + "</bold></gradient>"));
        }
        return frames;
    }

    /**
     * Creates a pulsing text effect alternating between two colors.
     *
     * @param text        the text to animate
     * @param color1      first MiniMessage color tag (e.g. "<green>")
     * @param color2      second MiniMessage color tag (e.g. "<dark_green>")
     * @param frameCount  number of frames to generate
     * @return list of alternating color Component frames
     */
    public static List<Component> createPulsingText(final String text, final String color1,
                                                    final String color2, final int frameCount) {
        List<Component> frames = new ArrayList<>();
        for (int i = 0; i < frameCount; i++) {
            String color = (i % 2 == 0) ? color1 : color2;
            frames.add(MM.deserialize(color + text + "</>"));
        }
        return frames;
    }

    /**
     * Sends a typewriter effect to a player by revealing text character by character.
     *
     * @param player      the player to send the message to
     * @param miniMessage the MiniMessage string to animate
     * @param plugin      the PolarSMP plugin for scheduling
     */
    public static void typewriterEffect(final Player player, final String miniMessage,
                                        final PolarSMP plugin) {
        // Strip tags to get raw text for typewriter
        String plain = MM.stripTags(miniMessage);
        final int[] index = {0};

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || index[0] >= plain.length()) {
                if (player.isOnline()) {
                    player.sendMessage(MM.deserialize(miniMessage)); // Final styled version
                }
                task.cancel();
                return;
            }
            String current = plain.substring(0, index[0] + 1);
            player.sendActionBar(MM.deserialize("<gray>" + current + "<dark_gray>▋</dark_gray></gray>"));
            index[0]++;
        }, 0L, 2L);
    }

    /**
     * Spawns an expanding ring of TOTEM particles around a player over 20 ticks.
     *
     * @param player the player to surround with particles
     * @param plugin the PolarSMP plugin for scheduling
     */
    public static void spawnTotemRing(final Player player, final PolarSMP plugin) {
        final int[] tick = {0};
        final int totalTicks = 20;

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || tick[0] >= totalTicks) {
                task.cancel();
                return;
            }
            double radius = 0.5 + tick[0] * 0.1;
            int particles = 16;
            for (int i = 0; i < particles; i++) {
                double angle = (2 * Math.PI * i) / particles;
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                player.getWorld().spawnParticle(
                        Particle.TOTEM_OF_UNDYING,
                        player.getLocation().add(x, 1.0, z),
                        1, 0, 0, 0, 0.1
                );
            }
            tick[0]++;
        }, 0L, 1L);
    }

    /**
     * Spawns happy villager particles at the player's location.
     *
     * @param player the player to spawn particles around
     */
    public static void spawnVillagerHappy(final Player player) {
        player.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1.5, 0),
                20, 0.5, 0.5, 0.5, 0.1
        );
    }
}
