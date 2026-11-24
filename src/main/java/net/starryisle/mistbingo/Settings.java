package net.starryisle.mistbingo;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Typed access to config.yml, with defaults that keep the plugin usable out of the box. */
public final class Settings {

    private final JavaPlugin plugin;
    private final ZoneId zone;
    private final int refreshHour;
    private final int maxPending;
    private final Set<Material> blacklist = EnumSet.noneOf(Material.class);
    private final Set<Material> excluded = EnumSet.noneOf(Material.class);

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.zone = ZoneId.of(config.getString("refresh.zone", "Asia/Shanghai"));
        this.refreshHour = Math.max(0, Math.min(23, config.getInt("refresh.hour", 0)));
        this.maxPending = Math.max(0, config.getInt("max-pending", 7));

        readMaterials(config, "blacklist", blacklist);
        readMaterials(config, "exclude", excluded);
    }

    private void readMaterials(FileConfiguration config, String path, Set<Material> target) {
        for (String name : config.getStringList(path)) {
            try {
                target.add(Material.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("未知的物品名，已跳过: " + name);
            }
        }
    }

    public ZoneId zone() {
        return zone;
    }

    /**
     * The card date currently in play.
     *
     * <p>Before the configured refresh hour the previous day's card is still the
     * active one, so a midnight boundary does not hand out a new card early.
     */
    public LocalDate today() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        if (refreshHour > 0 && now.getHour() < refreshHour) {
            return now.toLocalDate().minusDays(1);
        }
        return now.toLocalDate();
    }

    /** Milliseconds until the next refresh, used by the scheduler. */
    public long millisUntilNextRefresh() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.toLocalDate().atTime(refreshHour, 0).atZone(zone);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toMillis();
    }

    public int maxPending() {
        return maxPending;
    }

    /** Days of inactivity before a player's data is pruned; 0 keeps everything. */
    public int dataRetentionDays() {
        return Math.max(0, plugin.getConfig().getInt("data-retention-days", 90));
    }

    public boolean blacklisted(Material material) {
        return blacklist.contains(material);
    }

    /** Extra items to keep out of the pool, on top of the blacklist. */
    public boolean excluded(Material material) {
        return excluded.contains(material);
    }

    /** When true, per-action diagnostics are logged at INFO instead of FINE. */
    public boolean debug() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    /** Regenerate the built-in catalog on every start, replacing manual offers. */
    public boolean catalogOnStart() {
        return plugin.getConfig().getBoolean("shop.catalog-on-start", true);
    }

    public int catalogPrice() {
        return Math.min(ShopService.MAX_PRICE,
                Math.max(1, plugin.getConfig().getInt("shop.catalog-price", 2)));
    }

    /** Window title, mirroring the "商店 → 分类" style of the reference layout. */
    public String shopTitle() {
        String title = plugin.getConfig().getString("shop.title", "&8商店 &8→ &6建材");
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', title);
    }

    /** {@code gui} is the click-to-buy window; {@code merchant} is the vanilla trade UI. */
    public String shopStyle() {
        String style = plugin.getConfig().getString("shop.style", "gui");
        return style == null || style.isBlank() ? "gui" : style;
    }

    /** {@code text} prints the 3x3 as three lines; {@code bundle} uses the datapack's hover. */
    public String uiStyle() {
        String style = plugin.getConfig().getString("ui.style", "bundle");
        return style == null || style.isBlank() ? "bundle" : style;
    }

    /** {@code datapack} uses the curated list; {@code all} uses every obtainable item. */
    public String poolMode() {
        String mode = plugin.getConfig().getString("pool.mode", "datapack");
        return mode == null || mode.isBlank() ? "datapack" : mode;
    }

    /** Minimum per cell; the datapack rolls 1..5 for every slot. */
    public int minAmount() {
        return Math.max(1, plugin.getConfig().getInt("amounts.min", 1));
    }

    public int maxAmount() {
        return Math.max(minAmount(), plugin.getConfig().getInt("amounts.max", 5));
    }

    /** Extra vouchers awarded every {@code bonus-every} completed cards. */
    public int bonusEvery() {
        return Math.max(0, plugin.getConfig().getInt("voucher.bonus-every", 5));
    }

    public int bonusExtra() {
        return Math.max(0, plugin.getConfig().getInt("voucher.bonus-extra", 1));
    }

    /**
     * Console commands run once when a card completes, with {@code %player%}
     * replaced by the player's name. Missing section keeps the default reward so
     * servers upgrading from an older config still pay it.
     */
    public List<String> rewardCommands() {
        FileConfiguration config = plugin.getConfig();
        if (!config.isSet("reward.commands")) {
            return List.of("money give %player% 200");
        }
        return config.getStringList("reward.commands");
    }

    public String message(String key, String fallback) {
        String value = plugin.getConfig().getString("messages." + key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
