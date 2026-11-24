package net.starryisle.mistbingo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Chinese display names for the pool items.
 *
 * <p>The names come from the vanilla {@code zh_cn} language file, generated into
 * {@code item-names-zh_cn.properties}. Unknown materials fall back to the English
 * material name so a new server version never shows blanks.
 */
public final class ItemNames {

    private final Map<String, String> names = new HashMap<>();

    public ItemNames(JavaPlugin plugin) {
        try (InputStream stream = plugin.getResource("item-names-zh_cn.properties")) {
            if (stream == null) {
                plugin.getLogger().warning("缺少中文物品名文件，将显示英文名");
                return;
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            properties.forEach((key, value) -> names.put(
                    String.valueOf(key).toUpperCase(java.util.Locale.ROOT), String.valueOf(value)));
            plugin.getLogger().info("已加载中文物品名 " + names.size() + " 条");
        } catch (Exception exception) {
            plugin.getLogger().warning("读取中文物品名失败: " + exception.getMessage());
        }
    }

    /** Chinese name when known, otherwise a readable English fallback. */
    public String of(Material material) {
        String name = names.get(material.name());
        return name != null ? name : pretty(material);
    }

    /** Shorter name for the three-column shortfall report. */
    public String shortOf(Material material) {
        String name = of(material);
        return name.length() > 6 ? name.substring(0, 6) + "…" : name;
    }

    private static String pretty(Material material) {
        String name = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
