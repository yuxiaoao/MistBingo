package net.starryisle.mistbingo;

import java.io.File;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Plugin entry point: wires the services and keeps cards rolling at midnight. */
public final class MistBingoPlugin extends JavaPlugin {

    private static final String BUILD = "2026-11-24.7";

    private Settings settings;
    private ItemPool pool;
    private ItemNames names;
    private Voucher voucher;
    private PlayerStore store;
    private MistDailyService daily;
    private ChatUi chat;
    private ShopStore shopStore;
    private ShopService shop;
    private ShopGui shopGui;
    private BukkitTask refreshTask;
    private BukkitTask storeFlushTask;
    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        migrateLegacyData();
        saveDefaultConfig();

        settings = new Settings(this);
        names = new ItemNames(this);
        pool = new ItemPool(this);
        voucher = new Voucher(this);
        store = new PlayerStore(this);
        store.load();
        store.prune(settings.dataRetentionDays());
        daily = new MistDailyService(this);
        chat = new ChatUi(this);

        shopStore = new ShopStore(this);
        shop = new ShopService(this);
        shop.reload();
        if (settings.catalogOnStart() && shop.offers().isEmpty()) {
            // Only seed the catalog when the shop is empty; replacing it on every
            // start would wipe offers added with /mistshop add.
            shop.replace(new ShopCatalog(this).build(settings.catalogPrice()));
            getLogger().info("商店目录已生成: " + shop.offers().size() + " 件");
        }
        shopGui = new ShopGui(this);
        getServer().getPluginManager().registerEvents(shopGui, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new VoucherListener(this), this);

        PluginCommand dailyCommand = getCommand("mistdaily");
        if (dailyCommand != null) {
            MistDailyCommand executor = new MistDailyCommand(this);
            dailyCommand.setExecutor(executor);
            dailyCommand.setTabCompleter(executor);
        } else {
            getLogger().severe("plugin.yml 缺少 mistdaily 命令");
        }

        PluginCommand shopCommand = getCommand("mistshop");
        if (shopCommand != null) {
            MistShopCommand executor = new MistShopCommand(this);
            shopCommand.setExecutor(executor);
            shopCommand.setTabCompleter(executor);
        } else {
            getLogger().severe("plugin.yml 缺少 mistshop 命令");
        }

        // Re-check every minute so players who stay online across midnight still
        // receive a fresh card without reconnecting, and drop states of players who
        // left without a quit event.
        refreshTask = getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                try {
                    daily.ensureToday(player);
                } catch (RuntimeException exception) {
                    getLogger().warning("刷新 " + player.getName() + " 的集物表失败: "
                            + exception.getMessage());
                }
            }
            daily.evictOffline();
        }, 20L * 60, 20L * 60);

        // Batched write of player data: the file is serialised on the main thread and
        // written off-thread, so a click never waits for disk IO.
        storeFlushTask = getServer().getScheduler().runTaskTimer(this,
                () -> store.flush(), store.flushIntervalTicks(), store.flushIntervalTicks());

        // Prune inactive players every six hours; the same pass runs on startup.
        cleanupTask = getServer().getScheduler().runTaskTimer(this,
                () -> store.prune(settings.dataRetentionDays()),
                20L * 60 * 60 * 6, 20L * 60 * 60 * 6);

        getLogger().info("MistBingo build " + BUILD + " enabled, item pool " + pool.size()
                + ", offers " + shop.offers().size()
                + ", jar " + jarTimestamp());
    }

    /**
     * Copies settings and player data from the old {@code plugins/DailyBingo}
     * folder after the rename, so existing progress is not lost.
     */
    private void migrateLegacyData() {
        File parent = getDataFolder().getParentFile();
        if (parent == null) {
            return;
        }
        File legacy = new File(parent, "DailyBingo");
        if (!legacy.isDirectory()) {
            return;
        }
        File target = getDataFolder();
        if (!target.exists() && !target.mkdirs()) {
            getLogger().warning("无法创建数据目录，跳过迁移");
            return;
        }
        for (String name : new String[]{"config.yml", "data.yml", "shops.yml"}) {
            File source = new File(legacy, name);
            File destination = new File(target, name);
            if (source.isFile() && !destination.exists()) {
                try {
                    java.nio.file.Files.copy(source.toPath(), destination.toPath());
                    getLogger().info("已从旧目录 DailyBingo 迁移 " + name);
                } catch (Exception exception) {
                    getLogger().warning("迁移 " + name + " 失败: " + exception.getMessage());
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        if (storeFlushTask != null) {
            storeFlushTask.cancel();
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        if (daily != null) {
            daily.saveAll();
        }
        if (store != null) {
            store.shutdown();
        }
    }

    /** Diagnostic line: printed only when {@code debug: true}, otherwise kept at FINE. */
    public void debugLog(String message) {
        if (settings != null && settings.debug()) {
            getLogger().info(message);
        } else {
            getLogger().fine(message);
        }
    }

    /** Modification time of the jar actually loaded, useful when a live swap broke classes. */
    public String jarTimestamp() {
        try {
            var file = getFile();
            var time = java.nio.file.Files.getLastModifiedTime(file.toPath());
            return java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(time.toInstant());
        } catch (Exception exception) {
            return "unknown";
        }
    }

    public Settings settings() {
        return settings;
    }

    public ItemPool pool() {
        return pool;
    }

    /** Localised item names, used by every player-facing list. */
    public ItemNames names() {
        return names;
    }

    public Voucher voucher() {
        return voucher;
    }

    public PlayerStore store() {
        return store;
    }

    public MistDailyService daily() {
        return daily;
    }

    public ChatUi chat() {
        return chat;
    }

    public ShopStore shopStore() {
        return shopStore;
    }

    public ShopService shop() {
        return shop;
    }

    public ShopGui shopGui() {
        return shopGui;
    }
}
