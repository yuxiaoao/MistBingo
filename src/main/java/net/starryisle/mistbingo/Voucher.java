package net.starryisle.mistbingo;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Creates and recognises the building-material voucher. */
public final class Voucher {

    private final NamespacedKey key;
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final int rewardPerCard;

    public Voucher(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "voucher");
        String configured = plugin.getConfig().getString("voucher.material", "PAPER");
        Material parsed;
        try {
            parsed = Material.valueOf(configured.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("未知的券材质 " + configured + "，改用 PAPER");
            parsed = Material.PAPER;
        }
        this.material = parsed;
        this.name = plugin.getConfig().getString("voucher.name", "&6建材兑换券");
        this.lore = plugin.getConfig().getStringList("voucher.lore");
        this.rewardPerCard = plugin.getConfig().getInt("voucher.reward-per-card", 1);
    }

    public int rewardPerCard() {
        return rewardPerCard;
    }

    public ItemStack create(int amount) {
        int max = Math.max(1, material.getMaxStackSize());
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(amount, max)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (!lore.isEmpty()) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(color(line));
                }
                meta.setLore(lines);
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isVoucher(ItemStack stack) {
        if (stack == null || stack.getType() != material) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** A bare voucher stack used as the merchant's ingredient, count is the price. */
    public ItemStack price(int amount) {
        return create(Math.max(1, amount));
    }

    /** Splits a large amount into legal stacks (e.g. 2304 vouchers). */
    public List<ItemStack> createStacks(int amount) {
        int remaining = Math.max(1, amount);
        int max = Math.max(1, material.getMaxStackSize());
        List<ItemStack> stacks = new ArrayList<>();
        while (remaining > 0) {
            int size = Math.min(remaining, max);
            stacks.add(create(size));
            remaining -= size;
        }
        return stacks;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
