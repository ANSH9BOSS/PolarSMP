package com.polarsmp.util;

/**
 * Static utility class providing string-formatting helpers for in-game display.
 *
 * <p>All methods are pure functions with no side effects and are safe to call
 * from any thread.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class FormatUtil {

    private FormatUtil() {
        throw new UnsupportedOperationException("FormatUtil is a static utility class.");
    }

    // ─── Number formatting ────────────────────────────────────────

    /**
     * Formats a large number into a compact human-readable string.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code 999} → {@code "999"}</li>
     *   <li>{@code 1_000} → {@code "1.0K"}</li>
     *   <li>{@code 1_500_000} → {@code "1.5M"}</li>
     *   <li>{@code 2_000_000_000} → {@code "2.0B"}</li>
     * </ul>
     * </p>
     *
     * @param num the number to format
     * @return a compact string representation
     */
    public static String formatNumber(final long num) {
        if (num < 0) return "-" + formatNumber(-num);
        if (num < 1_000L)       return String.valueOf(num);
        if (num < 1_000_000L)   return String.format("%.1fK", num / 1_000.0);
        if (num < 1_000_000_000L) return String.format("%.1fM", num / 1_000_000.0);
        return String.format("%.1fB", num / 1_000_000_000.0);
    }

    // ─── K/D ratio ────────────────────────────────────────────────

    /**
     * Formats a kill-to-death ratio as a 2-decimal-place string.
     *
     * <p>Deaths is floored at {@code 1} to prevent division-by-zero; a player
     * with zero deaths will therefore show a ratio equal to their kill count.</p>
     *
     * @param kills  total kills
     * @param deaths total deaths
     * @return the K/D string, e.g. {@code "3.50"}
     */
    public static String formatKD(final int kills, final int deaths) {
        int safeDeaths = Math.max(1, deaths);
        return String.format("%.2f", (double) kills / safeDeaths);
    }

    // ─── Duration formatting ──────────────────────────────────────

    /**
     * Converts a duration in milliseconds into a human-readable time string.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code 90_000} ms (90 s) → {@code "1m"}</li>
     *   <li>{@code 3_661_000} ms → {@code "1h 1m"}</li>
     *   <li>{@code 90_061_000} ms → {@code "1d 1h 1m"}</li>
     * </ul>
     * Components with a zero value are omitted unless all components are zero,
     * in which case {@code "0m"} is returned.</p>
     *
     * @param ms duration in milliseconds
     * @return the formatted duration string
     */
    public static String formatDuration(final long ms) {
        long totalSeconds = ms / 1_000L;
        long days    = totalSeconds / 86_400L;
        long hours   = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L)  / 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0)    sb.append(days).append("d ");
        if (hours > 0)   sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m");

        String result = sb.toString().trim();
        return result.isEmpty() ? "0m" : result;
    }

    // ─── Progress bar ─────────────────────────────────────────────

    /**
     * Generates a MiniMessage-formatted progress bar string.
     *
     * <p>Example output with {@code filled='|', empty='|', filledColor="<green>",
     * emptyColor="<dark_gray>", barLength=20, current=10, max=20}:
     * <pre>{@code <green>||||||||||<dark_gray>||||||||||}</pre></p>
     *
     * @param current     the current value (clamped to [0, max])
     * @param max         the maximum value (must be &gt; 0)
     * @param barLength   the total number of bar characters
     * @param filled      the character used for the filled portion
     * @param empty       the character used for the empty portion
     * @param filledColor a MiniMessage color tag (e.g. {@code "<green>"})
     * @param emptyColor  a MiniMessage color tag (e.g. {@code "<dark_gray>"})
     * @return a MiniMessage-formatted progress bar string
     */
    public static String generateProgressBar(final long current,
                                             final long max,
                                             final int barLength,
                                             final char filled,
                                             final char empty,
                                             final String filledColor,
                                             final String emptyColor) {
        if (max <= 0) {
            return emptyColor + String.valueOf(empty).repeat(barLength);
        }

        long clampedCurrent = Math.clamp(current, 0L, max);
        int filledCount = (int) Math.round((double) clampedCurrent / max * barLength);
        int emptyCount  = barLength - filledCount;

        StringBuilder bar = new StringBuilder();
        if (filledCount > 0) {
            bar.append(filledColor).append(String.valueOf(filled).repeat(filledCount));
        }
        if (emptyCount > 0) {
            bar.append(emptyColor).append(String.valueOf(empty).repeat(emptyCount));
        }
        return bar.toString();
    }

    /**
     * Formats a progress percentage.
     *
     * @param current current progress
     * @param max     max progress
     * @return percentage string (e.g. "75%")
     */
    public static String formatPercent(final long current, final long max) {
        if (max <= 0) return "0%";
        int pct = (int) Math.round((double) Math.clamp(current, 0L, max) / max * 100.0);
        return pct + "%";
    }
}
