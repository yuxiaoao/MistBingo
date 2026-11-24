package net.starryisle.mistbingo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Picks the nine items for a card.
 *
 * <p>By default the pool is the curated list the NyaaGears datapack uses
 * ({@code pool.txt}, 332 obtainable items). Setting {@code pool.mode: all} falls
 * back to every obtainable material minus the blacklist.
 */
public final class ItemPool {

    private final MistBingoPlugin plugin;
    private final Settings settings;
    private final List<Material> candidates = new ArrayList<>();
    private final Random random = new Random();

    public ItemPool(MistBingoPlugin plugin) {
        this.plugin = plugin;
        this.settings = plugin.settings();
        rebuild();
    }

    /** Filters the material list; safe to call again after a config reload. */
    public void rebuild() {
        candidates.clear();

        boolean curated = settings.poolMode().equalsIgnoreCase("datapack");
        List<Material> source = curated ? loadCurated() : everyMaterial();

        for (Material material : source) {
            if (material == null || !material.isItem() || material.isAir() || material.isLegacy()) {
                continue;
            }
            if (material.getMaxStackSize() <= 0 || settings.blacklisted(material)
                    || settings.excluded(material)) {
                continue;
            }
            if (unobtainable(material)) {
                continue;
            }
            candidates.add(material);
        }
        candidates.sort(Collections.reverseOrder());
        Collections.reverse(candidates);
        if (candidates.isEmpty()) {
            plugin.getLogger().warning("物品池为空：请检查 pool.mode、blacklist 和 pool.txt");
        }
    }

    private List<Material> everyMaterial() {
        List<Material> all = new ArrayList<>();
        Collections.addAll(all, Material.values());
        return all;
    }

    /** Reads the curated list shipped inside the jar. */
    private List<Material> loadCurated() {
        List<Material> list = new ArrayList<>();
        try (InputStream stream = plugin.getResource("pool.txt")) {
            if (stream == null) {
                plugin.getLogger().warning("缺少 pool.txt，退回全物品模式");
                return everyMaterial();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String name = line.trim().toUpperCase(Locale.ROOT);
                    if (name.isEmpty()) {
                        continue;
                    }
                    try {
                        list.add(Material.valueOf(name));
                    } catch (IllegalArgumentException ignored) {
                        // The server version may not have this item; skip it.
                    }
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("读取 pool.txt 失败: " + exception.getMessage());
            return everyMaterial();
        }
        return list;
    }

    public int size() {
        return candidates.size();
    }

    /** Nine distinct materials, each paired with a random amount. */
    public Card roll(String date) {
        if (candidates.isEmpty()) {
            throw new IllegalStateException("物品池为空：请检查 pool.mode、blacklist 和 pool.txt");
        }
        List<Material> pool = new ArrayList<>(candidates);
        Collections.shuffle(pool, random);
        List<ItemStack> items = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        List<Boolean> submitted = new ArrayList<>();

        int min = Math.max(1, settings.minAmount());
        int max = Math.max(min, settings.maxAmount());

        for (int index = 0; index < 9; index++) {
            Material material = pool.get(index % pool.size());
            int amount = min + random.nextInt(max - min + 1);
            items.add(new ItemStack(material, 1));
            amounts.add(amount);
            submitted.add(false);
        }
        return new Card(date, items, amounts, submitted);
    }

    /**
     * Items a survival player cannot get. The curated list already avoids these,
     * but {@code pool.mode: all} needs the guard.
     */
    static boolean unobtainable(Material material) {
        String name = material.name();
        if (name.endsWith("_SPAWN_EGG")) {
            return true;
        }
        return switch (name) {
            case "BUDDING_AMETHYST", "REINFORCED_DEEPSLATE", "PETRIFIED_OAK_SLAB",
                 "KNOWLEDGE_BOOK", "DEBUG_STICK", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
                 "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "BARRIER",
                 "LIGHT", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW",
                 "BEDROCK", "END_PORTAL_FRAME", "SPAWNER", "TRIAL_SPAWNER", "VAULT",
                 // NBT-bearing items: an enchanted book or a potion asked for on a
                 // card is ambiguous, so they never enter the pool.
                 "ENCHANTED_BOOK", "POTION", "SPLASH_POTION", "LINGERING_POTION",
                 "TIPPED_ARROW", "OMINOUS_BOTTLE", "SUSPICIOUS_STEW",
                 "FILLED_MAP", "WRITTEN_BOOK", "WRITABLE_BOOK" -> true;
            default -> name.startsWith("INFESTED_");
        };
    }
}
