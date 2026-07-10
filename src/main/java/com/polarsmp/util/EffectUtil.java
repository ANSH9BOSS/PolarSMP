package com.polarsmp.util;

import com.polarsmp.rank.RankPerk;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.UUID;

/**
 * Static utility class for managing rank-based potion effects and attribute modifiers.
 *
 * <p>All rank-applied effects are tagged in the player's
 * {@link org.bukkit.persistence.PersistentDataContainer} (PDC) so they can be cleanly
 * removed when a rank changes or is revoked, without touching any non-plugin effects.</p>
 *
 * <p>Max health is extended via a named {@link AttributeModifier} whose
 * {@link NamespacedKey} encodes the rank number. Stable UUIDs per rank ensure
 * old modifiers can always be found and removed across restarts.</p>
 *
 * <p><strong>All methods must be called from the main server thread.</strong></p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class EffectUtil {

    /**
     * Deterministic UUIDs for rank health {@link AttributeModifier}s (index 0 = rank 1).
     * Fixed values allow reliable removal across plugin reloads.
     */
    private static final UUID[] RANK_MODIFIER_UUIDS = {
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"), // rank 1
            UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f01234567891"), // rank 2
            UUID.fromString("c3d4e5f6-a7b8-9012-cdef-012345678902"), // rank 3
            UUID.fromString("d4e5f6a7-b8c9-0123-def0-123456789013"), // rank 4
            UUID.fromString("e5f6a7b8-c9d0-1234-ef01-234567890124"), // rank 5
            UUID.fromString("f6a7b8c9-d0e1-2345-f012-345678901235"), // rank 6
            UUID.fromString("a7b8c9d0-e1f2-3456-0123-456789012346"), // rank 7
            UUID.fromString("b8c9d0e1-f2a3-4567-1234-567890123457"), // rank 8
            UUID.fromString("c9d0e1f2-a3b4-5678-2345-678901234568"), // rank 9
            UUID.fromString("d0e1f2a3-b4c5-6789-3456-789012345679"), // rank 10
    };

    /**
     * {@link NamespacedKey} used to tag plugin-applied potion effects in the
     * player's {@link org.bukkit.persistence.PersistentDataContainer}.
     * The stored value is a comma-separated list of Bukkit effect-type key strings.
     */
    public static final NamespacedKey POLAR_EFFECT_KEY =
            new NamespacedKey("polarsmp", "rank_effects");

    /** NamespacedKey prefix for all PolarSMP rank health attribute modifiers. */
    private static final String MODIFIER_KEY_PREFIX = "polar_rank_";

    private EffectUtil() {
        throw new UnsupportedOperationException("EffectUtil is a static utility class.");
    }

    // ─── Apply rank perks ─────────────────────────────────────────

    /**
     * Removes any existing rank perks and applies the perks defined in the
     * given {@link RankPerk} to the player.
     *
     * <p>Potion effects are applied as permanent (duration {@link PotionEffect#INFINITE_DURATION}),
     * ambient, without visible particles, and with the HUD icon visible.</p>
     *
     * <p>The player's current health is clamped to the new maximum if the new
     * maximum is lower than the current health value.</p>
     *
     * @param player the target player (must be online)
     * @param perk   the rank perk bundle to apply
     */
    public static void applyRankPerks(final Player player, final RankPerk perk) {
        // 1. Remove old rank perks first
        removeRankPerks(player);

        // 2. Apply potion effects and track names in PDC
        StringBuilder effectNames = new StringBuilder();
        for (RankPerk.PerkEffect pe : perk.getEffects()) {
            player.addPotionEffect(new PotionEffect(
                    pe.effectType(),
                    PotionEffect.INFINITE_DURATION,
                    pe.amplifier(),
                    true,   // ambient – reduces particles
                    false,  // no particles
                    true    // show HUD icon
            ));
            if (!effectNames.isEmpty()) effectNames.append(',');
            effectNames.append(pe.effectType().key().asString());
        }

        // 3. Tag the applied effects in PDC
        player.getPersistentDataContainer().set(
                POLAR_EFFECT_KEY,
                PersistentDataType.STRING,
                effectNames.toString()
        );

        // 4. Apply max-health attribute modifier
        if (perk.getExtraHearts() > 0) {
            AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                NamespacedKey key = new NamespacedKey("polarsmp",
                        MODIFIER_KEY_PREFIX + perk.getRankNumber());
                AttributeModifier modifier = new AttributeModifier(
                        key,
                        perk.getExtraHearts(),
                        AttributeModifier.Operation.ADD_NUMBER
                );
                attr.addModifier(modifier);

                // 5. Clamp current health to the new maximum
                double newMax = attr.getValue();
                if (player.getHealth() > newMax) {
                    player.setHealth(newMax);
                }
            }
        }
    }

    // ─── Remove rank perks ────────────────────────────────────────

    /**
     * Removes all rank-applied potion effects and health attribute modifiers
     * from the player.
     *
     * <p>Effects are identified by the comma-separated PDC entry at
     * {@link #POLAR_EFFECT_KEY}. Only effects whose type key matches an entry
     * in that list AND whose duration is {@link PotionEffect#INFINITE_DURATION}
     * are removed, preserving any vanilla or other plugin effects.</p>
     *
     * <p>After removal, if the player's health exceeds the base maximum of 20
     * (10 hearts) it is clamped down to {@code min(currentMax, 20)}.</p>
     *
     * @param player the target player (must be online)
     */
    public static void removeRankPerks(final Player player) {
        // 1. Remove PDC-tagged potion effects
        String stored = player.getPersistentDataContainer()
                .get(POLAR_EFFECT_KEY, PersistentDataType.STRING);
        if (stored != null && !stored.isBlank()) {
            for (String effectKey : stored.split(",")) {
                String trimmed = effectKey.trim();
                if (trimmed.isEmpty()) continue;
                Collection<PotionEffect> active = player.getActivePotionEffects();
                for (PotionEffect effect : active) {
                    if (effect.getType().key().asString().equalsIgnoreCase(trimmed)
                            && effect.getDuration() == PotionEffect.INFINITE_DURATION) {
                        player.removePotionEffect(effect.getType());
                        break;
                    }
                }
            }
        }
        // Clean up the PDC entry
        player.getPersistentDataContainer().remove(POLAR_EFFECT_KEY);

        // 2. Remove all rank health attribute modifiers
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.getModifiers().stream()
                    .filter(m -> m.getKey().getKey().startsWith(MODIFIER_KEY_PREFIX))
                    .toList()
                    .forEach(attr::removeModifier);

            // 3. Cap health at base 20 HP if it now exceeds it
            double currentMax = attr.getValue();
            double capAt = Math.min(currentMax, 20.0);
            if (player.getHealth() > capAt) {
                player.setHealth(capAt);
            }
        }
    }
}
