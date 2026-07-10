package com.polarsmp.rank;

import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.List;

/**
 * Immutable model representing the perk bundle for a single rank.
 *
 * <p>Loaded from ranks.yml at startup and cached in {@link RankManager}.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class RankPerk {

    private final int rankNumber;
    private final int extraHearts;
    private final List<PerkEffect> effects;
    private final String colorFormat;
    private final String prefixFormat;
    private final int customModelData;

    /**
     * Constructs a RankPerk with all fields.
     *
     * @param rankNumber     the rank this perk belongs to (1–10)
     * @param extraHearts    number of extra half-hearts granted
     * @param effects        list of potion effects to apply
     * @param colorFormat    MiniMessage color tag for this rank
     * @param prefixFormat   MiniMessage prefix string for nametag/tab
     * @param customModelData resource pack custom model data value
     */
    public RankPerk(final int rankNumber, final int extraHearts, final List<PerkEffect> effects,
                    final String colorFormat, final String prefixFormat, final int customModelData) {
        this.rankNumber = rankNumber;
        this.extraHearts = extraHearts;
        this.effects = Collections.unmodifiableList(effects);
        this.colorFormat = colorFormat;
        this.prefixFormat = prefixFormat;
        this.customModelData = customModelData;
    }

    /** @return the rank number (1–10) */
    public int getRankNumber() { return rankNumber; }

    /** @return extra half-hearts granted (e.g. 10 = 5 extra hearts = 30 total HP) */
    public int getExtraHearts() { return extraHearts; }

    /** @return total max health in half-hearts (base 20 + extra) */
    public double getMaxHealth() { return 20.0 + extraHearts; }

    /** @return unmodifiable list of potion effects for this rank */
    public List<PerkEffect> getEffects() { return effects; }

    /** @return MiniMessage color format string */
    public String getColorFormat() { return colorFormat; }

    /** @return MiniMessage prefix format string */
    public String getPrefixFormat() { return prefixFormat; }

    /** @return custom model data for resource pack rank badge */
    public int getCustomModelData() { return customModelData; }

    /**
     * Inner record representing a single potion effect perk.
     *
     * @param effectType the Bukkit PotionEffectType
     * @param amplifier  the amplifier (0-indexed, so 1 = Level II)
     */
    public record PerkEffect(PotionEffectType effectType, int amplifier) {}
}
