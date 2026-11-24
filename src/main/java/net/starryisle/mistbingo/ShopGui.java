package net.starryisle.mistbingo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Click-to-buy shop window: the whole shop on pages of 45 items, each showing its
 * price and whether the player can afford it.
 */
public final class ShopGui implements Listener {

    /** Items start on the second row, like the reference layout. */
    private static final int FIRST_SLOT = 9;
    private static final int ENTRY_SLOTS = 36;
    private static final int PREV_SLOT = 45;
    private static final int BALANCE_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int CLOSE_SLOT = 53;
    private static final long PURCHASE_COOLDOWN_MS = 300L;

    private final MistBingoPlugin plugin;
    private final Map<UUID, Long> lastPurchase = new HashMap<>();

    public ShopGui(MistBingoPlugin plugin) {
        this.plugin = plugin;
    }

    public static final class Holder implements InventoryHolder {
        private int page;
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int page) {
        List<ShopEntry> offers = plugin.shop().offers();
        Holder holder = new Holder();
        holder.page = Math.max(0, Math.min(page, pages(offers) - 1));

        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text(plugin.settings().shopTitle()));
        holder.inventory = inventory;
        render(player, inventory, holder);
        player.openInventory(inventory);
    }

    private static int pages(List<ShopEntry> offers) {
        return Math.max(1, (offers.size() + ENTRY_SLOTS - 1) / ENTRY_SLOTS);
    }

    private void render(Player player, Inventory inventory, Holder holder) {
        List<ShopEntry> offers = plugin.shop().offers();
        int balance = plugin.shop().countVouchers(player);
        int start = holder.page * ENTRY_SLOTS;

        for (int slot = 0; slot < 54; slot++) {
            inventory.setItem(slot, null);
        }

        for (int index = 0; index < ENTRY_SLOTS; index++) {
            int offerIndex = start + index;
            if (offerIndex >= offers.size()) {
                break;
            }
            ShopEntry entry = offers.get(offerIndex);
            ItemStack display = entry.item().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add("§7价格：§e" + entry.price() + " §7张兑换券");
                lore.add(balance >= entry.price()
                        ? "§a你现在买得起（持有 " + balance + " 张）"
                        : "§c还差 " + (entry.price() - balance) + " 张");
                lore.add("");
                lore.add("§e点击兑换");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(FIRST_SLOT + index, display);
        }

        int pages = pages(offers);
        inventory.setItem(BALANCE_SLOT, button(Material.PAPER,
                "§6兑换券：§f" + balance,
                List.of("§7点击商品立即兑换", "§7第 " + (holder.page + 1) + " 页 共 " + pages + " 页")));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "§c关闭商店",
                List.of("§7点击关闭这个界面")));
        if (pages > 1) {
            inventory.setItem(PREV_SLOT, button(Material.ARROW, "§6<- 上一页",
                    List.of("§7第 " + (holder.page + 1) + " 页 共 " + pages)));
            inventory.setItem(NEXT_SLOT, button(Material.ARROW, "§6下一页 ->",
                    List.of("§7第 " + (holder.page + 1) + " 页 共 " + pages)));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }
        if (slot == PREV_SLOT) {
            open(player, holder.page - 1);
            return;
        }
        if (slot == NEXT_SLOT) {
            open(player, holder.page + 1);
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot < FIRST_SLOT || slot >= FIRST_SLOT + ENTRY_SLOTS) {
            return;
        }

        List<ShopEntry> offers = plugin.shop().offers();
        int index = holder.page * ENTRY_SLOTS + (slot - FIRST_SLOT);
        if (index >= offers.size()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastPurchase.get(player.getUniqueId());
        if (last != null && now - last < PURCHASE_COOLDOWN_MS) {
            return;
        }
        lastPurchase.put(player.getUniqueId(), now);
        if (plugin.shop().purchase(player, offers.get(index))) {
            render(player, event.getInventory(), holder);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastPurchase.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
