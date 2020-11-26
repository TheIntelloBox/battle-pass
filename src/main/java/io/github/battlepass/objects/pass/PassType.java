package io.github.battlepass.objects.pass;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.github.battlepass.actions.Action;
import io.github.battlepass.cache.RewardCache;
import io.github.battlepass.objects.user.User;
import me.hyfe.simplespigot.config.Config;
import me.hyfe.simplespigot.item.SpigotItem;
import me.hyfe.simplespigot.text.Text;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigInteger;
import java.util.List;
import java.util.TreeMap;

public class PassType {
    private final Config config;
    private final String id;
    private final String name;
    private final String requiredPermission;
    private final int defaultPointsRequired;
    private final TreeMap<Integer, Tier> tiers = Maps.newTreeMap();
    private final List<Action> tierUpActions = Lists.newArrayList();

    public PassType(String id, Config config) {
        this.id = id;
        this.config = config;
        this.name = config.string("name");
        this.requiredPermission = config.string("required-permission");
        this.defaultPointsRequired = config.integer("default-points-required");
        for (String key : config.keys("tiers", false)) {
            if (!StringUtils.isNumeric(key)) {
                continue;
            }
            int tier = Integer.parseInt(key);
            int requiredPoints = config.has("tiers." + key + ".required-points") ? config.integer("tiers." + key + ".required-points") : this.defaultPointsRequired;
            List<String> rewardIds = config.stringList("tiers." + key + ".rewards");
            ItemStack lockedTierItem = SpigotItem.toItem(this.config, "tiers." + key + ".locked-tier-item", replacer -> replacer.set("tier", tier));
            ItemStack unlockedTierItem = SpigotItem.toItem(this.config, "tiers." + key + ".unlocked-tier-item", replacer -> replacer.set("tier", tier));
            ItemStack claimedTierItem = SpigotItem.toItem(this.config, "tiers." + key + ".claimed-tier-item", replacer -> replacer.set("tier", tier));
            this.tiers.put(tier, new Tier(tier, requiredPoints, rewardIds, lockedTierItem, unlockedTierItem, claimedTierItem));
        }
        for (String action : config.stringList("tier-up-actions")) {
            this.tierUpActions.add(Action.parse(action));
        }
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getRequiredPermission() {
        return this.requiredPermission;
    }

    public int getDefaultPointsRequired() {
        return this.defaultPointsRequired;
    }

    public BigInteger getTotalPoints(int maxTier) {
        BigInteger totalPoints = BigInteger.ZERO;
        for (int i = 2; i <= maxTier; i++) {
            totalPoints = totalPoints.add(BigInteger.valueOf(this.tiers.containsKey(i) ? this.tiers.get(i).getRequiredPoints() : this.defaultPointsRequired));
        }
        return totalPoints;
    }

    public TreeMap<Integer, Tier> getTiers() {
        return this.tiers;
    }

    public List<Action> getTierUpActions() {
        return this.tierUpActions;
    }

    public ItemStack tierToItem(RewardCache rewardCache, User user, String passId, Tier tier, boolean hasTier) {
        boolean hasPass = user.hasPassId(passId);
        boolean hasClaimed = user.getPendingTiers(passId) != null && !user.getPendingTiers(passId).contains(tier.getTier());
        String itemKey;
        ItemStack itemStack;
        if (this.config.has("items.".concat("doesnt-have-pass-item"))) {
            itemKey = hasPass ? (hasTier ? (hasClaimed ? "claimed-tier-item" : "unlocked-tier-item") : "locked-tier-item") : "doesnt-have-pass-item";
            itemStack = SpigotItem.toItem(this.config, "items.".concat(itemKey), replacer -> replacer.set("tier", tier.getTier()));
        } else {
            itemKey = hasTier ? (hasClaimed ? "claimed-tier-item" : "unlocked-tier-item") : "locked-tier-item";
            itemStack = SpigotItem.toItem(this.config, "items.".concat(itemKey), replacer -> replacer.set("tier", tier.getTier()));
        }
        ItemStack tierItem = tier.getItem(itemKey);
        if (tierItem != null && !tierItem.getType().equals(Material.DIRT)) {
            itemStack = tierItem;
        }
        if (itemStack.getItemMeta() == null || itemStack.getItemMeta().getLore() == null) {
            return itemStack;
        }
        List<String> updatedLore = Lists.newArrayList();
        ItemMeta itemMeta = itemStack.getItemMeta();
        for (String line : itemMeta.getLore()) {
            if (line.contains("%lore_addon%")) {
                for (String rewardId : tier.getRewardIds()) {
                    rewardCache.get(rewardId).ifPresent(reward -> updatedLore.addAll(Text.modify(reward.getLoreAddon())));
                }
            } else {
                updatedLore.add(line);
            }
        }
        itemMeta.setLore(updatedLore);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }
}
