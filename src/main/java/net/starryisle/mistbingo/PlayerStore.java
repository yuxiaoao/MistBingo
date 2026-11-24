package net.starryisle.mistbingo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persists every player in a single {@code data.yml}, the way most plugins do.
 *
 * <p>To keep that cheap the file is only rewritten when something changed, at most
 * once per {@link #FLUSH_INTERVAL_TICKS}, and the actual disk write happens on a
 * background thread. Losing at most half a minute of progress on a crash is the
 * trade for not rewriting the whole file on every click.
 */
public final class PlayerStore {

    private static final long FLUSH_INTERVAL_TICKS = 20L * 20L;

    private final JavaPlugin plugin;
    private final File file;
    private final File legacyFolder;
    private YamlConfiguration data = new YamlConfiguration();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MistBingo-IO");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean dirty = new AtomicBoolean();

    public PlayerStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.legacyFolder = new File(plugin.getDataFolder(), "players");
    }

    public void load() {
        data = YamlConfiguration.loadConfiguration(file);
        importLegacyFolder();
    }

    /** Pulls in files written by the short-lived per-player layout, if any exist. */
    private void importLegacyFolder() {
        File[] files = legacyFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return;
        }
        int imported = 0;
        for (File legacy : files) {
            String name = legacy.getName().replace(".yml", "");
            try {
                UUID uuid = UUID.fromString(name);
                if (data.isConfigurationSection("players." + uuid)) {
                    continue;
                }
                YamlConfiguration config = YamlConfiguration.loadConfiguration(legacy);
                ConfigurationSection section = config.getConfigurationSection("player");
                if (section != null) {
                    save(uuid, fromSection(section));
                    imported++;
                }
            } catch (IllegalArgumentException ignored) {
                // Not a UUID file; leave it alone.
            }
        }
        if (imported > 0) {
            dirty.set(true);
            plugin.getLogger().info("已从 players/ 导入 " + imported + " 名玩家数据");
            flush();
        }
        for (File legacy : files) {
            if (!legacy.delete()) {
                plugin.getLogger().warning("无法删除旧的玩家文件: " + legacy.getName());
            }
        }
    }

    public PlayerState get(UUID uuid) {
        ConfigurationSection section = data.getConfigurationSection("players." + uuid);
        return section == null ? new PlayerState() : fromSection(section);
    }

    private PlayerState fromSection(ConfigurationSection section) {
        PlayerState state = new PlayerState();
        state.lastRollDate(section.getString("last-roll", ""));
        state.lastSeen(section.getLong("last-seen", 0L));
        state.streak(section.getInt("streak", 0));

        ConfigurationSection cards = section.getConfigurationSection("cards");
        if (cards == null) {
            return state;
        }
        for (String key : cards.getKeys(false)) {
            ConfigurationSection cardSection = cards.getConfigurationSection(key);
            if (cardSection == null) {
                continue;
            }
            String date = cardSection.getString("date", key);
            List<ItemStack> items = new ArrayList<>();
            List<Integer> amounts = new ArrayList<>();
            List<Boolean> submitted = new ArrayList<>();

            boolean valid = true;
            for (String value : cardSection.getStringList("items")) {
                try {
                    ItemStack item = ItemCodec.decode(value);
                    if (item == null || item.getType().isAir()) {
                        valid = false;
                        break;
                    }
                    items.add(item);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("玩家 " + section.getName()
                            + " 的集物表物品无法读取，已跳过该表: " + exception.getMessage());
                    valid = false;
                    break;
                }
            }
            amounts.addAll(cardSection.getIntegerList("amounts"));
            submitted.addAll(cardSection.getBooleanList("submitted"));

            if (!valid || items.size() != 9 || amounts.size() != 9 || submitted.size() != 9) {
                plugin.getLogger().warning("玩家 " + section.getName()
                        + " 的集物表数据不完整，已跳过");
                continue;
            }
            state.cards().add(new Card(date, items, amounts, submitted));
        }
        return state;
    }

    /** Updates the in-memory file image; the disk write is batched. */
    public void save(UUID uuid, PlayerState state) {
        String path = "players." + uuid;
        data.set(path + ".last-roll", state.lastRollDate());
        data.set(path + ".last-seen", state.lastSeen());
        data.set(path + ".streak", state.streak());
        data.set(path + ".cards", null);

        int index = 0;
        for (Card card : state.cards()) {
            String cardPath = path + ".cards." + index++;
            data.set(cardPath + ".date", card.date());
            List<String> encoded = new ArrayList<>();
            List<Integer> amounts = new ArrayList<>();
            List<Boolean> submitted = new ArrayList<>();
            for (int cell = 0; cell < 9; cell++) {
                encoded.add(ItemCodec.encode(card.items().get(cell)));
                amounts.add(card.amount(cell));
                submitted.add(card.isSubmitted(cell));
            }
            data.set(cardPath + ".items", encoded);
            data.set(cardPath + ".amounts", amounts);
            data.set(cardPath + ".submitted", submitted);
        }
        dirty.set(true);
    }

    public long flushIntervalTicks() {
        return FLUSH_INTERVAL_TICKS;
    }

    /** Removes players whose last activity is older than {@code days}; 0 disables it. */
    public int prune(int days) {
        if (days <= 0) {
            return 0;
        }
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        int removed = 0;
        for (String key : new ArrayList<>(players.getKeys(false))) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (plugin.getServer().getPlayer(uuid) != null) {
                continue;
            }
            long lastSeen = players.getLong(key + ".last-seen", 0L);
            if (lastSeen <= 0) {
                lastSeen = parseDate(players.getString(key + ".last-roll", ""));
            }
            if (lastSeen > 0 && lastSeen < cutoff) {
                players.set(key, null);
                removed++;
            }
        }
        if (removed > 0) {
            dirty.set(true);
            plugin.getLogger().info("已清理 " + removed + " 名超过 " + days + " 天未活动的玩家数据");
            flush();
        }
        return removed;
    }

    private static long parseDate(String value) {
        try {
            return LocalDate.parse(value)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /** Serialises now and writes on the IO thread; no-op when nothing changed. */
    public void flush() {
        if (!dirty.compareAndSet(true, false)) {
            return;
        }
        String text = data.saveToString();
        writer.execute(() -> writeToDisk(text));
    }

    /** Writes to a temp file first so a crash cannot leave a half-written data.yml. */
    private void writeToDisk(String text) {
        Path target = file.toPath();
        Path temp = target.resolveSibling(file.getName() + ".tmp");
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("无法创建数据目录");
                dirty.set(true);
                return;
            }
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            dirty.set(true);
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
                // Best effort cleanup; the original file is still intact.
            }
            plugin.getLogger().severe("保存 data.yml 失败: " + exception.getMessage());
        }
    }

    /** Writes pending changes and waits for the queue, used on shutdown. */
    public void shutdown() {
        dirty.set(true);
        String text = data.saveToString();
        writer.execute(() -> writeToDisk(text));
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("保存队列超时，可能有数据未写入");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
