package com.polarsmp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Static utility class for all Adventure API / MiniMessage message delivery.
 *
 * <p>All methods are stateless and thread-safe. Never call methods that
 * interact with the Bukkit API from an async context.</p>
 *
 * <p>Text centering via {@link #sendCenteredMessage} uses a fixed-width
 * pixel-per-character approximation suitable for the default Minecraft font.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class MessageUtil {

    /** The shared MiniMessage instance used for all parsing. */
    public static final MiniMessage mm = MiniMessage.miniMessage();

    /** Default chat line width in pixels (Minecraft default chat box). */
    private static final int CHAT_WIDTH_PIXELS = 320;
    /** Average pixel width of a space character in the default font. */
    private static final int SPACE_WIDTH = 4;

    private MessageUtil() {
        throw new UnsupportedOperationException("MessageUtil is a static utility class.");
    }

    // ─── Parsing ──────────────────────────────────────────────────

    /**
     * Parses a MiniMessage string into an Adventure {@link Component}.
     *
     * @param miniMessage the raw MiniMessage-formatted string
     * @return the parsed component
     */
    public static Component parse(final String miniMessage) {
        return mm.deserialize(miniMessage);
    }

    // ─── Sending ──────────────────────────────────────────────────

    /**
     * Sends a pre-built {@link Component} to any {@link CommandSender}.
     *
     * @param sender the recipient (player, console, etc.)
     * @param msg    the component to deliver
     */
    public static void sendMessage(final CommandSender sender, final Component msg) {
        sender.sendMessage(msg);
    }

    /**
     * Sends a component as an action bar message to a player.
     *
     * @param player the target player
     * @param msg    the pre-built component to display in the action bar
     */
    public static void sendActionBar(final Player player, final Component msg) {
        player.sendActionBar(msg);
    }

    /**
     * Displays a title and subtitle to a player with configurable tick timing.
     *
     * @param player   the target player
     * @param title    the main title component
     * @param subtitle the subtitle component shown below the main title
     * @param in       fade-in duration in ticks
     * @param stay     stay duration in ticks
     * @param out      fade-out duration in ticks
     */
    public static void sendTitle(final Player player,
                                 final Component title,
                                 final Component subtitle,
                                 final int in,
                                 final int stay,
                                 final int out) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(in * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(out * 50L)
        );
        player.showTitle(Title.title(title, subtitle, times));
    }

    // ─── Broadcasting ─────────────────────────────────────────────

    /**
     * Sends a component to every currently online player.
     *
     * @param msg the component to broadcast
     */
    public static void broadcastMessage(final Component msg) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(msg);
        }
    }

    // ─── Centering ────────────────────────────────────────────────

    /**
     * Sends a chat message centered in the player's chat box.
     *
     * <p>Centering is approximated using pixel widths based on the default
     * Minecraft font. The space character is used as padding on the left.</p>
     *
     * @param player      the target player
     * @param miniMessage the MiniMessage-formatted string to center and send
     */
    public static void sendCenteredMessage(final Player player, final String miniMessage) {
        // Strip all MiniMessage tags to get the raw text for width calculation
        String stripped = mm.stripTags(miniMessage);

        int textWidth = estimatePixelWidth(stripped);
        int padding = Math.max(0, (CHAT_WIDTH_PIXELS - textWidth) / 2);
        int spaces = padding / SPACE_WIDTH;

        String spacePadding = " ".repeat(spaces);
        Component centered = parse(spacePadding + miniMessage);
        player.sendMessage(centered);
    }

    /**
     * Estimates the pixel width of a plain (tag-stripped) string using the
     * default Minecraft font's approximate character widths.
     *
     * @param text the raw text
     * @return estimated pixel width
     */
    private static int estimatePixelWidth(final String text) {
        int width = 0;
        for (char c : text.toCharArray()) {
            width += getCharWidth(c);
        }
        return width;
    }

    /**
     * Returns the approximate pixel width of a single character in the
     * default Minecraft font.
     *
     * @param c the character
     * @return pixel width
     */
    private static int getCharWidth(final char c) {
        return switch (c) {
            case 'f', 't'                      -> 4;
            case 'i', '!', ',', '.', ':', ';',
                 '|'                           -> 2;
            case 'l'                           -> 3;
            case ' '                           -> SPACE_WIDTH;
            default                            -> 6;
        };
    }
}
