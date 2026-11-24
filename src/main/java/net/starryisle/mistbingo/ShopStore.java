package net.starryisle.mistbingo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Persists the shop offers in shops.yml. */
public final class ShopStore {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data = new YamlConfiguration();

    public ShopStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shops.yml");
    }

    public List<ShopEntry> load() {
        data = YamlConfiguration.loadConfiguration(file);
        List<ShopEntry> entries = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("offers");
        if (section == null) {
            return entries;
        }
        for (String key : section.getKeys(false)) {
            String encoded = section.getString(key + ".item");
            int price = section.getInt(key + ".price", 1);
            ItemStack item;
            try {
                item = ItemCodec.decode(encoded);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("商品 " + key + " 的物品数据无法读取，已跳过: "
                        + exception.getMessage());
                continue;
            }
            if (item != null) {
                int safePrice = Math.min(ShopService.MAX_PRICE, Math.max(1, price));
                entries.add(new ShopEntry(item, safePrice));
            }
        }
        return entries;
    }

    public boolean loadOpen() {
        return data.getBoolean("open", true);
    }

    public void saveOpen(boolean open) {
        data.set("open", open);
        flush();
    }

    public void save(List<ShopEntry> entries) {
        data.set("offers", null);
        int index = 0;
        for (ShopEntry entry : entries) {
            String path = "offers." + index++;
            data.set(path + ".item", ItemCodec.encode(entry.item()));
            data.set(path + ".price", entry.price());
        }
        flush();
    }

    private void flush() {
        Path target = file.toPath();
        Path temp = target.resolveSibling(file.getName() + ".tmp");
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("无法创建数据目录");
                return;
            }
            Files.writeString(temp, data.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
                // Best effort cleanup; the original file is still intact.
            }
            plugin.getLogger().severe("保存 shops.yml 失败: " + exception.getMessage());
        }
    }
}
